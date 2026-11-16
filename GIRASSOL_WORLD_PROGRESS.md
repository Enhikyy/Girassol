# GIRASSOL / MUNDO — Progresso de engenharia

Atualizado em 2026-11-12. Branch de trabalho: `feat/mundo-simulation`. Base: `main` em `52a3f11afce7b009d121163244c3b34860628203`.

## Estado desta entrega

Esta branch contém uma fatia vertical funcional em código do universo persistente, não a conclusão de toda a visão do MUNDO. O motor, a economia, a persistência, a cena OpenGL, os painéis e as integrações usam o mesmo `WorldState`. Não há gráficos históricos preenchidos com valores aleatórios nem respostas fictícias quando a inferência falha.

A rodada atual acrescenta histórico de atividades em tempo real, transições de foco cinematográficas, toasts de conclusões reais, exportação da sessão e do trace visível, monitor de throughput, representação honesta do barramento de agentes e histórico persistente de checkpoints de treino. Componentes equivalentes nativos em Compose foram usados porque o projeto não contém React, Framer Motion ou D3.

**Verificação local final em 2026-11-12:** o ambiente foi provisionado com Temurin 21.0.13, Gradle 9.9.0 e Android SDK; os runtimes Android instrumentados do Robolectric foram disponibilizados localmente para evitar downloads durante a suíte. O comando `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` terminou com **BUILD SUCCESSFUL em 2 min 20 s** após a correção de locale.

- `:app:assembleDebug`: **BUILD SUCCESSFUL**. APK debug com 37.580.204 bytes e SHA-256 `9bf4c6bbc78c33ea10ebf139ecf29b4e8390d0e444a800a637b4cd067a7b1ed4`.
- APK verificado com assinatura APK Signature Scheme v2 válida e um signer debug; pacote `app.girassol`, `versionCode 11`, `versionName 1.6.1`, `minSdk 26`, `targetSdk 34`, `compileSdk 37` e somente `arm64-v8a`. Há nove bibliotecas nativas empacotadas, incluindo `libllama.so` e `libllama-common.so`. O empacotamento informou que não foi possível strip de símbolos das bibliotecas nativas, mantendo-as como estão; isso não foi tratado como falha de build.
- `:app:testDebugUnitTest`: **BUILD SUCCESSFUL**; 23 classes, 77 testes, 70 aprovados, 0 falhas, 0 erros e 7 ignorados. Os sete skips são testes opt-in de rede/fluxo ao vivo condicionados a `LIVE_API=1`: cinco em `LiveResearchSmokeTest`, um em `PublicApiLiveTest` e um fluxo Compose em `AutodidactFlowTest`. Eles não foram executados nem contados como aprovações.
- A verificação direcionada desta rodada executou oito testes, todos aprovados e sem skips: quatro em `WorldActivityTrackerTest`, dois em `AgentSessionExportTest` e dois em `TrainingCheckpointStoreTest`. Esse subconjunto também está incluído na suíte integral acima.
- `:app:lintDebug`: **BUILD SUCCESSFUL**; 0 erros, 48 avisos e 41 sugestões (`hints`). Uma execução anterior desta árvore encontrou um erro `NonObservableLocale`; ele foi corrigido e toda a verificação foi repetida, portanto os números acima pertencem à árvore corrigida.
- APK, XMLs dos testes, relatórios de lint e log da execução final foram preservados localmente em `.verification/2026-11-12-final/`; esses arquivos de verificação não fazem parte do código-fonte a publicar.

Esses resultados são locais. Nenhum teste em aparelho físico ou emulador foi realizado; build, testes unitários e lint não comprovam renderização, gestures, memória ou desempenho em runtime.

## Arquitetura implementada

### Simulação independente de Android e UI

