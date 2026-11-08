# Girassol 1.8.0 — stack de pesquisa e continuidade de autoaprendizado

- Integra catálogo GitHub-first por tópico: labml, LLaMA Factory, MuonClip, Kimi Linear/KDA, Flash Linear Attention, UI-Voyager, lau-agent-dream e ZenBrain como referências técnicas, com filtragem por licença.
- Preserva referências sem licença, indisponíveis, ou com licença restritiva apenas como metadados; não baixa nem incorpora seus READMEs/código.
- Integra pesquisa de repositórios e READMEs licenciados na pesquisa, síntese e dataset.
- Reforça `LearningContinuity`: árvore de competências com pré-requisitos, revisões espaçadas e consolidação idempotente sem repetir a mesma experiência em cada ciclo ocioso.
- O treino LoRA passa a estar ativado por padrão ao concluir um ciclo; resultado e falhas nativas são registados como evidência para a memória e revisões futuras. A execução continua sujeita ao orçamento adaptativo de RAM.
- Adiciona etapa explícita de consolidação/sono, flor radial de 24 pétalas com animação leve e painel de competências mais legível.
- Documenta a tradução nativa em Compose das referências Figma, sem redistribuir assets de terceiros.
- Mantém o autoestudo local mesmo sem LLM; memória e dataset não significam alteração de pesos até que um LoRA seja concluído com sucesso.

# Girassol 1.7.0 — estabilidade de treinamento e autoaprendizado

- Corrige o uso de `JNIEnv` entre threads nativas, que podia encerrar o processo durante o fluxo de logs do treinamento.
- Evita carregar o dataset inteiro na RAM nativa: o corpus fica completo no armazenamento e cada ciclo de treino lê uma janela rotativa limitada.
- LoRA adaptativo para aparelhos com pouca RAM: contexto, rank, alvos e threads reduzidos; bloqueio preventivo de jobs cujo orçamento ultrapasse a memória disponível.
- Monitora pressão de RAM durante o treino, solicita parada cooperativa ao fim da época e preserva checkpoint LoRA por época.
- Pesquisa autônoma com GitHub em primeiro lugar, priorização por estrelas/atividade e ingestão do README de projetos para fundamentar síntese e dataset.
- Alternância Pure Black OLED persistente; animações de fundo suspensas nesse modo para reduzir trabalho gráfico, com gráficos de barras e linhas animados.
- Acrescenta árvore de habilidades persistente, revisão espaçada e painel de domínio por tópico. 
- Adiciona consolidação offline de experiências, agrupamento de padrões e revisão de falhas com evidências/confiança.
- Preserva o piloto automático, o autoaprendizado iterativo e os recursos já existentes. Não há limite artificial de tamanho do APK definido pelo projeto.

## Observação de hardware
Em aparelhos com 4 GB, o perfil seguro prioriza LoRA pequeno e ciclos curtos; fine-tuning completo fica bloqueado quando a memória total/disponível não atende ao orçamento. Isso evita confundir “APK sem limite de tamanho” com memória RAM ilimitada.

# Girassol 1.6.1 — Refinamento de interface

- Melhorias de legibilidade e escala tipográfica nas telas principais.
- Ajustes de hierarquia de títulos, metadados e rótulos para leitura em telas móveis.
- Preservação do motor nativo, modelos, pesquisa, ferramentas de agente, dados e fluxos existentes.

# Changelog

## 1.6.0 — 2026-11-08

Pesquisa ampliada, ingestão de conteúdo e piloto automático.

- Acrescenta Crossref, PubMed e Hugging Face Datasets às fontes sem chave; Brave Search e GitHub permanecem opcionais.
- Downloader integrado: coleta páginas HTTPS textuais, normaliza HTML/JSON/XML/Markdown e incorpora conteúdo ao corpus usado pela síntese e pelo dataset.
- Piloto automático repete pesquisa → download → síntese → currículo → dataset → autoajuste; métricas de tokens, fontes, downloads e exemplos são persistidas e exibidas em gráficos.
- Corrige propagação de cancelamento das buscas, valida destinos alternativos do Git push e trata rejeições do servidor como falha.
- Permite commits e operações Git na pasta clonada e registra ferramentas GitHub no runtime Llamatik.
- CI deixa de ignorar falhas nos testes unitários; testes e build passam a bloquear a entrega.


## 1.5.0 — 2026-11-08

Brave Search, GitHub para o agente e liquid glass.

- Autoestudo com Brave Search (chave opcional em Ajustes) e busca de repositórios no GitHub como fontes extras.
- Agente/Codex ganham ferramentas GitHub: `github_search_repos`, `github_search_code`, `github_read_file`, `github_list_issues`, `github_create_issue`, `github_create_pr`, `git_clone`, `git_branch` e `git_push`.
- Token do GitHub só é enviado para `github.com`; push direto em `main`/`master` é bloqueado e o token é mascarado em erros.
- Liquid glass: desfoque real de fundo (Haze, Android 12+) na barra de navegação e no seletor, bordas iluminadas e reflexo em cards e botões.
- Animações: indicador líquido com mola nas abas, transições de tela com mola e escala, entrada dos cards, brilho nos botões e fundo aurora com mais movimento.

## 1.4.0 — 2026-11-07

Refinamento de UI/UX e correções visuais.

