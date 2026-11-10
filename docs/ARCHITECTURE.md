# Arquitetura

## Grafo de dependências

`GirassolApplication` cria um `AppContainer` por processo. O container reúne repositórios, motores e o `ActivityHub`. `MainActivity` instala a UI Compose; `AppViewModel` coordena corrotinas e estados observáveis. O `SleepWorker` usa o mesmo container quando o WorkManager executa uma consolidação em segundo plano.

## Estado real compartilhado

Produtores chamam `begin`, `progress`, `complete` ou `fail` no `ActivityHub`. Cada `ActivityKind` aponta para uma zona do Mundo. O renderizador lê o snapshot mais recente a cada frame e move o agente até a zona correspondente. O progresso exibido em Compose é exatamente o publicado pelo produtor.

Operações não são persistidas como se estivessem ativas após uma morte de processo. Apenas eventos finais são recarregados; isso evita mostrar um build ou treino antigo como se ainda estivesse executando.

## IA local

`ModelStore` importa GGUF pelo Storage Access Framework, valida o cabeçalho e limita a cópia pelo espaço disponível. `LocalLlmEngine` serializa inferências com um mutex, carrega/descarrega modelos por Llamatik e transmite deltas. O histórico é persistido somente no fim da resposta; os deltas ficam em memória para evitar escrita por token.

## Pesquisa e aprendizado

`ResearchEngine` consulta Wikipédia e Crossref por OkHttp com respostas limitadas a 4 MiB. As notas mantêm URL e origem. O dataset é JSONL gerado das notas, não um registro de treino de pesos.

`MemoryEngine` classifica eventos finais por tipo, sucesso e falha. O resultado é inspecionável e determinístico. `SleepScheduler` usa trabalho periódico de 12 horas com carregamento, bateria não baixa e dispositivo ocioso.

## Studio

`ProjectRepository` cria e mantém projetos dentro de `files/workspace`. `CodeAnalyzer` implementa regras locais; somente espaços finais têm correção automática. `ToolchainManager` mantém processos e logs, executa o build real e publica falhas no barramento. O Codex usa inferência local e um estado `CodexProposal`; não há escrita automática no workspace.

## Mundo nativo

`WorldView` é um `GLSurfaceView` OpenGL ES 3.0. `WorldRenderer` gera meshes de cubos e cilindros por função, usa shaders próprios, iluminação direcional, profundidade e fog. A cena é composta por seis zonas funcionais. Não há WebView, motor web ou serviço de telemetria.

## Armazenamento

- `files/models`: GGUF importados; excluído de backup.
- `files/toolchain`: JDK/Gradle/SDK privados; excluído de backup.
- `files/workspace`: projetos Android.
- `files/research`: notas e datasets.
- `files/state`: chat, histórico e memória.
- DataStore: aparência, modelo ativo e automação.
