# Arquiteturas experimentais no backend nativo

## O que foi implementado

O alvo Android agora compila `rsi_kernels.cpp` dentro de `liblabforge.so` via CMake/NDK arm64. Os kernels C++ implementam operações de estado oculto independentes:

- **RDT:** refinamento recorrente com pesos compartilhados, âncora contextual e gate sigmoide por dimensão.
- **Modus_X:** atualização recorrente de estado com recuperação de memória associativa matricial e atualização delta-rule. O estado/memória não crescem com o comprimento da sequência, embora a matriz cresça quadraticamente com a largura oculta.
- **Lunaris MoC:** roteamento top-k, refinamento iterativo de especialistas, agregação/mediador aprendido e fusão por gate.

A API JNI/Kotlin expõe as operações para vetores e pesos treinados. Os kernels têm teste C++ host independente e um smoke test JNI acessível pelo painel RSI. Limites atuais: largura oculta RDT até 2048; Modus_X e MoC até 512; até 16 especialistas; loops de 1–16 no RDT e 1–8 no IRL MoC.

## O que ainda não é um modelo de linguagem completo

Estes kernels **não substituem** o decoder/tokenizador/LM head de llama.cpp. A API atual recebe vetores ocultos e tensores já carregados; o caminho de geração GGUF do llama.cpp ainda não expõe os estados internos necessários para inserir esses blocos entre camadas. Portanto, não se deve anunciar que o app já executa Mythic-RDT, Modus_X ou Lunaris MoC como modelos completos. O próximo passo de engenharia é a integração dos adapters no grafo de inferência e um conversor/checkpoint end-to-end com testes de logits e perplexidade.

## Situação dos pesos oficiais (verificada em 16/11/2026)

| Arquitetura | Estado do projeto de origem | Pesos específicos disponíveis para este runtime |
|---|---|---|
| Mythic-RDT | O README upstream declara pre-alpha, sem release público; GGUF/llama.cpp está explicitamente fora do escopo atual. O base DeepSeek-Coder-V2-Lite citado é aproximadamente 31 GB em BF16. | **Não há checkpoint Mythic-RDT publicado para baixar/converter**; não embutir o base de 31 GB no APK. |
| Modus_X 3.0 | Release de código/paper/evidências; o README declara que checkpoints grandes treinados não estão incluídos e que não é um assistente pré-treinado. | **Nenhum checkpoint de linguagem pré-treinado distribuído no release consultado.** |
| Lunaris MoC | Código de arquitetura e treino PyTorch com experimentos; README aponta datasets e W&B, não um checkpoint MoC pronto para llama.cpp. Os pesos Lunaris Guard no Hugging Face são classificadores de segurança distintos, não MoC gerativo. | **Nenhum checkpoint MoC GGUF/NDK verificado.** Não confundir Lunaris Guard com Lunaris MoC. |

Fontes upstream:
- Mythic-RDT: https://github.com/mann1x/Mythic-RDT
- Modus_X: https://github.com/sanyamChaudhary27/Modus_X
- Lunaris MoC: https://github.com/Auren-Research/lunaris
- Lunaris Guard (modelo separado): https://huggingface.co/auren-research/lunaris-guardv3

## Exportação de adapters treinados

O script `scripts/export_rsiw.py` converte um `state_dict` PyTorch treinado em `.rsiw`. Ele exige um JSON de mapeamento explícito entre os nomes dos tensores nativos e as chaves do checkpoint; valida shapes, valores finitos, arquitetura, dimensão, especialistas e profundidade; grava FP32 little-endian e calcula SHA-256. O importador Android valida novamente o pacote antes de aceitar os tensores.

Exemplo de mapeamento RDT (os nomes à direita dependem do treinamento real):

```json
{
  "input_weights": "adapter.input.weight",
  "recurrent_weights": "adapter.recurrent.weight",
  "bias": "adapter.bias",
  "gate": "adapter.gate"
}
```

Exportação:

```bash
python scripts/export_rsiw.py \
  --checkpoint adapter.pt \
  --mapping rdt-mapping.json \
  --architecture rdt \
  --hidden-size 256 \
  --reasoning-steps 4 \
  --output girassol-rdt.rsiw
```

Para Lunaris MoC, o mapeamento precisa fornecer router, especialistas, mediador e fusão com as dimensões especificadas no script. Modus_X usa parâmetros de estado `decay`/`learning_rate` e memória persistente, sem tensores treináveis neste kernel mínimo.

## Formato esperado para pesos

O runtime C++ recebe tensores FP32 em ordem row-major; os nomes e dimensões esperados estão definidos nas assinaturas JNI/Kotlin. Nenhum tensor aleatório é distribuído como checkpoint. Um pacote de pesos só deve ser aceito quando trouxer:
1. arquitetura e versão do formato;
2. largura oculta, número de especialistas e profundidade;
3. todos os tensores com dimensões exatas;
4. hash SHA-256 do payload;
5. licença e origem dos pesos;
6. benchmark de validação/holdout, além de teste de carga no dispositivo.

Até existir um checkpoint treinado e licenciado, os kernels são testados com pequenos vetores sintéticos apenas para validar a matemática e a ABI JNI. Esses vetores não são pesos de modelo e não geram texto.
