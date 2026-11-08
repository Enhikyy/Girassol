# Avaliação dos motores de treino móvel e integração GGUF

## Estado atual do Girassol

Girassol mantém o motor llama.cpp nativo e a implementação LoRA existente. Não substituímos o motor funcional por um framework externo sem provar compatibilidade com o contrato GGUF/JNI, arquitetura do modelo, formato de adaptador e licença. O merge/exportação de adaptador GGUF precisa ser validado com o mesmo commit do llama.cpp usado no app.

## Referências avaliadas

- **MobileFineTuner**: existe um SDK Android JNI/AAR em C++ e um smoke test sintético de um passo LoRA. A documentação declara arm64-v8a, mas também informa que a API Android expõe o self-test e um passo LoRA, não um loop completo de dataset/checkpoint. O contrato de assets usa diretórios no estilo Hugging Face, portanto não é um substituto direto para treinar GGUF F16 sem conversão/loader adicional. Fonte: https://github.com/Edge-Intelligence-Lab/MobileFineTuner/blob/main/docs/ANDROID_SDK.md
- **QVAC Fabric**: a documentação descreve fork de llama.cpp, LoRA, Vulkan e dynamic tiling para limites de SSBO mobile. A página de benchmarks mostra OOM em vários cenários 4B, então resultados dependem do modelo, quantização e GPU; não é correto prometer suporte irrestrito em aparelhos 4 GB. O código público é descrito como work-in-progress e deve ser fixado a um commit e auditado antes de vendorização. Fontes: https://github.com/tether-ai-research/qvac-fabric-llm-finetune e https://github.com/tether-ai-research/qvac-fabric-llm-finetune/blob/main/docs/BENCHMARKS.md
- **amv-train / termux-train**: não foi possível localizar um repositório público verificável sob esses nomes durante esta revisão. Não foi integrado.
- **qvac-pocket-tune**: não foi possível localizar um repositório público verificável com esse nome durante esta revisão. Não foi integrado.
- **llama.cpp export-lora**: o upstream oferece ferramenta de exportação/merge de adaptadores para GGUF, mas o uso no Android exige compilar e validar o alvo de exportação para arm64 e conferir a compatibilidade exata do formato do adaptador. Fonte: https://github.com/ggml-org/llama.cpp/blob/master/tools/export-lora/README.md

## Técnicas: o que é e o que não é entregue

| Técnica | Estado |
|---|---|
| mmap do modelo base | Implementado no caminho de inferência já existente |
| LoRA nativo | Implementação existente, corrigida em branch anterior |
| Export/merge GGUF on-device | Precisa de validação de ponta a ponta com adaptador produzido no telefone |
| DoRA | Não implementado |
| QLoRA com backward de pesos quantizados | Não implementado; não confundir inferência Q4 com treinamento QLoRA |
| Layer streaming real | Não implementado; mmap/page cache não equivale a streaming layerwise |
| Activation recomputation / MeSP | Não implementado |
| GPU slicing / vocab slicing | Não implementado |
| Dynamic tiling Vulkan Adreno/Mali | Não integrado |
| Stiefel optimizer | Não implementado |
| NPU | Não há backend NPU genérico acessível via llama.cpp para todos os aparelhos; não presumir disponibilidade |
| Foreground service | Serviço de foreground já existe no app; o fluxo completo dataset→train→merge precisa ser ligado e testado como job resiliente |

## Critério de integração

Qualquer novo backend precisa passar: licença e proveniência; build arm64 reproduzível; teste sintético JNI; smoke test GGUF pequeno; checkpoint/resume; exportação e reload do adaptador; merge em GGUF; comparação de logits/perplexidade antes/depois; medição de RSS, temperatura, energia e tempo em dispositivo real. Até isso acontecer, as técnicas acima permanecem explicitamente marcadas como pendentes.
