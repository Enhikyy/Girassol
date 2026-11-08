# Compatibilidade da stack de pesquisa — Girassol 1.8.0

Este documento separa implementação realmente incorporada, documentação que entra na pesquisa e fontes que ficam apenas como referência. Não se considera uma biblioteca integrada só porque seu README aparece na pesquisa.

## Implementado em Kotlin/Android

| Fonte | Ideia/uso no Girassol | Estado |
|---|---|---|
| [human-skill-tree](https://github.com/24kchengYe/human-skill-tree) | Árvore progressiva, pré-requisitos entre competências e revisões espaçadas. Foi criada uma implementação própria em Kotlin dentro de `LearningContinuity`; nenhum conteúdo `SKILL.md` foi copiado. | Nativo |
| [lau-agent-dream](https://github.com/SuperInstance/lau-agent-dream) | Consolidação offline, memória de experiências e revisão de falhas. A lógica de ciclo foi reimplementada em Kotlin; nenhum runtime Rust é incluído. | Nativo |
| [UI-Voyager](https://github.com/ui-voyager/UI-Voyager) | A ideia de preservar exemplos de falhas para revisão e aprendizado posterior foi aplicada à memória local. O modelo de 4B, vLLM e harness de emulador não são embarcados. | Conceito portado |
| [amv-aichain](https://pypi.org/project/amv-aichain/) | Ideias de chains com estado, RAG local e telemetria foram traduzidas para os componentes Kotlin que já existem; não se incorpora runtime Python. | Conceito portado |
| Catálogo de competências | Pré-requisitos, domínio baseado em revisão, intervalo de repetição e persistência local idempotente. | Nativo |

A tela de autoestudo apresenta as fases de pesquisa, ingestão, síntese, currículo, dataset, ajuste e consolidação. O LoRA automático usa o preset do aparelho e regista sucesso/falha na memória para o próximo ciclo.

## Pesquisa técnica via GitHub

Os READMEs dos projetos abaixo podem ser acrescentados ao corpus apenas para pesquisa/síntese, respeitando as suas licenças. Não são dependências de execução do APK.

| Repositório | Licença verificada | Uso no corpus | Por que não é runtime Android |
|---|---|---|---|
| [labml annotated implementations](https://github.com/labmlai/annotated_deep_learning_paper_implementations) | MIT | Sim | Exemplos didáticos PyTorch; servem para estudar algoritmos, não para rodar dentro do app. |
| [LLaMA Factory](https://github.com/hiyouga/LlamaFactory) | Apache-2.0 | Sim | Stack Python/PyTorch/Transformers/PEFT; as receitas orientam treino, mas o motor do Girassol continua llama.cpp/NDK. |
| [MuonClip](https://github.com/GAD-cell/muon-clip) | MIT | Sim | Implementação PyTorch de Muon, QK clipping e Newton–Schulz; ainda requer port e validação em ggml/ARM64 antes de poder treinar nativamente. |
| [Kimi Linear](https://github.com/hkevin01/kimi-linear) | MIT | Sim | Referência KDA/PyTorch, não um modelo/operador pronto para o backend ARM64 atual. |
| [KDA educational](https://github.com/hwilner/kimi-delta-attention) | MIT | Sim | Implementação educacional; requer implementação e validação de kernels nativos. |
| [Flash Linear Attention](https://github.com/fla-org/flash-linear-attention) | MIT | Sim | Os kernels principais usam Triton e backends de servidor/GPU; é necessário um port específico ggml/NEON para Android. |
| [ZenBrain](https://github.com/zensation-ai/zenbrain) e [paper arXiv:2604.23878](https://arxiv.org/abs/2604.23878) | Apache-2.0 no código; paper CC BY 4.0 | Sim | Referência de arquitetura. O artigo distingue o núcleo público de componentes PMA privados; o Girassol não declara ter reproduzido os sete níveis do paper. |
| [UI-Voyager](https://github.com/ui-voyager/UI-Voyager) | MIT | Sim | O pipeline original usa modelo GUI de 4B, ferramentas de treino e emulator evaluation; apenas o princípio de replay de falhas é portado. |

## Somente metadados / não redistribuir

| Fonte | Situação verificada | Decisão |
|---|---|---|
| [Human Skill Tree](https://github.com/24kchengYe/human-skill-tree) | Aplicação AGPL-3.0; a licença menciona dual licensing MIT para arquivos `SKILL.md`. | Não copiamos código nem arquivos de skills; usamos apenas conceitos gerais. |
| [Android-Guru-Agent](https://github.com/Ultra-Guru/Android-Guru-Agent) | Licença UGRL v1.0 com restrições de redistribuição/derivados. | Não copiar nem integrar seu código, assets ou shell privilegiado. |
| [SVC](https://github.com/lyymuwu/SVC) | Repositório sem licença SPDX/arquivo de licença confirmado durante a auditoria. | Só referência bibliográfica; não importar código nem fazer fusão de LoRAs ainda. |
| [nanoKimi](https://github.com/dishant2009/nanoKimi) | Sem licença confirmada. | Apenas metadados. |
| [Kimi MoE example](https://github.com/mandeep0405/kimi) | Sem licença confirmada. | Apenas metadados. |
| [Eidos-X](https://github.com/itsaquestion/eidos-x) | Apache-2.0, mas os módulos de reflexão consultados eram incompletos/placeholder e a atividade observada era antiga. | Não incorporado; a continuidade de memória é implementada no módulo local do Girassol. |
| [AgenticART](https://github.com/GitSolved/AgenticART) | A API pública do GitHub retornou 404 na auditoria. | Link preservado para consulta futura, sem baixar código. |
| [BYRD AI](https://huggingface.co/omoplatapus/byrd-ai) | Não foi possível validar o model card/licença do URL fornecido. | Não baixar pesos nem executar até a origem e licença serem verificadas. |
| [SRC-Merging](https://neurips.cc/) | O endereço fornecido é a página geral da conferência, não a publicação específica. | Não há implementação para integrar com segurança; falta a referência precisa. |

## Kernels e optimizadores: o que falta para integrar de verdade

MuonClip e KDA não são portados automaticamente por adicioná-los ao README. Para uma implementação real no telefone, os operadores terão de ser escritos em C++/ggml e validados em ARM64/NEON, com testes numéricos contra as referências PyTorch e medições de pico de RAM, throughput e temperatura. A FLA usa Triton em backends que não são plug-and-play para o NDK Android. Por isso, nesta versão entram como fonte de pesquisa, não como opção de treino que aparenta funcionar mas não tem kernel validado.

A fusão SVC também não é ativada: a licença do repositório e a referência exata do paper precisam de ser confirmadas, e a operação deve ser validada em adaptadores reais antes de escrever modelos resultantes.

## Autonomia e limites reais

A pesquisa, a memória, a síntese extrativa, o currículo, a geração de dataset, a revisão de falhas e a consolidação offline funcionam sem exigir uma LLM remota. Quando existe um modelo base compatível e o orçamento medido permite, o auto-treino LoRA é iniciado automaticamente. O sucesso do LoRA é registado como evidência; falhas entram na memória para análise posterior.

O processo permanece dentro do isolamento normal do Android. O app não desliga SELinux, não executa código arbitrário de README com root e não transforma um agente de pesquisa em um canal de shell privilegiado. Essas medidas do sistema operacional não impedem a autonomia do ciclo de pesquisa/autoaprendizado.

Não há limite artificial de tamanho do APK no projeto. A RAM física, a memória disponível, o calor e o suporte de arquitetura continuam a ser limites do dispositivo.
