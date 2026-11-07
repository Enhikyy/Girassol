# Segurança

## Escopo

O Girassol é um app local para dispositivo controlado. Ele inclui shell, arquivos e Git por design; essas ferramentas são intencionais e ficam no sandbox do app.

## Dados

- Sem telemetria.
- Chaves de API e endpoint ficam em armazenamento local.
- Dossiês, datasets e adapters ficam em `files/` do app.
- A pesquisa envia apenas o tema para APIs públicas.

## Rede

- APIs gratuitas: Wikipedia, OpenAlex, arXiv, Stack Overflow, DuckDuckGo, Semantic Scholar.
- LLM: endpoint configurado pelo usuário.
- Nenhum backend do Girassol.

## Reportar

Abra uma issue privada/security advisory no GitHub com:

- versão;
- dispositivo/Android;
- passos;
- impacto.

Não publique chaves, tokens ou dados pessoais.