- Texto escuro (`Lab.OnPrimary`) sobre o dourado: contraste ~10.9:1 (antes ~1.7:1).
- Piso tipográfico de 11sp; `Muted`/`Outline` mais legíveis; campos e console sem fundos azulados.
- Navegação com 5 abas: "Agentes" reúne Chat e Codex; back de Codex/Llamatik volta à origem.
- Alvos de toque ≥ 44–48dp e rótulos de acessibilidade em ícones de ação.
- `GradientButton` desabilitado não desenha mais um retângulo preto atrás do texto.
- "6 APIs" corrigido; versão em Ajustes vem de `BuildConfig.VERSION_NAME`.
- Plugin Figma: corrigido `y` indefinido em Ajustes; compatível com o limite de 3 páginas do Starter.

## 1.3.2 — 2026-11-07

Revisão completa de código: bugs, segurança, performance e arquitetura.

### Security

- **Sandbox do agente**: `write_file`/`apply_patch` agora recusam escrita em `.git/`
  (um hook gravado ali vira execução de código). Leitura continua permitida.
- Caminhos com byte nulo são rejeitados antes de tocar o disco.
- `network_security_config.xml`: cleartext HTTP desabilitado por padrão; exceção
  restrita a loopback/emulador (Ollama, LM Studio, llama-server).
- Respostas das APIs de pesquisa limitadas a 4 MB (um endpoint hostil não derruba o app).
- Importação de assets verifica espaço livre antes de copiar.

### Fixed

- **`Gguf.read`**: `tensor_count`/`kv_count` absurdos eram truncados silenciosamente,
  desalinhando o leitor e corrompendo todos os tensores. Agora falham com erro claro.
- **`ImatrixReader`**: `readFully` estourava `EOFException` em imatrix truncado; a
  amostragem agora é limitada ao tamanho real do arquivo.
- **`ShellSession`**: comando que estourava o timeout deixava a thread leitora presa
  (vazamento por comando). O pipe é fechado após `destroyForcibly`.
- **`JobEngine`**: `begin()` fazia check-then-set sem lock — dois jobs podiam iniciar
  juntos. Agora a reserva é atômica e `current` é limpo ao finalizar.
- **`SettingsRepository.update`**: read-modify-write fora do `edit{}` descartava
  alterações concorrentes.
- **`AppRoot`**: `AnimatedContent` usava só `route` como chave, então navegar de um
  modelo para outro não recompunha a tela de detalhe.
- **`MemoryBooster`**: a limpeza de cache apagava o diretório de trabalho do agente.
- **`CodexViewModel`**: modo "ilimitado" ganhou teto de segurança de 100 iterações.
- **`ReActAgent`**: guarda contra loop de ferramenta idêntica repetida.

### Performance

- `LlmClient` reutiliza um único `OkHttpClient` (antes: um pool de conexões e threads
  por instância/tela).
- `DatasetBuilder` limita o corpus gerado a 64 MB.
- `LessonStore.search` reforça o `uses` das lições recuperadas (ranking por utilidade).

### Added

- `.github/workflows/android.yml` — CI real: testes unitários, APK e release em tags `v*`.
- `AgentSandboxTest` — 6 testes de regressão do sandbox (traversal, `.git`, byte nulo).

## 1.3.1 — 2026-11-07

### Fixed

- Autoestudo não fecha mais o app:
  - `titles.take(n - 3)` negativo no currículo extrativo → `coerceAtLeast(0)`;
  - foreground service removido do fluxo de pesquisa (OEMs restritivos);
  - `GirassolService.startForeground` protegido com try/catch + `stopSelf`;
  - auto-treino só inicia com GGUF F32/F16/BF16;
  - `refresh()` do painel protegido com `runCatching`.
- Regressão `AutodidactFlowTest` cobre pesquisa → dataset → preset → UI populada.

### Added

- Workflow GitHub Actions que gera o APK e publica artifact/release.
- Design system e plugin Figma com 8 telas, capa e fluxo.
- Documentação de arquitetura, release, segurança e contribuição.

## 1.3.0 — 2026-11-07

### Added

- Llamatik SDK 2.2.2 (core nativo excluído).
- Retrofit 3.0.0 + OkHttp 4.12.0 + kotlinx.serialization.
- `PublicApiTool` (`public_api_posts`), `LlamatikRuntime`, tela Llamatik.
- Ponte `LlamatikToolBridge` no ReAct.
- Streaming SSE com `AgentStreamChunk`.
- Testes Llamatik/MockWebServer/API live.

### Changed

- Gradle 9.9.0, AGP 9.5.1, Kotlin 2.3.21, compileSdk 37, JDK 21.
- targetSdk 34.

## 1.2.0 — 2026-11-06

### Added

- Pesquisa em 6 APIs gratuitas, BM25 RAG, `ResearchEngine`.
- `AutoTuner`, `MemoryBooster`, UI animada e Home bento.
- 18 testes.

## 1.1.1 — 2026-11-05

### Fixed

- Crash de estado Compose fora da main thread no `AgentViewModel.init`.
- `libc++_shared.so` ausente no APK.

### Added

- Codex, autodidact, foreground service, `MemoryGuard`, `JobEngine`.
- Streaming com reasoning, contagem nativa de tokens e ETA.

## 1.0.0 — 2026-11-04

- Primeira versão: parser GGUF, imatrix, quantização, fine-tune e LoRA nativo.
