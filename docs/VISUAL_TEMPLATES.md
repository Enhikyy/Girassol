<!-- coderabbit-ai-generated:v1 -->

# Modelos visuais implementados

Foram baixados modelos abertos do [Android Compose Samples](https://github.com/android/compose-samples), semelhantes aos grupos de chat, dashboard e cartões enviados no Figma. Os originais estão em `design-references/compose-samples/`: **Jetchat Conversation**, **Jetchat UserInput** e **Reply ListContent**, com a licença Apache2 original. Não são reproduções dos arquivos Figma bloqueados. O novo layout adapta seus padrões à identidade do Girassol.

Também foi baixado o [ícone auto_awesome do Google Material Icons](https://github.com/google/material-design-icons), convertido para `ic_studio_spark.xml` e usado na página inicial e na abertura do chat. O SVG original permanece em `design-references/auto_awesome.svg`. <!-- coderabbit-ai-generated:v1 -->

<!-- coderabbit-ai-generated-section:v1:9276a08d5c3c95e27ad0f49e8a0379ac39c579297e4adef85183971d222309bf -->

As licenças Apache2 originais encontram-se em `design-references/compose-samples/LICENSE` e `design-references/MaterialIcons-LICENSE`; a licença do ícone também é incluída no APK em `assets/third_party/MaterialIcons-LICENSE.txt`. A atribuição dos assets e dos templates consta no manifesto `design-references/sources.json`.

<!-- /coderabbit-ai-generated:v1 -->

## Aplicação nativa

| Referências enviadas | Modelo aplicado ao Girassol |
|---|---|
| Prompverse, DataVista, landing pages de IA | Área de destaque em gradiente, tipografia maior, girassol animado e chamadas para chat/pesquisa na tela inicial |
| Cards, dashboard e bibliotecas de ícones | Grade adaptável de quatro atalhos: conversa, código, pesquisa e compilação; cartões e métricas reais existentes preservados abaixo |
| Chatbot UI kits, Agentra, design systems | Tela inicial de conversa com cartões de sugestões e compositor Material, botão de enviar/parar rotulado e desabilitação de envio vazio |
| Blocos de atividade dos agentes | Cartões expansíveis de análise e ferramentas, com resultado, argumentos, falha/espera expressos em texto e conteúdo selecionável |
| Sunflower animation | Animação Compose do girassol já existente integrada ao novo destaque, sem download de um player de animação |

Os atalhos usam as rotas reais: Chat, Codex, Estudo (aba3) e Compilar (aba5). Sugestões preenchem a mensagem; só o usuário inicia a execução. Enviar, interromper e usar o teclado continuam ligados ao AgentViewModel existente. Falhas e resultados vêm das mensagens reais; não foram adicionados gráficos com números fictícios, microfone sem implementação ou estados de sucesso decorativos.

A grade usa quatro colunas em largura disponível de840dp, duas a partir de340dp e uma em tamanhos menores. Com fonte de1,4x ou maior, os cartões ficam em uma coluna. Textos e ações podem crescer; botões da área de destaque usam FlowRow. O bloco de atividade mantém todo o texto disponível ao expandir, com rolagem no resultado.

## Origem e limites

O manifesto guarda URLs e SHA256 de cada download. As URLs main foram acessíveis, mas a tentativa de fixar o download por SHA retornou404; por isso a identidade reprodutível dos arquivos é seu hash de conteúdo. Os arquivos originais ficam fora de `app/src` e não são compilados; os componentes Kotlin nativos implementam o layout. Não foi acrescentada dependência de Figma, WebView, Flutter engine ou biblioteca de UI externa.

Jetchat e Reply têm autoria do Android Open Source Project, sob Apache License2.0. O ícone Material é do projeto Google Material Design Icons, sob Apache License2.0. Os cabeçalhos originais foram preservados nos downloads; a conversão SVG→VectorDrawable é indicada no próprio XML. O código original do Girassol continua sob sua licença existente, com essas referências/asset sujeitos à licença indicada.

As capturas de validação usam as telas e componentes Compose reais em RobolectricAPI33, com fixtures locais. Não comprovam execução no aparelho ARM64. Os testes cobrem os callbacks, envio/parada, expansão e as adaptações de largura/fonte.