- `WorldState` e seus modelos são serializáveis e essencialmente imutáveis.
- `WorldEngine` concentra tempo, decisões, necessidades, deslocamento, memórias, relações, habilidades, empresas, produtos, contratação e falência.
- O planejamento usa utilidade sobre necessidades, personalidade, profissão, rotina e contexto. Não existe um modelo de linguagem instanciado por habitante.
- O gerador xorshift64* tem seu estado persistido. A mesma seed e a mesma sequência de entradas produzem a mesma simulação.
- LOD: selecionados são detalhados; visíveis e os primeiros 128 residentes são simplificados a cada 60 minutos simulados; os demais são estatísticos a cada 360 minutos.
- O loop ativo avança 10 minutos simulados por tick em 1×, com escalas 4× e 16×.
- A retomada compensa no máximo sete dias simulados e registra um evento temporal. Não se afirma execução contínua em segundo plano.

### Mundo inicial e economia

- Cidade Aurora, 72 habitantes por padrão, 6 empresas e 20 edifícios iniciais.
- A factory aceita de 8 a 4.096 habitantes para testes e evolução de escala.
- Há sedes, escritórios, instituto, universidade, data center, praça, café, terminal e residências.
- Ações: sono, alimentação, trabalho, pesquisa, aprendizado, socialização, relaxamento e observação.
- Empresas possuem caixa, departamentos, estratégia, tecnologia, produtos, qualidade, inovação, preço, vendas, receita acumulada e métricas diárias.
- Fechamentos diários calculam produtividade a partir da equipe, folha salarial, operações, pesquisa, demanda, concorrência, receita, despesa e lucro.
- Contratações usam caixa, estratégia, produtividade e candidatos realmente desempregados.
- Falências encerram vínculos, aposentam produtos lançados e ficam no histórico. Empregados liberados recebem o salário já debitado mesmo quando a empresa quebra; recontratação por empresa solvente no mesmo fechamento continua permitida.
- O agregado de tecnologia permanece finito e persistível quando não existe empresa ativa.
- Conclusões de jobs só alteram resumo e recompensa quando seu timestamp é positivo e posterior à última conclusão aplicada; callbacks antigos ou duplicados não reescrevem o histórico.
- A fundação pelo usuário debita ₡25.000 da reserva e cria empresa, sede e primeiro produto no estado real.
- Festival, blackout, fomento e expansão de mercado alteram necessidades, infraestrutura, caixa, tecnologia ou demanda.

### Persistência e runtime

- `WorldRuntime` é process-wide, inicializado pelo `LabForgeApp`, com `Mutex`, `StateFlow` e escopo próprio.
- O load instala o snapshot, aplica retomada temporal, reconcilia o estado atual dos jobs, atualiza os fluxos e grava antes de liberar mutações e saves que aguardam `loadReady`.
- O collector de jobs relê o estado mais recente dentro da mutação protegida, evitando aplicar um valor obsoleto após esperar o mutex.
- Mutações com a simulação pausada têm save automático com debounce de 400 ms; save manual aguarda o load e cancela o debounce pendente.
- Avisos transitórios de save e avisos de recuperação são independentes. O aviso de recuperação sobrevive ao save inicial e só é dispensado explicitamente pela UI.
- `WorldSnapshotStore` grava envelope JSON com SHA-256, arquivo temporário, `fsync` e backup conhecido.
- O load tenta o snapshot principal, depois o backup e só então cria um mundo novo. Somente o schema atualmente suportado é aceito; versões diferentes exigirão um reader/migração explícita.
- Arquivos inválidos são preservados; antes de uma nova gravação, snapshots corrompidos são movidos para arquivos `.corrupt*` em vez de silenciosamente destruídos.
- Memórias, atividades, eventos e históricos têm limites para conter crescimento.

### Renderização e interação

