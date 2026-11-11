# GIRASSOL / MUNDO — Progresso de engenharia

Atualizado em 2026-11-11. Branch de trabalho: `feat/mundo-simulation`. Base: `main` em `52a3f11afce7b009d121163244c3b34860628203`.

## Estado desta entrega

Esta branch contém uma fatia vertical funcional em código do universo persistente, não a conclusão de toda a visão do MUNDO. O motor, a economia, a persistência, a cena OpenGL, os painéis e as integrações usam o mesmo `WorldState`. Não há gráficos históricos preenchidos com valores aleatórios nem respostas fictícias quando a inferência falha.

**Verificação local em 2026-11-11:** o ambiente foi provisionado com Temurin 21.0.13, Gradle 9.9.0 e Android SDK; os runtimes Android instrumentados do Robolectric foram disponibilizados localmente para evitar downloads durante a suíte.

- `:app:assembleDebug`: **BUILD SUCCESSFUL**. APK debug com 37.320.164 bytes e SHA-256 `62f03fdfd9588dfc2e503ffb9ad57f3eeb72ba58cae747f830f4ad19fe3a74e3`.
- APK verificado com assinatura APK Signature Scheme v2 válida e um signer; pacote `app.girassol`, `versionCode 11`, `versionName 1.6.1`, `minSdk 26`, `targetSdk 34` e somente `arm64-v8a`. As bibliotecas nativas do llama.cpp/JNI e demais dependências esperadas estão empacotadas.
- `:app:testDebugUnitTest`: **BUILD SUCCESSFUL**; 62 testes, 55 aprovados, 0 falhas, 0 erros e 7 ignorados. Os sete skips são testes opt-in de rede/fluxo ao vivo condicionados a `LIVE_API=1`: cinco em `LiveResearchSmokeTest`, um em `PublicApiLiveTest` e um fluxo Compose em `AutodidactFlowTest`. Eles não foram executados nem contados como aprovações.
- `:app:lintDebug`: **BUILD SUCCESSFUL**; 0 erros, 48 avisos e 41 sugestões. Os relatórios locais estão em `app/build/reports/lint-results-debug.*`.

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
- Falências encerram vínculos, aposentam produtos lançados e ficam no histórico.
- A fundação pelo usuário debita ₡25.000 da reserva e cria empresa, sede e primeiro produto no estado real.
- Festival, blackout, fomento e expansão de mercado alteram necessidades, infraestrutura, caixa, tecnologia ou demanda.

### Persistência e runtime

- `WorldRuntime` é process-wide, inicializado pelo `LabForgeApp`, com `Mutex`, `StateFlow` e escopo próprio.
- `WorldSnapshotStore` grava envelope JSON com SHA-256, arquivo temporário, `fsync` e backup conhecido.
- O load tenta o snapshot principal, depois o backup e só então cria um mundo novo.
- Arquivos inválidos são preservados; antes de uma nova gravação, snapshots corrompidos são movidos para arquivos `.corrupt*` em vez de silenciosamente destruídos.
- Memórias, atividades, eventos e históricos têm limites para conter crescimento.

### Renderização e interação

- `GLSurfaceView` com OpenGL ES 3, meshes procedurais, VAO/VBO/IBO e shaders próprios.
- Edifícios em camadas, janelas emissivas, habitantes, ruas, iluminação perimetral, praça e holograma de jobs reais.
- Iluminação difusa, especular, rim light, emissive e fog. Sombras atuais são projeções/blob shadows, não shadow mapping.
- Ciclo dia/noite deriva de `minuteOfDay`.
- Câmera orbital por instância de renderer, rotação, pan, zoom, foco animado e picking por raio/esfera.
- O limite visual é de 96 agentes em aparelhos com pelo menos 6 núcleos e 64 nos demais. FPS é medido por frames renderizados.
- Falha de OpenGL mantém a simulação e os painéis acessíveis e informa a limitação.

### Interface e integração com o Girassol

- Nova rota e aba `MUNDO`, sem remoção das rotas anteriores.
- Card da Home usa dia, população, empresas ativas, locais e último resultado econômico do estado coletado.
- Painéis de habitantes, empresas, locais, economia, eventos e parâmetros permitidos.
- Seleção contextual mostra necessidades, ação, objetivos, habilidades, relações, memórias, atividades, departamentos, produtos, ocupantes e gráficos derivados dos dados.
- Conversa com habitante reutiliza `LlmClient`, endpoint, modelo, chave e temperatura configurados. O prompt usa o estado real; falhas são exibidas sem resposta inventada.
- Lições do `LessonStore` ampliam tecnologia do Instituto e conhecimento/memórias dos pesquisadores.
- Jobs reais do `JobEngine` atualizam o Núcleo de Inferência.
- O chat ReAct recebe `world_summary`, `world_agent`, `world_events` e `world_happening`. As três primeiras ferramentas são de consulta; a última aplica somente os quatro acontecimentos permitidos.

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
- `app/src/test/java/dev/labforge/app/core/world/WorldEngineTest.kt`
- `app/src/test/java/dev/labforge/app/core/world/WorldSnapshotStoreTest.kt`
- `app/src/test/java/dev/labforge/app/ui/world/WorldCameraTest.kt`

