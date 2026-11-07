# Ativar o workflow no GitHub

O workflow está versionado em [`ci/android.yml`](../ci/android.yml). Ele não pôde ser enviado diretamente para `.github/workflows/android.yml` porque o GitHub App usado pelo agente tem permissão de **conteúdo**, mas não de **workflows**.

## Ativar em 30 segundos

1. Abra <https://github.com/Enhikyy/Girassol/new/main?filename=.github%2Fworkflows%2Fandroid.yml>.
2. Cole o conteúdo de [`ci/android.yml`](../ci/android.yml).
3. Clique em **Commit changes**.

O commit em `main` dispara o workflow. Ele:

- roda os testes;
- gera o APK;
- publica o artifact **`Girassol-APK`**;
- em tags `v*`, cria uma release com o APK.

## Alternativa

Se o app conector receber a permissão **Workflows: write**, o arquivo pode ser enviado direto para `.github/workflows/android.yml` e o workflow disparado sem passo manual.