- `GLSurfaceView` com OpenGL ES 3, meshes procedurais, VAO/VBO/IBO e shaders próprios.
- Edifícios em camadas, janelas emissivas, habitantes, ruas, iluminação perimetral, praça e holograma de jobs reais.
- Iluminação difusa, especular, rim light, emissive e fog. Sombras atuais são projeções/blob shadows, não shadow mapping.
- Ciclo dia/noite deriva de `minuteOfDay`.
- Câmera orbital por instância de renderer, rotação, pan, zoom, foco animado e picking por raio/esfera.
- O picking usa exatamente o snapshot e a lista de habitantes do último frame renderizado, não os primeiros elementos do mundo. Habitantes têm prioridade sobre edifícios quando o mesmo raio intersecta ambos.
- Ao sair do gesto de dois pontos, as coordenadas passam a vir do dedo remanescente, evitando salto pela leitura do dedo levantado.
- O limite visual é de 96 agentes em aparelhos com pelo menos 6 núcleos e 64 nos demais. FPS é medido por frames renderizados e a contagem de agentes visíveis vem do frame efetivamente publicado.
- A reordenação por proximidade só ocorre quando mudam o snapshot ou o alvo da câmera. Cores RGBA e sombras constantes são reutilizadas. São otimizações estruturais, sem alegação de ganho medido em aparelho.
- Falha de OpenGL mantém a simulação e os painéis acessíveis e informa a limitação.

### Interface e integração com o Girassol

- Nova rota e aba `MUNDO`, sem remoção das rotas anteriores.
- Card da Home usa dia, população, empresas ativas, locais e último resultado econômico do estado coletado.
- Painéis de habitantes, empresas, locais, economia, eventos e parâmetros permitidos, alinhados à parte inferior sem cobrir a top bar.
- Seleção contextual mostra necessidades, ação, objetivos, habilidades, relações, memórias, atividades, departamentos, produtos, ocupantes e gráficos derivados dos dados.
- Avisos de recuperação são exibidos separadamente de erros transitórios e podem ser dispensados sem apagar o diagnóstico de save.
- Happenings e fundação liberam o estado `busy` em `finally`; cancelar diálogo não limpa o `busy` de outra operação concorrente.
- Conversa com habitante reutiliza `LlmClient`, endpoint, modelo, chave e temperatura configurados. O prompt usa o estado real; falhas são exibidas sem resposta inventada.
- Lições do `LessonStore` ampliam tecnologia do Instituto e conhecimento/memórias dos pesquisadores.
- Jobs reais do `JobEngine` atualizam o Núcleo de Inferência.
- O chat ReAct recebe `world_summary`, `world_agent`, `world_events` e `world_happening`. As três primeiras ferramentas são de consulta; a última aplica somente os quatro acontecimentos permitidos.

### Atividade, transições e feedback no MUNDO

- `AgentActivityLog` combina ações atuais e histórico persistido em uma timeline cronológica com filtros, contadores, cores por ação e indicadores pulsantes para estudo, trabalho e sono. O painel é uma nova opção do dock e não substitui os painéis existentes.
- `WorldActivityTracker` observa novos `ActivityRecord` sem repetir o snapshot inicial ou restaurado. Diferencia habitante novo de habitante conhecido sem histórico e acompanha substituições da janela limitada de atividades.
- Estudo concluído gera toast com nome, duração e local reais. A dispensa por identificador impede que o temporizador de um toast antigo feche uma notificação mais recente.
- Mudanças reais `LEARN` → `SLEEP` ou `WORK` do habitante selecionado acionam `CinematicSceneTransition`: perspectiva, escala e portal radial em Compose, com foco de câmera no edifício de destino real. Não foram inventados interiores nem uma camada React/Framer Motion.
- O ciclo de Modo Sono gera toast somente depois de `LlamatikRuntime.consolidate()` persistir a memória com sucesso. Falhas não produzem notificação de sucesso, e consolidações concorrentes são bloqueadas. O ciclo existente consolida/persiste memória; não executa uma otimização adicional comprovada.

### Sessão, throughput e diagnóstico

