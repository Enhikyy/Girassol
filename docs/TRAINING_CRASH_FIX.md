<!-- coderabbit-ai-generated:v1 -->

# Correção de fechamento durante treinamento

## Causas reproduzidas

O leitor de logs nativos usava o `JNIEnv` da thread de treinamento. Esse objeto pertence à thread que o obteve. A execução anterior encerrou o processo com `FATAL ERROR in native method: Using JNIEnv in non-Java thread` e código 134, mesmo com um modelo sintético de aproximadamente 3 milhões de parâmetros.

Além disso, o código reutilizava IDs de métodos de uma classe de callback em objetos de outra classe. Agora cada trabalho resolve seus próprios métodos, e a thread de logs obtém seu próprio ambiente JNI.

A inicialização de B no LoRA reutilizava o buffer de A. Projeções FFN de expansão e QKV fundidas podem exigir mais elementos em B. O buffer agora é dimensionado pelo tensor que será escrito.

O motor CPU abortou também no backward de BF16, por falta de backend para uma operação de gradiente. A ponte cria uma cópia temporária F16 para LoRA e F32 para full fine-tuning; F16 também é promovido a F32 no full, pois o otimizador só marca parâmetros F32. O arquivo original é preservado e o temporário é removido ao finalizar. Essa preparação exige espaço em disco e pode aumentar a RAM usada. A aceitação anterior de BF16 sem conversão não era compatível com o motor embarcado.

Datasets agora precisam produzir pelo menos dois exemplos, permitindo treino e avaliação. O limite usa o contexto efetivamente criado pelo motor. Antes, a divisão inteira podia produzir zero exemplos e disparar uma asserção fatal; um exemplo podia terminar sem atualizar pesos. Filtros sem parâmetros e combinações incompatíveis de contexto/batch/microbatch retornam erro antes das asserções. Full fine-tuning rejeita pesos quantizados, conforme seu requisito de pesos flutuantes. O LoRA desativa buffers de pesos otimizados apenas para inferência, pois o backward precisa de outras operações.

## Regressão nativa no host

Requer JDK 21, CMake, compilador C++ e o checkout llama.cpp fixado pelo projeto. Um GGUF Llama existente fornece somente o vocabulário: os pesos de teste são sintéticos, determinísticos, com duas camadas e cerca de 3M parâmetros para o vocabulário SmolLM2 usado na validação.

```bash
JAVA_HOME="$(mise where java@21)"   scripts/test-native-training.sh /caminho/modelo-referencia.gguf
```

`NATIVE_HOST_BUILD_DIR` permite reaproveitar um build host; `NATIVE_TEST_DIR` escolhe onde guardar fixtures e resultados. Por padrão, são diretórios externos ao repositório. O script compila o arquivo JNI de produção, remapeando somente os nomes das funções exportadas para um pacote Java permitido. O stderr fica visível para diagnósticos fatais; o stdout continua passando pela thread real de logs.

A execução com `-Xcheck:jni` cobre LoRA em F32/F16/BF16/Q8, full em F32/F16/BF16, callbacks de classes diferentes, proteção do arquivo base, remoção de temporários e erros de dataset, filtro, modelo ausente e batch incompatível. Os arquivos produzidos não representam modelos úteis: validam a execução do treinamento.

Esses testes são de host x86_64. O APK usa a ponte recompilada para ARM64; execução no aparelho e confirmação do caso exato do usuário precisam do modelo, versão do Android e logcat correspondentes.
