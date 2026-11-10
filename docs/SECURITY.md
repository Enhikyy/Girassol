# Segurança

## Fronteiras

- Nenhum fluxo solicita root ou modifica SELinux.
- O app não executa scripts descobertos em páginas, modelos ou READMEs.
- Projetos e arquivos passam por canonicalização; caminhos absolutos, NUL e traversal são rejeitados.
- Imports ZIP têm limite de 2.000 entradas, 16 MiB por arquivo e 256 MiB descompactados no total.
- Downloads de bootstrap, command-line tools e AAPT2 têm SHA-256 fixado.
- APKs são abertos por `FileProvider`; a instalação depende das confirmações do Android.

## Dados

Chat, notas, memória e projetos ficam no sandbox privado. Nenhum provedor remoto de IA é chamado nesta revisão. Pesquisa envia apenas o tema digitado às fontes públicas selecionadas. Modelos e toolchain são excluídos de backup por seu tamanho e natureza reproduzível.

## Revisão necessária antes de produção

O toolchain executa pacotes nativos de terceiros e deve passar por testes em aparelhos representativos. O diagnóstico de segredos é heurístico e não substitui uma revisão de segurança. Propostas do Codex são não confiáveis até revisão humana; por isso começam como texto no editor, não como código aplicado.
