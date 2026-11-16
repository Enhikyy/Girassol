# Integração cognitiva e resiliência do MUNDO

## Camadas
```text
WorldScreen -> WorldRuntime -> WorldEngine.step()
                               |
                         CognitiveEngine
                         /            \
                  System 1          System 2
                Kotlin heuristics   async reasoner contract
                         \            /
                       Kahneman Control Loop
                                 |
                             SoulProfile
WorldRuntime -> WorldCognitionCoordinator -> SeveranceMemoryManager
                                            INNIE / OUTIE partitions
                                            episodic / semantic / soul
WorldSnapshotStore <- WorldCatchUpWorker <- WorkManager

WorldRenderer (OpenGL ES 3 atual)
        |
native physical-device capability probe
        |
VK_KHR_ray_query available? -> future Vulkan renderer
        +-------------------> otherwise OpenGL ES fallback
```

## Implementado
- A seleção de ações do mundo passa pelo System 1 Kotlin, com nove eixos ternários e espaço de 19.683 estados.
- O Kahneman Control Loop só cristaliza política após três observações e taxa de sucesso mínima de 75%.
- System 2 tem interface assíncrona com timeout e só escala quando a confiança é baixa e um reasoner é fornecido; o tick do mundo nunca aguarda um LLM.
- SoulProfile tem valores persistíveis e atualização limitada por experiência.
- SeveranceMemoryManager classifica localização real: sede, escritório, universidade, laboratório e data center ativam innie; outros contextos ativam outie. Estresse >=82 força outie mesmo no trabalho.
- A busca filtra a persona antes da classificação. Embeddings são opcionais; sem provedor local, usa sobreposição lexical.
- Atividades concluídas entram na memória uma única vez por source key, com importância emocional e esquecimento seletivo.
- WorkManager agenda catch-up periódico best-effort a partir do snapshot após morte do processo.

## Vulkan
O probe nativo consulta extensões e feature bits reais do dispositivo para ray query e acceleration structures. A versão Vulkan isolada não é suficiente. O renderer existente permanece OpenGL ES 3; a implementação do renderer Vulkan, sincronização e gestão de recursos ainda precisa ser feita e validada antes de selecionar o caminho Vulkan. ARM NSS, FSR e Mobile NeRF não são habilitados sem integração compatível e benchmarks.

## Execução Android
WorkManager é best-effort e sujeito a Doze, bateria e limites OEM; não garante execução ininterrupta 24/7. O ForegroundService atual permanece dedicado aos jobs visíveis de modelo. Uma sessão de mundo sempre ativa exige opt-in explícito e cumprimento das regras de foreground service da versão Android.

## Referências externas
Os repositórios BTCU, coreason-cortex, DPT-Agent, Intern e iamjosephmj/bridge não foram resolvidos como fontes públicas verificáveis a partir dos nomes fornecidos. Os padrões foram reimplementados nativamente sem copiar código nem assumir benchmarks não confirmados. O backend GGUF/llama.cpp existente não é substituído por MediaPipe/Gemma. O padrão de memória hierárquica é portado em Kotlin, sem embutir um servidor Python.
