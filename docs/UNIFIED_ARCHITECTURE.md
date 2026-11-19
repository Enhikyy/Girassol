# Girassol — Arquitetura Unificada (linha de integração)

Status: **fundação de integração criada; migração funcional ainda em andamento**.

## Decisões de composição

- Nova identidade de instalação: `com.girassol.ecosystem`.
- Namespace de código mantido temporariamente em `com.enhikyy.girassol` para evitar quebrar as referências `R` e classes geradas antes da migração de pacotes.
- Um único aplicativo Android, um único processo de autoridade para o estado do mundo e uma única composição de subsistemas.
- A simulação não deve ser reimplementada em uma tela: Mundo, agentes, escola, hospital, memória, evolução e pesquisa são projeções/serviços sobre o mesmo estado.
- O renderer recebe snapshots imutáveis; nunca altera regras, memória ou persistência.
- O motor neural deve ser único. Não se deve empacotar dois runtimes llama.cpp com bibliotecas JNI/SONAME conflitantes.

## Mapa de subsistemas

| Subsistema | Responsabilidade | Fonte principal |
|---|---|---|
| WorldCore | ticks, comandos, eventos canônicos, replay, checkpoints, terreno e reservas | Fase 20 + Arquitetura Mestre |
| AgentCore / CognitiveCore | agentes, drives, Utility AI, inferência ativa, deliberação | Fase 20 + GEA |
| MemoryCore | memória episódica/semântica, consolidação, crenças e imunidade epistêmica | Fase 20 + RSI |
| EvolutionCore | genoma, especiação, avaliação e promoção delimitada | Fase 20 + RSI |
| LearningCore | escola, datasets, domínio, retenção e habilidades | Fase 20 + GEA |
| SocialCore | relações, confiança, cultura e instituições | Fase 20 + GEA |
| EconomyCore | recursos, produção, reservas e conservação | GEA |
| AIEngine | GGUF, inferência local, ferramentas, Codex e LoRA | runtime nativo existente |
| ResearchCore | APIs, ingestão, corpus e pesquisa | runtime existente |
| WorldRenderer | viewport 3D/2D e assets visuais | Fase 20 |

## Contratos obrigatórios

1. Comandos de agentes, LLM, UI e instituições entram pelo escritor único do WorldCore.
2. Escritas persistentes são transacionais e versionadas.
3. Eventos têm ordenação canônica, identidade estável e validação de cadeia.
4. Subsistemas declaram dependências e ciclo de vida; a inicialização é topológica e a parada é reversa.
5. Toda hipótese experimental (H1–H6) fica atrás de feature flag, métricas, shadow replay e critérios de promoção/rejeição.
6. Falha do LLM não pode interromper o tick determinístico; deve haver fallback não neural.
7. Dados de UI são snapshots; nenhuma tela mantém uma cópia autoritativa da simulação.

## Etapas de migração

### Etapa A — fundação
- Nova branch isolada.
- Nova applicationId.
- CI independente para build, testes e lint.
- Coordenador de ciclo de vida com dependências explícitas e testes.

### Etapa B — núcleo canônico
- Migrar WorldActor, CanonicalEvent, WorldCheckpoint, WorldPersistence e SpatialStigmergy.
- Ligar GameLoop e SimulationService ao mesmo WorldActor.
- Remover caminhos de escrita paralelos somente após equivalência de testes.

### Etapa C — motores e memória
- Consolidar inferência GGUF/llama.cpp e Llamatik como camada de agente/ferramentas.
- Conectar memória, pesquisa, escola, evolução e hospital ao mesmo event log.
- Não promover código gerado ou parâmetros RSI sem shadow replay e aprovação do gate.

### Etapa D — interface e renderer
- Uma única tela Mundo ligada a snapshots do WorldCore.
- Carregamento real de GLB/Filament validado; catálogo de assets não é prova de renderização.
- Fallback Compose mantido para aparelhos sem suporte ou sob pressão de memória.

### Etapa E — validação
- Unit tests, lint, assembleDebug, testes instrumentados/emulador e teste físico.
- Verificar instalação limpa com a nova applicationId.
- Verificar retomada/replay, pausa, serviço em primeiro plano, memória e inferência.
- Publicar APK somente após CI verde; não declarar integração completa antes dos testes.

## Limitações conhecidas nesta fundação

A inclusão de assets GLB no pacote de origem não prova que eles são carregados no renderer. O pacote CodeAssist registra explicitamente que o viewport atual usa OpenGL ES 2.0/Compose fallback e que a sonda Filament não carrega GLB. O carregador e o ciclo de vida do renderer continuam uma tarefa de implementação/validação.

A documentação GEA e RSI descreve mecanismos propostos e hipóteses; H1–H6 são hipóteses testáveis, não capacidades empiricamente comprovadas.
