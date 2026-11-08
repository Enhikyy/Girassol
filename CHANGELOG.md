# Changelog

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
