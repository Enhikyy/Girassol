# Referências de UI — Girassol 1.8.0

As referências da Figma Community foram fornecidas como inspiração visual. Os links comunitários não expuseram ao conector um arquivo editável com chave de projeto/camadas; por isso, nenhum arquivo, vetor ou componente foi copiado diretamente. Os conceitos foram traduzidos em componentes Jetpack Compose originais, preservando a marca Girassol, Material 3 e o modo Pure Black OLED.

## Referências indicadas

| Referência | Conceito aproveitado no Girassol |
|---|---|
| [Prompverse AI landing page](https://www.figma.com/community/file/1385497115608726899/prompverse-ai-ai-landing-page-modern-landing-page-minimal-landing-page) | Hero compacto, mensagem principal clara, foco em uma ação prioritária e superfícies escuras com acentos luminosos moderados. |
| [AI Icons / AI Art Generation](https://www.figma.com/community/file/1257662273421934431/ai-icons-ai-art-generation-icon-set) | Linguagem de ícones consistente; no app usamos ícones Material Rounded em vez de redistribuir o conjunto de terceiros. |
| [3 Free AI Chatbot App UI Kit](https://www.figma.com/community/file/1393822153168458311/3-free-ai-chatbot-app-ui-kit) | Hierarquia conversacional, estados de agente, cartões de mensagens, ações contextuais e áreas de interação com alvos táteis confortáveis. |
| [Sunflower Animation](https://www.figma.com/community/file/1668810584094593349/sunflower-animation-figma-motion) | Movimento radial de pétalas e pulsação orgânica. Implementado por Canvas/Compose com 24 pétalas e animação leve. |
| [DataVista dashboard](https://www.figma.com/community/file/1420754870674483529/datavista-landing-page-template-for-figma-and-framer) | Cartões de métricas, hierarquia de dados, gráficos de linha/barras e leitura rápida de indicadores. |

## Tradução para componentes nativos

- **Hero de pesquisa:** AutodidactPane usa a flor radial animada, título prioritário, resumo de capacidades e formulário de tema.
- **Fluxo autônomo:** pesquisa, ingestão, síntese, currículo, dataset, ajuste e consolidação aparecem como etapas explícitas com progresso.
- **Árvore de competências:** nós com pré-requisitos, domínio estimado, revisões espaçadas e ações de recordação.
- **Memória de repouso:** cartões de consolidação e revisão de falhas com confiança e quantidade de evidências.
- **Analytics:** donut de fontes, barras de ingestão, curva de tokens e indicadores de ciclos.
- **OLED:** superfícies pretas e redução de efeitos de fundo; o conteúdo, os gráficos e as animações funcionais continuam legíveis.

## Restrições de implementação

O desenho é Compose nativo, sem WebView, React Native ou runtime Python adicional. Os efeitos são limitados a Canvas/Compose e usam animações curtas. O modo Pure Black desliga os brilhos de fundo e evita superfícies translúcidas caras. As áreas de toque mantêm espaçamento e altura adequados para uso em telefone.

A interface é uma interpretação original dos conceitos, não uma réplica pixel-perfect dos arquivos Figma que não puderam ser importados.
