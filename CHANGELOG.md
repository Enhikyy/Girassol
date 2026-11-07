# Changelog

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
