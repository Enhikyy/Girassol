<!-- coderabbit-ai-generated:v1 -->

# Central de compilação e UI nativa

Abra **Laboratório → Compilar**. Informe um projeto Android já existente no Termux, o módulo (`:app` ou `:mobile:app`) e Debug/Release. A opção offline usa somente dependências já disponíveis. A compilação usa o wrapper Gradle do projeto, com dois workers, console simples e stacktrace. Não substitui o compilador Kotlin/Java nem embute o SDK no APK do Girassol.

## Pipeline reforçado

1. Diagnóstico de wrapper, JDK, SDK/platforms, aapt2 ARM64, unzip, sha256sum e apksigner.
2. Testes unitários da variante.
3. Lint da variante, com inspeção do XML: Error/Fatal interrompem o pipeline mesmo quando `abortOnError=false`.
4. Montagem da variante selecionada.
5. Verificação de integridade ZIP e assinatura com apksigner para cada APK da pasta da variante. Caminho, tamanho e SHA-256 são retornados como metadados; sem metadados válidos o pipeline falha.

Esse pipeline completo é uma nova opção. As ações individuais existentes continuam disponíveis; o XML torna o fluxo completo mais exigente do que executar somente a ação lint. Não são desativadas regras nem reescritas configurações do projeto. Um APK Release sem assinatura não passa na verificação; configure a assinatura no próprio projeto.

O agente pode chamar `android_build` com `action=pipeline`, `project`, `module`, `variant=debug|release` e `offline`. Painel e agente compartilham uma execução por vez. O relatório mais recente fica em `files/labforge/runs/android-build.json`, com estado, saída, exit code e duração por etapa. A saída é recebida ao terminar cada comando, não em streaming, e o relatório guarda os últimos 80 mil caracteres de cada etapa.

Falhas interrompem as etapas seguintes. A interface oferece o log selecionável, sugestões de correção e reexecução completa após corrigir o problema. Não faz retry cego nem apaga caches/projetos automaticamente. Interromper a espera ou fechar o processo do Girassol não garante encerrar a compilação externa; confira o Termux antes de repetir. Uma etapa que estava em execução ao restaurar o relatório aparece como interrompida.

## Ambiente sem root

Termux é instalado separadamente. Habilite `allow-external-apps=true` em `~/.termux/termux.properties` e conceda RUN_COMMAND ao Girassol. Configure JDK/SDK compatíveis com o projeto, `ANDROID_HOME` (ou `ANDROID_SDK_ROOT`), `aapt2` ARM64 em `$PREFIX/bin`, `unzip`, `sha256sum` e `apksigner` no PATH do Termux. O projeto usa o UID e os arquivos privados do Termux. Arquivos do Girassol não ficam automaticamente disponíveis lá. Instalação de APK continua pelo instalador Android, com confirmação do sistema.

O caminho de saída segue a convenção Android Gradle Plugin `<módulo>/build/outputs/apk/<debug|release>`. Esta versão suporta projetos sem product flavors e com o diretório do módulo correspondendo ao caminho Gradle. Flavors, diretórios remapeados, compilação Dart/Flutter e provisionamento automático do SDK ainda não fazem parte do pipeline. Não há alegação de SDK Flutter oficialmente suportado dentro do Android/Termux.

## UI/UX pesquisada no Flutter e implementada em Compose

A interface nativa usa Material 3 e o tema do Girassol. As [referências oficiais de widgets Material](https://docs.flutter.dev/ui/widgets/material) demonstram que o visual pode ser construído por componentes em código. Foram aplicados cartões, botões e chips com estados, campos rotulados, progresso indeterminado durante uma etapa real e resultados que combinam ícone e texto.

Seguindo os princípios de [design adaptável](https://docs.flutter.dev/ui/adaptive-responsive), o painel usa uma coluna no telefone e duas a partir de 840 dp de largura disponível. As abas do laboratório agora rolam horizontalmente para evitar apertar seis rótulos na largura do telefone. A lista de etapas mostra exatamente os resultados recebidos, sem percentuais inventados.

As orientações de [acessibilidade do Flutter](https://docs.flutter.dev/ui/accessibility-and-internationalization/accessibility) embasaram alvos de pelo menos 48 dp, rótulos, estados expressos por texto, avisos sem depender somente de cor e anúncios de status por semântica. O motor visual continua sendo Compose; não foi importado um runtime Flutter nem copiados arquivos Figma.

Também foram consultadas a [arquitetura do Flutter](https://docs.flutter.dev/resources/architectural-overview), [instalação do SDK](https://docs.flutter.dev/install/manual) e [publicação Android](https://docs.flutter.dev/deployment/android). Seis páginas oficiais retornaram HTTP200 nesta pesquisa. A existência de suporte para publicar apps Flutter no Android não comprova suporte para executar todo o SDK de desenvolvimento dentro de um telefone.

## Validação e limites

Testes JVM/Compose verificam parada por falha, cancelamento, timeout, metadados, persistência e controles da interface. Scripts bash gerados também são exercitados no host; isso não substitui um build dentro do Termux no telefone. A execução real por RUN_COMMAND e a inspeção visual no dispositivo ARM64 continuam dependendo de aparelho conectado. O app Android não possui uma versão web para prévia em Chromium.