- O rodapé do chat exporta JSON estruturado por SAF com sessão, métricas, mensagens e pensamentos já visíveis. O trace não inclui estado interno oculto, endpoint, API key ou configurações. Credenciais comuns presentes no conteúdo são redigidas, e falhas reais de gravação são mostradas ao usuário.
- `AgentEvent.Usage` atualiza tempo decorrido e tokens/s durante a execução. `PerformanceGauge` apresenta o valor real com escala visual de 0–120 t/s; o painel declara que utilização GPU/NPU não é exposta por uma fonte autorizada e não usa estimativas de hardware.
- `ConsensusBus` anima somente transferências fornecidas por uma fonte real. A integração atual tem um agente Llamatik e nenhuma transferência, portanto informa a ausência de fluxo em vez de encenar consenso multiagente.
- `TrainingCheckpointStore` registra até 100 checkpoints de treinos LoRA/full concluídos, com gravação temporária, `fsync`, rename e deduplicação. `JobEngine` grava o histórico antes de publicar o estado `done` e guarda loss final somente quando finita e não negativa.
- `LoraMergingTimeline` mostra checkpoints e loss reais, inclusive ausência do arquivo quando aplicável. Não há operação de merging nem medição de divergência no motor; a UI identifica os registros como treinos, informa que nenhum merge ocorreu e exibe divergência como não medida.

## Arquivos principais

### Novos

- `app/src/main/java/dev/labforge/app/core/world/WorldModels.kt`
- `app/src/main/java/dev/labforge/app/core/world/WorldRandom.kt`
- `app/src/main/java/dev/labforge/app/core/world/WorldFactory.kt`
- `app/src/main/java/dev/labforge/app/core/world/WorldEngine.kt`
- `app/src/main/java/dev/labforge/app/core/world/WorldSnapshotStore.kt`
- `app/src/main/java/dev/labforge/app/core/world/WorldRuntime.kt`
- `app/src/main/java/dev/labforge/app/core/world/WorldTools.kt`
- `app/src/main/java/dev/labforge/app/vm/WorldViewModel.kt`
- `app/src/main/java/dev/labforge/app/ui/world/WorldCamera.kt`
- `app/src/main/java/dev/labforge/app/ui/world/WorldMeshes.kt`
- `app/src/main/java/dev/labforge/app/ui/world/WorldRenderer.kt`
- `app/src/main/java/dev/labforge/app/ui/world/WorldSceneView.kt`
- `app/src/main/java/dev/labforge/app/ui/screens/WorldScreen.kt`
- `app/src/main/java/dev/labforge/app/core/world/WorldActivityTracker.kt`
- `app/src/main/java/dev/labforge/app/ui/world/AgentActivityLog.kt`
- `app/src/main/java/dev/labforge/app/ui/world/CinematicSceneTransition.kt`
- `app/src/main/java/dev/labforge/app/ui/components/PremiumToast.kt`
- `app/src/main/java/dev/labforge/app/vm/AgentSessionExport.kt`
- `app/src/main/java/dev/labforge/app/ui/components/PerformanceMonitor.kt`
- `app/src/main/java/dev/labforge/app/ui/components/ConsensusBus.kt`
- `app/src/main/java/dev/labforge/app/core/train/TrainingCheckpointStore.kt`
- `app/src/main/java/dev/labforge/app/ui/components/LoraMergingTimeline.kt`
- `app/src/test/java/dev/labforge/app/core/world/WorldEngineTest.kt`
- `app/src/test/java/dev/labforge/app/core/world/WorldSnapshotStoreTest.kt`
- `app/src/test/java/dev/labforge/app/core/world/WorldRuntimeTest.kt`
- `app/src/test/java/dev/labforge/app/ui/world/WorldCameraTest.kt`
- `app/src/test/java/dev/labforge/app/core/world/WorldActivityTrackerTest.kt`
- `app/src/test/java/dev/labforge/app/vm/AgentSessionExportTest.kt`
- `app/src/test/java/dev/labforge/app/core/train/TrainingCheckpointStoreTest.kt`

### Modificados sem remoção das áreas existentes

