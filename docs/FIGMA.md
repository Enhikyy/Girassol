# Figma — Girassol

O design é reproduzível dentro do Figma por um plugin local. Ele não depende de imagens externas: a marca é vetorial e as telas são frames nativos.

## Importar

1. Abra o Figma (web ou desktop) em um **arquivo novo**.
2. Menu **Plugins → Development → Import plugin from manifest…**
3. Selecione [`design/plugin/manifest.json`](../design/plugin/manifest.json).
4. Rode **Plugins → Development → Girassol Design System**.
5. O plugin cria:
   - `00 · Capa` — 1600×900;
   - `01 · Design System` — paleta, tipografia, botões, pills, card e marca;
   - `02 · Telas` — 8 telas 390×844;
   - `03 · Fluxo` — pipeline e decisões.

## Estrutura das telas

| Tela | Conteúdo |
|---|---|
| Home | hero, RAM, APIs, pipeline, modelos, próximo passo |
| Autoestudo | etapas, progresso, fontes, preset, dataset, CTA LoRA |
| Modelos | busca, filtros, cards GGUF, armazenamento |
| Treinar | abas, preset, loss, amostra, ações |
| Codex | tarefa, plano, diff, confirmação |
| Chat | mensagens, tool call, fontes, input |
| Llamatik | stream, tools, memória, execução |
| Ajustes | LLM, pesquisa, treino, sobre |

## Cores e tipografia

As cores são criadas como **Paint Styles** em `Girassol/*`. A tipografia usa Inter; o plugin tem fallback para Roboto/Arial.

## Prancha única

`design/board.png` reúne as 8 telas em uma imagem 1700×1840; pode ser arrastada para o Figma como referência visual. Para camadas editáveis, use o plugin.

## Atualizar os mockups

```bash
./design/screenshot.sh
```

Requer `google-chrome` (ou `CHROME=/caminho`).

## Observação

O ambiente do agente pode não ter uma conta Figma conectada. Nesse caso, o plugin é o artefato de importação: ele gera o arquivo completo em qualquer conta, sem precisar de token.
