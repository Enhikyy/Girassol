<!-- coderabbit-ai-generated:v1 -->

# Referências e implementação nativa

## Recursos disponíveis no aplicativo

O Girassol opera em Android sem root. A implementação usa Kotlin, Compose e C++/JNI; referências conceituais não são dependências automaticamente incorporadas.

- **Laboratório → Evolução:** árvore de habilidades com pré-requisitos, contagem de sucessos/falhas e recuperações observadas. O diário é alimentado pelas execuções reais do agente; consultar ou diagnosticar o ambiente não conta como compilar um projeto. A janela mantém 512 eventos, sem gravar argumentos ou saídas no diário. Pré-requisitos orientam a próxima prática, sem bloquear as ferramentas. As contagens não certificam domínio.
- **Recuperação:** a mesma ferramenta com os mesmos argumentos, independentemente da ordem das chaves JSON, precisa ter falhado e depois retornado sucesso. O registro associa a nova evidência à falha anterior e salva uma lição de recuperação. Um build bem-sucedido não comprova que testes passaram ou que o APK foi instalado.
- **Benchmark local:** duas amostras intercaladas para 1, metade e todas as CPUs disponíveis. Um prompt fixo usa temperatura zero e até 64 tokens; a mediana considera o tempo total de carga, prompt e geração. Configurações com falhas são descartadas. A recomendação é específica desta execução e pode ser aplicada manualmente à inferência. Alterar modelo, adaptador configurado ou contexto exige nova medição. Não mede GPU/NPU, qualidade de respostas ou Macrobenchmark do sistema.
- **Dataset:** importação de arrays JSON formatados Alpaca/ShareGPT, objeto JSON e JSONL, além de texto. O importador anterior cortava exemplos no limite de tamanho (`rendered.take(budget)`); agora mantém ou omite o exemplo inteiro e contabiliza a omissão. JSON completo acima de 64 MB precisa ser convertido para JSONL. Essa mudança evita respostas fragmentadas no corpus.
- **Automação:** hierarquia inclui classe, estado habilitado e capacidade de rolagem; `click_text` clica em um rótulo exato e único ou seu ancestral clicável. Rótulos ambíguos exigem outro alvo. Os testes de UI usam a interface Android por Acessibilidade.
- **Curiosidade no sono:** após as perguntas derivadas de falhas, o WorkManager pode pesquisar a próxima prática sugerida pela árvore. Isso não executa automaticamente tarefas arbitrárias nem inicia treino LoRA.

O painel usa os componentes Compose do Girassol, incluindo botões animados, cartões e girassol de atividade. Não representa importação de arquivos Figma inacessíveis.

## Mapeamento das referências

| Referência | Cobertura atual | Trabalho ainda necessário |
|---|---|---|
| [human-skill-tree](https://github.com/24kchengYe/human-skill-tree) | Árvore própria em Kotlin, evidências de execução e próxima prática | Não importa os 33 skills/conteúdos do projeto; não copia arquivos AGPL |
| [labml](https://github.com/labmlai/annotated_deep_learning_paper_implementations) | Referência de estudo; corpus e autoestudo já disponíveis | Portar e validar cada arquitetura PyTorch selecionada |
| [LlamaFactory](https://github.com/hiyouga/LlamaFactory) | Importação Alpaca/ShareGPT e treino LoRA existente via llama.cpp | Não embute o runtime Python, catálogo de métodos ou pipeline de avaliação completo |
| [llama.cpp](https://github.com/ggml-org/llama.cpp), [MLC](https://github.com/mlc-ai/mlc-llm), MindSpore, MediaPipe | Inferência GGUF/JNI CPU ARM64 e parâmetros de threads medidos | MLC/Vulkan e demais runtimes precisam de backends, artefatos e testes próprios; não são ativados trocando uma opção |
| [aider](https://github.com/Aider-AI/aider), OpenClaw/AnyClaw | Codex/ReAct próprio, edição, Git e registro de recuperação | Não incorpora esses agentes Python nem seus protocolos completos |
| [Semgrep](https://github.com/semgrep/semgrep), [Maestro](https://github.com/mobile-dev-inc/maestro) | Gradle lint/test e executor de UI próprio por Acessibilidade | Não incorpora o motor Semgrep nem interpreta todos os fluxos Maestro |
| [Optuna](https://github.com/optuna/optuna), [performance-samples](https://github.com/android/performance-samples) | Busca medida entre threads da inferência, preset heurístico de treino existente | TPE/pruning, busca de loss validada em dados separados e Macrobenchmark instrumentado ainda pendentes |
| [Termux](https://github.com/termux/termux-app), Gradle, amv-aichain | Serviço RUN_COMMAND com resultados, comandos e builds no projeto do Termux | Termux é instalado separadamente; não embutido; SDK ARM64 precisa ser preparado e verificado no telefone |
| Open-AutoGLM, UI-Voyager, Android-Guru-Agent, DroidMind | Controle e testes próprios sem root por Acessibilidade | Não incorpora servidores ADB, modelos visuais, bridges MCP ou todas as ações desses projetos |
| SnowDrift, AgenticART | Agente pode corrigir arquivos, repetir validações e registrar recuperação | Não substitui automaticamente o binário instalado; AgenticART não pôde ser inspecionado pelo README informado |
| lau-agent-dream, ZenBrain, eidos-x, BYRD | Replay, falhas, consolidação e sugestão de prática próprios | Não implementa integralmente suas arquiteturas de memória/agência; BYRD retornou 401 |
| MuonClip, KDA, flash-linear-attention, MoE, nanoKimi | Referências catalogadas | Necessitam kernels, alteração do grafo e pesos compatíveis, treino e avaliação; não foram adicionados ao llama.cpp apenas por constarem na lista |
| SRC-Merging, [SVC](https://github.com/lyymuwu/SVC) | Composição de adaptadores existentes durante inferência | Não exporta fusão SVC/SRC nem garante ausência de esquecimento; o link SRC fornecido aponta só à página inicial NeurIPS |
| Figma: chatbots, cards, gráficos, design systems e animações listados | Componentes próprios Compose preservados e painel nativo de evolução | Todos os 22 links de arquivo retornaram 403; aguardam exportações .fig/imagens para reprodução fiel |
| Kernel tweaks/FDE.AI/RiProG-AI | WorkManager e APIs Android públicas | Ajustes de kernel/processos privilegiados não estão disponíveis em um aplicativo comum sem root |

## Auditoria de acesso

[reference-status.json](reference-status.json) contém as 54 URLs consultadas, respostas HTTP, URLs efetivamente lidas e hashes dos textos acessíveis. Um 404 em README não prova que o repositório não existe; tentativas de metadata para MuonClip/AgenticART também retornaram 403. O README de labml foi encontrado como `readme.md`. A página arXiv fornecida identifica o artigo ZenBrain indicado. Erros de acesso não foram tratados como validação das alegações do anexo.

As funcionalidades foram implementadas diretamente no Girassol; uma lista de links não equivale a bibliotecas portadas. Execução ARM64, medições no telefone, cliques reais e builds no Termux ainda precisam de aparelho físico. Testes JVM/Compose e probes JNI no host são registrados separadamente em contexto de validação.