- `app/src/main/java/dev/labforge/app/LabForgeApp.kt`
- `app/src/main/java/dev/labforge/app/ui/AppRoot.kt`
- `app/src/main/java/dev/labforge/app/ui/screens/HomeScreen.kt`
- `app/src/main/java/dev/labforge/app/core/agent/ReActAgent.kt`
- `app/src/main/java/dev/labforge/app/vm/AgentViewModel.kt`
- `app/src/main/java/dev/labforge/app/vm/WorldViewModel.kt`
- `app/src/main/java/dev/labforge/app/ui/screens/AgentScreen.kt`
- `app/src/main/java/dev/labforge/app/vm/LlamatikViewModel.kt`
- `app/src/main/java/dev/labforge/app/ui/screens/LlamatikScreen.kt`
- `app/src/main/java/dev/labforge/app/integration/llamatik/LlamatikRuntime.kt`
- `app/src/main/java/dev/labforge/app/core/job/JobEngine.kt`
- `app/src/main/java/dev/labforge/app/vm/LabViewModel.kt`
- `app/src/main/java/dev/labforge/app/ui/screens/TrainPane.kt`
- `.github/workflows/android.yml` — ampliação preparada somente na cópia local; não transferida ao PR por falta do escopo `workflow`.

## Testes adicionados

### Motor

- Determinismo por seed e entradas.
- Prioridade do sono em energia crítica.
- Cadências diferentes de LOD.
- Compensação e limite de retomada.
- Balanço de receitas, despesas e lucros; produtos oriundos do desenvolvimento.
- Falência com liberação de vínculos, crédito do salário debitado, registro e recontratação somente por empresa solvente.
- Acontecimentos e fundação alterando o estado.
- Sincronização de lições e jobs reais, incluindo rejeição de conclusões antigas ou duplicadas.
- Rejeição de versões de schema sem reader explícito.
- Histórias diferentes para seeds diferentes.

### Persistência

- Round-trip completo.
- Recuperação pelo último backup íntegro.
- Não substituição silenciosa no load.
- Detecção de adulteração por checksum.
- Estabilidade do SHA-256.
- Preservação de arquivo corrompido durante save após recuperação.
- Economia sem empresas ativas mantendo tecnologia finita e round-trip completo.

### Runtime

- Mutação solicitada durante o load é aplicada ao snapshot instalado, não ao mundo provisório.
- Aviso de recuperação sobrevive ao save inicial até dispensa explícita.
- Happening aplicado com a simulação pausada é persistido após 400 ms, sem depender de tick ou disposal.

### Câmera

- Raio central coerente com a câmera.
- Interseção positiva mais próxima e rejeição de objetos atrás.
- Saída correta quando a origem está dentro da esfera.
- Limites de zoom/pitch e convergência do foco.
- Targets derivados somente dos habitantes efetivamente renderizados e da geometria persistida.
- Habitante prioritário quando o raio também intersecta a esfera de um edifício.

### Atividades, exportação e checkpoints

- Snapshot restaurado não repete conclusões; somente estudo novo gera toast e somente uma vez.
- Substituição do histórico limitado emite cada estudo novo; transição cinematográfica exige `LEARN` → `SLEEP`/`WORK`.
- Exportação inclui pensamentos visíveis, ferramentas e métricas e redige credenciais comuns sem apagar eventos.
- Checkpoints persistem e deduplicam loss real; saves distintos permanecem separados, `NaN` é tratado como ausente e nenhum campo de divergência é inventado.

**Situação:** suíte local final concluída com 70 testes aprovados e 7 skips opt-in, sem falhas nem erros. Os oito testes desta rodada foram aprovados separadamente e estão incluídos no total. O teste de falência exige que nenhum vínculo permaneça na empresa encerrada e permite recontratação legítima por empresa solvente no mesmo fechamento. O teste de câmera verifica separadamente o clamp do fator de zoom (distância 144 após o primeiro gesto extremo) e o limite máximo de distância (145 após o segundo).

## CI, segurança e artifacts

