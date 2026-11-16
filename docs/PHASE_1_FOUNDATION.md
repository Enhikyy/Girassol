# Fase 1 — Fundação do Girassol

## Estrutura do projeto

```text
Girassol/
├── .github/
│   └── workflows/
│       └── android_build.yml       # CI: JDK 21, Android SDK/NDK, testes e APK
├── app/
│   ├── build.gradle.kts            # Android, Kotlin, Compose, NDK e dependências
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml # permissões, capabilities e componentes Android
│       │   ├── cpp/
│       │   │   ├── CMakeLists.txt  # engine nativa e link Vulkan
│       │   │   └── labforge_jni.cpp
│       │   └── java/dev/labforge/app/
│       │       ├── service/        # Foreground Service e WakeLock
│       │       ├── core/world/     # runtime, simulação e cognição
│       │       ├── ui/             # interface Compose
│       │       └── vm/             # ViewModels
│       └── test/java/dev/labforge/app/
│           └── ExampleUnitTest.kt  # contratos básicos do módulo Android
├── gradle/
│   └── wrapper/                    # Gradle Wrapper 9.9
├── docs/
│   └── PHASE_1_FOUNDATION.md
├── build.gradle.kts                # versões dos plugins Gradle
├── gradle.properties
├── settings.gradle.kts
└── gradlew
```

## Contrato de build

| Camada | Configuração |
|---|---|
| Java | JDK 21 |
| Gradle Wrapper | 9.9 |
| Android Gradle Plugin | 9.5.1 |
| Kotlin | 2.3.21 |
| Android SDK | compileSdk 37 |
| NDK | 26.1.10909125 |
| CMake | 3.22.1 |
| ABI nativa | arm64-v8a |
| Testes | JUnit 4.13.2 |

As versões acima refletem a configuração existente no módulo, que foi preservada nesta fase.

## Inicialização e responsabilidades

- `LabForgeApp` inicializa o runtime do mundo, o agendador de recuperação e a ponte nativa.
- `GirassolService` mantém operações longas visíveis ao usuário por meio de notificação e adquire um `PARTIAL_WAKE_LOCK` limitado a seis horas.
- `WorldWorkScheduler` agenda recuperação periódica best-effort com WorkManager.
- `CMakeLists.txt` compila a ponte JNI e vincula a biblioteca Vulkan; a disponibilidade de recursos Vulkan é verificada em runtime.
- Vulkan e OpenGL ES são declarados como capabilities opcionais no Manifest. A ausência de Vulkan não deve impedir a instalação nem o caminho de fallback gráfico.

## Pipeline de integração contínua

O workflow `.github/workflows/android_build.yml` executa em push de branches, pull requests para `main` e execução manual. Instala JDK 21, Android SDK 37, NDK e CMake; executa `assembleDebug` e `testDebugUnitTest`; publica o APK debug e o relatório JUnit como artifacts.

## Limites operacionais

O Foreground Service mantém tarefas compatíveis com as políticas Android em execução visível, mas não constitui garantia de execução ininterrupta 24/7. WorkManager é um mecanismo de execução adiada e recuperação, não um loop de tempo real. A Fase 1 estabelece a fundação e o pipeline; os motores gráficos, eventos, cognição avançada, evolução de pesos e UI final pertencem às fases seguintes.
