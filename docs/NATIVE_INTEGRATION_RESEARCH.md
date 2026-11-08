# Pesquisa e integração nativa — Girassol

Atualizado em 2026-11-08. Este documento distingue código incorporado de projetos externos avaliados e de funcionalidades que ainda não passaram por validação Android.

## 1. Visualização 3D: Filament + gltfio

**Integrado nesta branch:** Google Filament 1.77.3, com os artefatos Maven Central `filament-android`, `gltfio-android` e `filament-utils-android`.

- O Native Studio mantém o importador e preview OBJ/STL existentes.
- GLB binário é carregado diretamente a partir de um arquivo mapeado.
- glTF JSON é carregado a partir de um pacote ZIP; buffers e texturas externos são resolvidos relativamente à pasta do modelo e validados para não sair dela.
- O `ModelViewer` de Filament fornece renderização glTF 2.0, materiais PBR, texturas, skins e reprodução das animações disponíveis.
- O SurfaceView cria/destrói o motor conforme o ciclo de attach/detach; arquivos e recursos externos têm limites de tamanho.

Limites desta integração:
- Pacotes ZIP: 32 MiB compactados, 64 MiB descompactados, 16 MiB por entrada, por causa dos limites atuais do importador seguro.
- GLB direto: máximo de 64 MiB; glTF JSON direto: 4 MiB e só funciona sem recursos externos. Para recursos externos, use ZIP preservando a estrutura relativa.
- Não é um editor CAD, não cria materiais PBR personalizados, não edita rigs e não oferece ferramentas de modelagem. O OBJ/STL existente continua no preview wireframe.
- O renderizador usa GPU/driver Android; a compilação CI não prova renderização física no Mali-G57 do A22.

Referências: https://github.com/google/filament ; https://github.com/google/filament/blob/v1.77.3/android/samples/sample-gltf-viewer/src/main/java/com/google/android/filament/gltf/MainActivity.kt

## 2. Treinamento GGUF: candidatos avaliados

### llama-tq (MIT) — candidato de maior aderência

Projeto: https://github.com/LL4nc33/llama-tq  
Commit de referência da documentação consultada: `773b8dfb097b3f75d9f3ea06d43a4dfff57d2f3f` (branch `turboquant` evoluiu depois desse commit).

A documentação descreve um `llama-finetune` que treina LoRA diretamente sobre bases GGUF quantizadas (K-quants, IQ, MXFP4 e ternários), calcula gradientes através de matmuls quantizados, salva adaptadores GGUF, faz resume com estado do AdamW e permite merge com `llama-export-lora`. Também descreve gradient checkpointing e checkpoints atômicos.

**Por que ainda não foi substituído/integrado ao motor principal:** é um fork amplo do llama.cpp, não uma biblioteca Android isolada. A evidência de benchmark publicada é principalmente RTX 2060 de 12 GB e RTX 5090 de 32 GB; o README diz que o caminho CPU existe, mas não há benchmark reproduzível no Galaxy A22, nem CI NDK/Android arm64 confirmada nesta auditoria. O código também não demonstra suporte a DoRA, MeSP ou Stiefel. A opção `--qat-target-quant` está explicitamente marcada experimental e sem validação de treino. Trocar o llama.cpp pinado por esse fork sem uma build Android, teste JNI, teste de backward, resume e merge poderia quebrar o chat e o LoRA que já funcionam.

**Estado no Girassol:** pesquisado, licença MIT verificada, não vendorizado nem conectado à tela de treino. Próximo gate: construir o fork em target Android arm64 numa branch isolada, rodar teste sintético CPU e comparar logits/loss/adaptador; só então avaliar a troca do backend.

Fonte técnica: https://llama-tq.com/docs/fine-tuning

### MobileFineTuner (Apache-2.0)

Projeto: https://github.com/Edge-Intelligence-Lab/MobileFineTuner

Tem core C++/autograd/LoRA, modelos de referência GPT-2, Gemma e Qwen, e um caminho Android arm64. Porém a documentação do AAR descreve apenas self-test e um passo de LoRA na API Android, não um loop completo de dataset/checkpoint. Os pesos são snapshots HuggingFace SafeTensors e não GGUF. Portanto não substitui o motor GGUF atual sem um loader, tokenizer, serialização de adaptador e ciclo de treino adicionais.

Estado: pesquisado; não integrado ao pipeline GGUF.

Fonte: https://github.com/Edge-Intelligence-Lab/MobileFineTuner/blob/main/docs/ANDROID_SDK.md

### DoRA

DoRA separa magnitude e direção: a direção usa LoRA e a magnitude tem parâmetros treináveis separados. A implementação de referência PEFT limita tipos de camada e alerta para overhead; o caminho de merge é recomendado para inferência.

Estado: conceito e referência confirmados, mas **não implementado no JNI**. O caminho atual cria apenas pares A/B LoRA e serializa metadados `adapter.type=lora`. Não rotular o treinamento atual como DoRA.

Referências: https://huggingface.co/docs/peft/package_reference/lora_variant_dora ; https://arxiv.org/abs/2402.09353

### QLoRA genuína / treino sobre GGUF quantizado

QLoRA requer backward através dos pesos quantizados congelados, dequantização/representação apropriada no caminho de gradiente e atualização dos parâmetros do adaptador. Rodar inferência com Q4 e treinar LoRA sobre uma base F16/F32 são coisas diferentes.