- O workflow **remoto atual** executa build, testes unitários, upload de APK e relatório de testes, com release no mesmo job para tags. Ele ainda usa `contents: write` global e não executa `lintDebug`.
- O run remoto `43009566933` (run 205), no SHA `05331486764da025a9ab7f57af31f50a1b8625fe`, falhou em `Assemble debug APK`; `Unit tests` e `Upload APK` foram pulados. O upload condicional do relatório apareceu como sucesso com `if-no-files-found: ignore`, o que não comprova a existência de resultados. Nenhum artifact remoto foi validado nesse run.
- O run remoto `43021759034` (run 206), no SHA `01e781d00a25a7313d70697f4c2f0366b9e7a9f3`, foi concluído com **sucesso** em 2026-11-11, incluindo build e testes no job `Build, test & release`.
- Foram confirmados dois artifacts não expirados no run 206: `girassol-debug-apk`, 29.292.144 bytes, digest `sha256:4438c979c33b3a67fcd49b3f70c8a5936dea60a2eabefaceff0a296eda2e2feb`; e `test-report`, 42.472 bytes, digest `sha256:d2b3365ef42e1848b59bc8ed62448ccac8fa23e1ec8771fd0cf53af5964fb384`. Esses digests identificam os artifacts publicados, não o APK local descompactado.
- O run remoto `43022486603` (run 207), no SHA `f6286f6b013cea96c5a4beadf672f047305ca3ea`, foi concluído com **sucesso** em 2026-11-11, incluindo os checks `Build, test & release` e CodeRabbit.
- Foram confirmados dois artifacts não expirados no run 207: `girassol-debug-apk`, ID `13854699580`, 29.292.145 bytes, digest `sha256:a8f0d7e0abc7a84ff20747c2d24c29dade20ce5c7ada008968c88b4904873e49`; e `test-report`, ID `13856190391`, 42.419 bytes, digest `sha256:1f6de1309e93fbbb3c63359249bf344aae89db5baa0038b3f3ed8c6376b607a6`. Esses digests identificam os artifacts compactados, não o APK descompactado. O run 207 antecede as correções locais desta rodada e não as valida.
- O run remoto `43145599528` (run 212), no SHA `e5096782a233f74f5c56e6ab2fb3c91329dc8bba`, foi concluído com **sucesso** em 2026-11-12 sobre a árvore de código alinhada à versão local testada. Foram confirmados `girassol-debug-apk`, ID `13911626561`, 29.299.824 bytes, digest `sha256:dfee65bb035e2935a05552effa00b375f2c197240571b7bb35f6c75763057786`; e `test-report`, ID `13911318522`, 43.883 bytes, digest `sha256:195bce16730144c405bf4acac55da7af90470931bb3035a6b04fbba02953b0a2`.
- O run remoto `43145984093` (run 213), no SHA `4dab9d0576c6b3f89c50ae7ea70c32f948ae8795`, foi concluído com **sucesso** em 2026-11-12. `Assemble debug APK`, `Unit tests`, `Upload APK` e `Upload test report` foram concluídos com sucesso; `Publish release` foi pulado por não ser uma tag. Foram confirmados `girassol-debug-apk`, ID `13911801865`, 29.299.820 bytes, digest `sha256:a2eee58595853ae0e5ebd8f3f50c2ab8c5d1585d52da491048c4fb0cb96bc657`; e `test-report`, ID `13911289298`, 43.881 bytes, digest `sha256:b3835924257773f29356d9bca9a5ea1030b89cd8aa9f91ec160b745d59de1a2b`.
- O run remoto `43158647261` (run 214), no SHA `5224c55e0cb23d804140842e422afaa77cc3dff9`, foi concluído com **sucesso** em 2026-11-12. Foram confirmados `girassol-debug-apk`, ID `13917221840`, 29.299.824 bytes, digest `sha256:ef70b4a72e03b9508a5438b868eec758361f43dde9bbf7df37dda3389ea25a3a`; e `test-report`, ID `13917590590`, 43.850 bytes, digest `sha256:9765db363fb43c09bfe003eadb3178ba3eaa7124de07aebadf6918896d50d2d3`. Esse run antecede as funcionalidades desta rodada.
- O run remoto `43182415622` (run 220), no SHA `d8c79eee65cf256221c1664e9409759cbb9487f8`, foi concluído com **sucesso** em 2026-11-12 sobre todo o código desta rodada. `Assemble debug APK`, `Unit tests`, `Upload APK` e `Upload test report` foram concluídos com sucesso; `Publish release` foi pulado por não ser uma tag. Foram confirmados `girassol-debug-apk`, ID `13927191153`, 29.398.953 bytes, digest `sha256:2f9eba2136fa5caeac16dd972389b5a100c079ee31d8bf65bb43fc5b6c186476`; e `test-report`, ID `13926866691`, 47.529 bytes, digest `sha256:152c7fca78f3080bc6db77ff5459d4580457b5b7c399abcc958a16ad028e493c`. Ambos estão não expirados, com expiração registrada para 2027-02-10T08:22:34Z.
- Os 23 arquivos de código e testes desta rodada foram comparados por SHA-256 entre a árvore local submetida à verificação integral e o clone do SHA `d8c79eee65cf256221c1664e9409759cbb9487f8`: **23/23 idênticos, sem divergências**. Isso comprova equivalência desses arquivos, não equivalência dos APKs nem validação física/visual.
- A tentativa de baixar o `test-report` do run 213 pela API pública respondeu HTTP 403. A conferência remota desta etapa cobre status, steps e metadados dos artifacts não expirados; não se afirma inspeção do conteúdo compactado nem equivalência entre seu APK e o APK local.
- O log remoto detalhado não foi obtido. Os quatro erros de compilação abaixo foram reproduzidos e corrigidos localmente, mas não se afirma que sejam comprovadamente a causa do run remoto.
- A ampliação local do workflow contém triggers adicionais, `contents: read`, `lintDebug`, artifacts de lint e release separado com `contents: write` somente para tags. Seu envio foi recusado por falta do escopo `workflow`; a permissão não foi contornada e essa versão ainda não está no GitHub.
- Nenhum token, credencial ou segredo foi incluído no código ou neste relatório.
- Workflow verde não será tratado como prova de funcionamento visual ou de desempenho em dispositivo físico.