### Modificados sem remoção das áreas existentes

- `app/src/main/java/dev/labforge/app/LabForgeApp.kt`
- `app/src/main/java/dev/labforge/app/ui/AppRoot.kt`
- `app/src/main/java/dev/labforge/app/ui/screens/HomeScreen.kt`
- `app/src/main/java/dev/labforge/app/core/agent/ReActAgent.kt`
- `app/src/main/java/dev/labforge/app/vm/AgentViewModel.kt`
- `.github/workflows/android.yml` — ampliação preparada somente na cópia local; não transferida ao PR por falta do escopo `workflow`.

## Testes adicionados

### Motor

- Determinismo por seed e entradas.
- Prioridade do sono em energia crítica.
- Cadências diferentes de LOD.
- Compensação e limite de retomada.
- Balanço de receitas, despesas e lucros; produtos oriundos do desenvolvimento.
- Falência com liberação de vínculos e registro.
- Acontecimentos e fundação alterando o estado.
- Sincronização de lições e jobs reais.
- Histórias diferentes para seeds diferentes.

### Persistência

- Round-trip completo.
- Recuperação pelo último backup íntegro.
- Não substituição silenciosa no load.
- Detecção de adulteração por checksum.
- Estabilidade do SHA-256.
- Preservação de arquivo corrompido durante save após recuperação.

### Câmera

- Raio central coerente com a câmera.
- Interseção positiva mais próxima e rejeição de objetos atrás.
- Saída correta quando a origem está dentro da esfera.
- Limites de zoom/pitch e convergência do foco.
- Targets derivados da geometria persistida.

**Situação:** suíte local concluída com 55 testes aprovados e 7 skips opt-in, sem falhas. O teste de falência exige que nenhum vínculo permaneça na empresa encerrada e permite recontratação legítima por empresa solvente no mesmo fechamento. O teste de câmera verifica separadamente o clamp do fator de zoom (distância 144 após o primeiro gesto extremo) e o limite máximo de distância (145 após o segundo).

## CI, segurança e artifacts

- O workflow **remoto atual** executa build, testes unitários, upload de APK e relatório de testes, com release no mesmo job para tags. Ele ainda usa `contents: write` global e não executa `lintDebug`.
- O run remoto `43009566933` (run 205), no SHA `05331486764da025a9ab7f57af31f50a1b8625fe`, falhou em `Assemble debug APK`; `Unit tests` e `Upload APK` foram pulados. O upload condicional do relatório apareceu como sucesso com `if-no-files-found: ignore`, o que não comprova a existência de resultados. Nenhum artifact remoto foi validado nesse run.
- O run remoto `43021759034` (run 206), no SHA `01e781d00a25a7313d70697f4c2f0366b9e7a9f3`, foi concluído com **sucesso** em 2026-11-11, incluindo build e testes no job `Build, test & release`.
- Foram confirmados dois artifacts não expirados no run 206: `girassol-debug-apk`, 29.292.144 bytes, digest `sha256:4438c979c33b3a67fcd49b3f70c8a5936dea60a2eabefaceff0a296eda2e2feb`; e `test-report`, 42.472 bytes, digest `sha256:d2b3365ef42e1848b59bc8ed62448ccac8fa23e1ec8771fd0cf53af5964fb384`. Esses digests identificam os artifacts publicados, não o APK local descompactado.
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
- Removida a chamada não compilável `GLES30.glEnable(GLES30.GL_MULTISAMPLE)`. Em OpenGL ES, MSAA pertence à configuração EGL; não havia funcionalidade MSAA compilável sendo removida.
- `AgentViewModel` passou a manter `worldRuntime` em uma propriedade, pois o parâmetro `app` do construtor não era acessível na lambda membro.
- Adicionados os imports das extensões `day` e `minuteOfDay` em `WorldViewModel`.
- Após essas correções, `assembleDebug` passou localmente. A suíte então expôs duas expectativas incorretas, ambas ajustadas sem alterar a economia ou a câmera de produção: recontratação por empresa solvente após falência é válida, e o primeiro zoom extremo produz 144 antes de uma segunda chamada atingir 145.

## Limitações e próximas tarefas

1. Obter autorização com escopo `workflow` para enviar a ampliação com lint, artifacts de lint e permissões mínimas; até lá, manter inalterado o workflow remoto e registrar sua divergência em relação à versão local.
2. Validar o APK em emulador e aparelho arm64, incluindo gestures, picking, OpenGL, memória e retomada de processo.
3. Executar estresse com milhares de agentes e medir memória/tempo de tick; o limite de 4.096 da factory não é evidência de desempenho validado.
4. Evoluir para interiores exploráveis e mais cidades sem quebrar a migração de snapshots.
5. Evoluir materiais, texturas/PBR, shadow mapping e, se validado, avaliar Filament/glTF da branch de Studio 3D sem misturar runtimes nativos prematuramente.
6. Adicionar reflexão periódica por LLM com orçamento e comunicação autônoma entre habitantes, mantendo o LOD e sem fingir ferramentas.
7. Exercitar e ampliar testes Compose/Robolectric para navegação e painéis.
8. Integrar mais profundamente Codex e árvore de habilidades, preservando os sistemas já existentes.
