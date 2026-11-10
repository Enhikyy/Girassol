# Componentes e fontes de terceiros

O projeto não incorpora assets visuais da implementação anterior nem cópias de designs do Figma.

## Dependências de execução

- AndroidX, Jetpack Compose e Material — Android Open Source Project / Google.
- Kotlin e corrotinas — JetBrains.
- OkHttp — Square.
- WorkManager e DataStore — AndroidX.
- Llamatik 0.12.0 e llama.cpp — inferência GGUF local. As licenças completas acompanham seus artefatos e repositórios oficiais.
- Apache Commons Compress — leitura segura do bootstrap ZIP.

## Toolchain instalado sob ação do usuário

- Termux bootstrap: https://github.com/termux/termux-packages
- Android command-line tools: https://developer.android.com/studio#command-line-tools-only
- Referência AAPT2 ARM64: https://github.com/poordevcode/termux-android-studio (Apache-2.0), commit `76652ef44d86baa1c42957da5e7e5bbaaf95956d`.
- OpenJDK, Gradle e demais pacotes são obtidos pelos canais configurados no toolchain e mantêm suas licenças próprias.

## Fontes de pesquisa

O app consulta as APIs públicas da Wikipédia e do Crossref em tempo de execução. Cada nota guarda sua URL; os textos pertencem aos respectivos autores e licenças das fontes.