## Erros e correções durante a implementação

- Restaurado o import de `LabViewModel` ao integrar o `WorldViewModel` na Home.
- Corrigido literal hexadecimal e removido código residual sem efeito na factory.
- Movido `WorldViewModel` para o pacote `vm` correto.
- Corrigido `glUniform3fv` para um vec3 por uniform.
- Câmera deixou de ser compartilhada entre renderers.
- Targets de ações receberam deslocamento determinístico para evitar sobreposição exata.
- Rotina noturna passou a reduzir trabalho/pesquisa e priorizar sono.
- Métricas de equipe passaram a incluir contratações do fechamento.
- O agregado mundial passou a preservar receita/despesa do dia da falência.
- Corrigida a numeração de eventos de empresas fundadas.
- O teste de energia crítica passou a permitir o deslocamento até a residência antes de verificar recuperação.
- O card da Home passou a rotular lucro como resultado, não como caixa.
- Uma divergência de transferência em `WorldMeshes.kt` foi detectada por comparação de SHA de blob e corrigida; o SHA final local/remoto conferido foi `3d75cd85f974016c905ecc41b2dcb2a08a940776`.
- Uma divergência de transferência em `WorldCamera.kt` foi isolada ao tipo de retorno explícito `: WorldVec3` em `cross`; a versão local testada foi regravada e o SHA final local/remoto conferido foi `fcd3be7e9f57e118d770ec05fc7e5d28a1ea3ac3`.
- Removida a chamada não compilável `GLES30.glEnable(GLES30.GL_MULTISAMPLE)`. Em OpenGL ES, MSAA pertence à configuração EGL; não havia funcionalidade MSAA compilável sendo removida.
- `AgentViewModel` passou a manter `worldRuntime` em uma propriedade, pois o parâmetro `app` do construtor não era acessível na lambda membro.
- Adicionados os imports das extensões `day` e `minuteOfDay` em `WorldViewModel`.
- Após essas correções, `assembleDebug` passou localmente. A suíte então expôs duas expectativas incorretas, ambas ajustadas sem alterar a economia ou a câmera de produção: recontratação por empresa solvente após falência é válida, e o primeiro zoom extremo produz 144 antes de uma segunda chamada atingir 145.
- Payroll na falência passou a creditar aos empregados liberados o salário já debitado da empresa.
- Conclusões antigas ou duplicadas de jobs deixaram de reescrever resumo e repetir recompensas; tecnologia sem empresas ativas deixou de produzir `NaN`.
- Mutações e saves solicitados durante o load passaram a aguardar a instalação do snapshot; happenings pausados receberam persistência com debounce.
- O aviso de recuperação foi separado de erros transitórios de save para não desaparecer após uma gravação bem-sucedida.
- Picking passou a compartilhar a lista efetivamente renderizada e a priorizar habitantes; `ACTION_POINTER_UP` passou a usar o dedo remanescente.
- Painéis inferiores deixaram de cobrir a top bar e o cancelamento de diálogo deixou de liberar `busy` pertencente a outra operação.
- Renderer passou a reutilizar seleção de agentes, cores e sombras constantes. Não houve medição em aparelho para quantificar o efeito.
- `PerformanceMonitor.kt` recebeu o import ausente de `fillMaxSize`; `WorldViewModel.kt` recebeu os imports de `agent`, `company` e, sobretudo, `building`, resolvendo as três referências não resolvidas do compilador.
- O tracker passou a aguardar o carregamento assíncrono e a usar `knownAgents`, evitando replay do snapshot restaurado e permitindo a primeira atividade real de um habitante conhecido sem histórico. A busca do registro anterior usa `indexOfFirst`, sem depender de `indexOf` com nullable.
- `JobEngine` passou a persistir o checkpoint antes de publicar `done`, eliminando a corrida em que o ViewModel relia o histórico cedo demais.
- `consolidate()` deixou de esconder falhas de persistência atrás de `runCatching`; `newSession()` também passou a zerar `startedAt` para não exportar timestamp de uma sessão anterior.
- Quatro fixtures de `WorldActivityTrackerTest` usavam `population = 4` e falhavam no contrato da factory. Foram corrigidos para oito habitantes; a regra de produção `8..4_096` não foi enfraquecida. Os oito testes direcionados passaram na repetição.
- A primeira verificação integral desta rodada gerou 77 testes sem falhas, mas o lint encontrou `NonObservableLocale` em `LoraMergingTimeline.kt`, apesar de `lint.abortOnError=false` permitir um build verde. `Locale.getDefault()` foi substituído por `LocalLocale.current.platformLocale`; a verificação completa foi repetida e o lint final ficou sem erros.

