# Inferência e LoRA off-heap com mmap

## Estado implementado

- Inferência local JNI carrega o GGUF com `LLAMA_LOAD_MODE_MMAP`.
- O modo `MMAP` da revisão de llama.cpp fixada pelo projeto mapeia o arquivo e **não** pede `mlock`. Não há um segundo booleano `use_mlock` nesta API: `MLOCK` e `MMAP_MLOCK` são modos separados e não são selecionados.
- O LoRA mantém a base congelada mapeada com `LLAMA_LOAD_MODE_MMAP`; somente os tensores A/B do adaptador e os buffers de treino são alocados para escrita.
- A inferência usa micro-batches de dois tokens, com posições explícitas, KV cache F16, contexto limitado a 512 no perfil de 4 GB e no máximo dois threads nesse perfil.
- Ao primeiro envio no Chat, o utilizador confirma o risco de pressão de RAM. O diálogo não ignora erros do SO: o Android ainda pode encerrar o processo quando não houver memória física.
- Se a criação do modelo/contexto falhar por alocação, o JNI devolve erro estruturado; o Chat tenta uma segunda execução com contexto 256, um thread e 64 tokens.
- Os logs nativos reportam `[mmap] ... enabled=true mlock=false verified=yes/no regions=N`. A verificação procura o caminho do GGUF em `/proc/self/maps`.
- O orçamento de LoRA considera um conjunto limitado de páginas quentes do modelo, em vez de contabilizar todo o tamanho do GGUF como heap residente. Reserva para KV/contexto, adaptadores, ativações e estado do otimizador continua a ser considerada.

## Por que não existe `android:largeHeap`

O manifesto não declara `android:largeHeap="true"`. A arquitetura mantém os pesos fora do heap gerenciado pelo ART e não aumenta o heap Java.

## Limites importantes

`mmap` não torna o modelo gratuito nem garante que um modelo maior que a RAM física funcione. O mapeamento evita copiar todo o arquivo para o heap Java, mas páginas acessadas passam a contar para o RSS/page cache; KV cache, ativações, buffers de computação, tokenizer, adaptadores e otimizador continuam a consumir memória. Se o conjunto de trabalho exceder RAM e page cache disponíveis, haverá page faults, thrashing, lentidão ou encerramento pelo low-memory killer.

O treino LoRA usa uma base congelada mapeada, mas gradientes/otimizador e ativações não são mmap. Fine-tuning completo modifica pesos e permanece no modo de carregamento gravável sem mmap. Streaming layerwise com prefetch próprio não foi adicionado: requer mudanças internas de backend/ggml e testes de equivalência/latência, não apenas alternar uma flag.

## Como validar no Galaxy A22 5G / Android Studio

1. Instale o APK ARM64 de debug e selecione um GGUF armazenado como arquivo local (não uma URI temporária do provider).
2. Abra **Chat**, envie uma mensagem e confirme o aviso de mmap.
3. Na aba **Logs**, procure `[mmap] purpose=inference enabled=true mlock=false verified=yes`. Para LoRA, procure `purpose=lora-base-frozen`.
4. No Android Studio **Profiler → Memory**, observe Java Heap e Native/Graphics. O tamanho do Java Heap não deve subir na proporção do tamanho do GGUF. Native/RSS pode crescer conforme as páginas do modelo são acessadas; isso é esperado.
5. Com ADB, encontre o PID e inspecione o mapa do processo:
   ```bash
   adb shell pidof dev.labforge.app
   adb shell cat /proc/$(adb shell pidof dev.labforge.app)/maps | grep '\.gguf'
   adb shell cat /proc/$(adb shell pidof dev.labforge.app)/status | grep -E 'VmRSS|VmSize|VmLck'
   ```
   Deve existir uma ou mais regiões com o caminho do GGUF; `VmLck` deve permanecer zero (ou não aumentar por causa do modelo). Alguns fabricantes restringem a leitura de `/proc/PID/maps` via ADB; nesse caso use o log `verified` emitido dentro do próprio processo.
6. Repita com o mesmo modelo após fechar e reabrir o app. Compare RSS/Native Heap, tempo de primeira resposta, tokens/s, temperatura e estabilidade. O RSS pode aumentar após tocar muitas páginas, sem significar cópia integral para o heap Java.

## Interpretação correta

- `verified=yes`: o arquivo GGUF aparece no mapa de memória do processo.
- `VmLck=0`: o processo não bloqueou as páginas do modelo com mlock.
- Java Heap baixo + Native/RSS crescente: comportamento esperado de pesos mapeados; não significa ausência de consumo de RAM.
- Um modelo que exceda muito a RAM disponível pode continuar falhando. A alternativa prática é um GGUF menor/mais quantizado (Q4/Q3), contexto menor ou LoRA com rank reduzido.
