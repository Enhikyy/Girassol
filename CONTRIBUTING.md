# Contribuindo

## Ambiente

- JDK 21;
- Android SDK 37.0 + build-tools 37.0.0;
- Gradle wrapper 9.9.0;
- NDK 26.1.10909125 apenas se for reconstruir o `.so`.

```bash
export JAVA_HOME=/caminho/jdk-21
export ANDROID_HOME=/caminho/android-sdk
./gradlew :app:testDebugUnitTest
```

## Fluxo

1. abra uma issue descrevendo o problema/feature;
2. crie uma branch `feat/...` ou `fix/...`;
3. rode os testes (e `LIVE_API=1` quando tocar em pesquisa/API);
4. abra um PR com:
   - o que mudou;
   - como testou;
   - prints (se UI).

## Código

- Kotlin + Compose, sem bibliotecas novas sem justificativa.
- ViewModels não fazem I/O na main thread.
- Estado de Compose só na main thread.
- Não remova a exclusão de `com.llamatik:core` sem resolver a colisão de SONAME.
- Novas tools de agente devem ser registradas no `ToolRegistry` e, se fizer sentido, no `AgentTools`.

## Commits

Conventional Commits: `feat:`, `fix:`, `docs:`, `test:`, `chore:`.

## Testes

```bash
./gradlew :app:testDebugUnitTest
LIVE_API=1 ./gradlew :app:testDebugUnitTest --rerun-tasks
```

Testes live dependem de rede e podem ser instáveis; não os use para bloquear um PR sem necessidade.
