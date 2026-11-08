# Release e CI

## GitHub Actions

O workflow [`android.yml`](../.github/workflows/android.yml) roda em:

- push para `main`;
- tags `v*`;
- `workflow_dispatch` manual.

### O que ele faz

1. checkout;
2. JDK 21 (Temurin);
3. Android SDK + `platforms;android-37.0` + `build-tools;37.0.0`;
4. `./gradlew :app:testDebugUnitTest`;
5. `./gradlew :app:assembleDebug`;
6. artifact **`Girassol-APK`**;
7. em tag, release com o APK anexado.

### Baixar

- **Actions** → último run → artifact `Girassol-APK`;
- ou **Releases** → tag `v1.4.0` → `Girassol-1.4.0-*.apk`.

### Criar uma release

```bash
git tag v1.4.0
git push origin v1.4.0
```

O workflow cria a release e anexa o APK.

## Assinatura

O APK de CI é assinado com a **debug keystore** (`signingConfig = debug`), suficiente para testes e sideload. Para publicar:

1. gere uma keystore;
2. adicione `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` nos secrets;
3. troque `signingConfig` para release no `app/build.gradle.kts`;
4. rode `assembleRelease`.

## Tamanho

O APK atual tem ~36 MB (arm64-v8a, llama.cpp + Compose). Limite de 300 MB do projeto.

## Cache

`gradle/actions/setup-gradle` cacheia dependências e wrapper. O build leva ~3–5 min no runner.