## Limitações e próximas tarefas

1. Obter autorização com escopo `workflow` para enviar a ampliação com lint, artifacts de lint e permissões mínimas; até lá, manter inalterado o workflow remoto e registrar sua divergência em relação à versão local.
2. Validar o APK em emulador e aparelho arm64, incluindo gestures, picking, OpenGL, memória e retomada de processo. Nenhum teste físico ou emulado foi executado nesta rodada.
3. Validar visualmente as novas animações, timeline, toasts, gauge e exportação via SAF; compilação e testes unitários não comprovam a apresentação nem a interação na tela.
4. Executar estresse com milhares de agentes e medir memória/tempo de tick; o limite de 4.096 da factory não é evidência de desempenho validado.
5. Evoluir para interiores exploráveis e mais cidades sem quebrar a migração de snapshots.
6. Evoluir materiais, texturas/PBR, shadow mapping e, se validado, avaliar Filament/glTF da branch de Studio 3D sem misturar runtimes nativos prematuramente.
7. Adicionar reflexão periódica por LLM com orçamento e comunicação autônoma entre habitantes, mantendo o LOD e sem fingir ferramentas.
8. Exercitar e ampliar testes Compose/Robolectric para navegação e painéis. Os sete testes opt-in de rede/fluxo ao vivo continuam skipped, não aprovados.
9. Integrar mais profundamente Codex e árvore de habilidades, preservando os sistemas já existentes.
10. Adicionar uma fonte autorizada antes de exibir utilização real de GPU/NPU; atualmente ela permanece indisponível.
11. Implementar consenso multiagente e transferências reais antes de ativar fluxo animado no `ConsensusBus`; a integração atual não possui esse consenso.
12. Implementar merging LoRA e uma métrica definida de divergência antes de apresentá-los como dados; atualmente existem somente checkpoints de treino e loss final real. O Modo Sono permanece limitado à consolidação/persistência de memória existente, sem otimização adicional comprovada.