Estado: **não implementado no backend principal atual**. llama-tq é um candidato com backward para bases quantizadas, mas sem validação Android. A opção QAT descrita por esse fork também está marcada experimental, não é prova de QLoRA validada.

## 3. MeSP e Stiefel

### MeSP — Memory-efficient Structured Backpropagation

O trabalho ACL 2026 descreve recomputar a projeção pequena `h=xA` no backward em vez de armazená-la, reduzindo em média 49% o pico de memória nos modelos avaliados. O repositório público usa MLX/Python e runtime Swift para Apple; os resultados publicados não são uma implementação ggml/Android.

Estado: **não portado**. Para integrar é preciso implementar backward manual no grafo ggml, comparar gradientes numéricos contra o caminho atual e medir RSS no ARM64. Não é apenas uma opção de configuração.

Referências: https://aclanthology.org/2026.acl-industry.62/ ; https://github.com/crinex/acl-mesp

### Stiefel / Riemannian LoRA optimizer

O trabalho EMNLP 2025 otimiza a matriz B de LoRA sob restrição de ortogonalidade no manifold de Stiefel. Isso requer uma etapa de otimização/retração coerente com os gradientes e o estado do otimizador.

Estado: **não portado** para o otimizador `llama_opt_epoch` atual. Não é seguro adicionar uma opção de UI sem hook de atualização pós-optimizer e testes de ortogonalidade/convergência.

Referência: https://aclanthology.org/2025.findings-emnlp.1143/

## 4. Layer streaming, GPU slicing e aceleradores

- **mmap:** já usado no caminho de inferência e no LoRA com base congelada. mmap deixa páginas de peso file-backed e recuperáveis; não elimina RSS, page faults, KV cache, buffers ou ativações.
- **Layer streaming:** não há no app um scheduler que carregue, execute, descarregue e sincronize explicitamente uma camada por vez. Não confundir mmap com layer streaming.
- **GPU layer offload:** llama.cpp tem backends de offload, mas o treino JNI atual força `n_gpu_layers=0`. Não há GPU backward/slicing no pipeline de treino do Girassol.
- **GPU slicing / dynamic tiling:** não integrado. Os resultados de backends Vulkan/OpenCL são específicos de GPU/driver e não podem ser extrapolados para todos os Android.
- **NPU:** o Galaxy A22 5G usa MediaTek Dimensity 700 e GPU Mali-G57 MC2. A existência de bloco NPU/APU no SoC não significa que exista backend genérico acessível pelo llama.cpp. Não há backend MediaTek/APU validado no Girassol.
- **OpenCL de llama.cpp:** a matriz oficial de dispositivos Android documentada foca em Snapdragon/Adreno recentes; não prova compatibilidade com Mali-G57.
- **KleidiAI:** habilitado no build arm64 existente para kernels CPU compatíveis; não é GPU nem NPU.

Referências: https://github.com/ggml-org/llama.cpp/blob/master/docs/build.md ; https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/OPENCL.md ; https://nanoreview.net/en/phone/samsung-galaxy-a22-5g

## 5. Compilar APK inteiramente dentro do app

O app não contém JDK, Gradle, Android SDK, build-tools e NDK completos. Empacotar tudo aumenta muito o APK, o armazenamento e o conjunto de processos; iniciar um build Gradle completo em um processo de app com 4 GB de RAM pode causar encerramento pelo sistema. A existência de Termux e scripts de build on-device demonstra um caminho externo para usuários avançados, não um SDK seguro e suportado para embutir dentro de outro APK.

Estado: **não integrado dentro do processo do Girassol**. O Native Studio continua a editar/importar/exportar projeto ZIP para Android Studio. Uma integração Termux exigiria consentimento explícito do usuário, instalação/configuração do app externo e contrato de comunicação/segurança; não deve fingir ser um compilador interno.

Referências: https://github.com/polygon-mv/termux-android-build ; https://developer.android.com/build/jdks

## 6. Matriz honesta

| Recurso | Estado nesta branch |
|---|---|
| OBJ/STL import/export e preview wireframe | Implementado anteriormente |
| GLB + glTF ZIP, PBR/texturas/animações via Filament | Integrado; aguardando CI e teste físico |
| GLTF com dependências externas em arquivo isolado | Não; use pacote ZIP |
| Build APK completo dentro do app | Não integrado |
| LoRA GGUF existente | Implementação anterior; não equivale a QLoRA |
| DoRA | Não integrado |
| QLoRA GGUF real | Candidato llama-tq pesquisado; não integrado |
| Merge GGUF no dispositivo | Ainda precisa validação end-to-end |
| MeSP | Paper/código pesquisados; não portado |
| Stiefel optimizer | Paper pesquisado; não portado |
| Layer streaming | Não integrado |
| GPU slicing/dynamic tiling de treino | Não integrado |
| NPU MediaTek | Sem backend validado |
| KleidiAI CPU arm64 | Build habilitado; depende do suporte do kernel/CPU |

## 7. Critérios antes de promover

1. CI Android arm64 build e testes sem falhas.
2. Testes de importação glTF/GLB e pacote ZIP malformado/ZIP traversal.
3. Teste físico de renderização, animação, textura e ciclo de vida no A22.
4. Backend QLoRA em branch isolada, com teste de gradiente, checkpoint/resume, exportação e merge.
5. Medição de RSS, temperatura, energia e tempo no dispositivo real.
6. Nenhum recurso avançado deve aparecer como habilitado enquanto não cumprir os testes acima.
