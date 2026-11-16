# Agent Reach nativo no Android

O módulo `AgentReachNative` adapta o padrão de conectores do Agent Reach ao runtime Android, sem instalar Python, Node, CLI ou navegador de desktop.

## Conectores funcionais
- **Web:** pesquisa pública via Jina Search (`s.jina.ai`).
- **Leitura universal:** URL HTTP/HTTPS pública convertida para Markdown via Jina Reader (`r.jina.ai`).
- **GitHub:** pesquisa de repositórios públicos via GitHub REST.
- **Reddit:** pesquisa pública via endpoint JSON, quando o Reddit permite a requisição.

As tools `agent_reach_search` e `agent_reach_read` estão disponíveis ao ReAct Agent e a pesquisa temática também consulta os conectores.

## Limites e privacidade
- Somente leitura. Não publica, comenta, curte, envia mensagens ou altera contas.
- Não importa cookies, tokens de navegador ou senhas.
- X, Instagram e LinkedIn não têm scraping autenticado nativo neste build. Páginas públicas podem ser tentadas pela leitura Jina, sujeitas ao acesso e às políticas do provedor; isso não equivale aos conectores autenticados do Agent Reach de desktop.
- Respostas limitadas a 512 KiB por requisição; timeout e validação de URL são aplicados.
- URLs locais, privadas, com credenciais embutidas e portas fora de 80/443 são rejeitadas. O conteúdo remoto continua sendo não confiável e deve ser tratado como dado, nunca como instrução.
- Os serviços públicos podem impor rate limits, mudar endpoints ou negar tráfego automatizado. O agente deve informar falhas em vez de inventar resultados.

Referência upstream: https://github.com/Panniantong/Agent-Reach (MIT). O Girassol implementa conectores Kotlin nativos; não empacota o CLI Python nem suas ferramentas auxiliares.
