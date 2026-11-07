# Publicar o repositório

O checkout local já está commitado em `main` com o histórico inicial. Falta apenas o remoto.

## GitHub

O conector GitHub do ambiente está autenticado como `Enhikyy`, mas o app instalado tem permissão de **conteúdo/PR**, não de **criação de repositório**. Crie o repositório vazio uma vez:

1. abra <https://github.com/new?name=girassol&visibility=public>;
2. **não** marque README, .gitignore ou licença;
3. clique em **Create repository**.

Depois, no sandbox:

```bash
cd /home/user/.workspace/girassol-repo
connector-cli github setup-git
git remote add origin https://github.com/Enhikyy/girassol.git
git push -u origin main
```

O push dispara `.github/workflows/android.yml`, que roda os testes, gera o APK e o publica como artifact `Girassol-APK`. Para uma release:

```bash
git tag v1.3.1
git push origin v1.3.1
```

## Figma

O ambiente não tem um conector Figma ativo. Há duas formas de criar o arquivo:

### Opção A — plugin (sem token)

1. abra um arquivo novo no Figma;
2. **Plugins → Development → Import plugin from manifest…**;
3. selecione `design/plugin/manifest.json`;
4. rode **Girassol Design System**.

O plugin cria capa, design system, 8 telas e fluxo em camadas nativas.

### Opção B — API

Conecte um MCP Figma ou forneça um token com `file_content:write` e o file key de destino. O agente então cria as páginas diretamente.

## Verificação

- GitHub Actions: **Actions → Android APK**;
- APK: artifact `Girassol-APK` ou release `v1.3.1`;
- Figma: 4 páginas (`00 · Capa`, `01 · Design System`, `02 · Telas`, `03 · Fluxo`).
