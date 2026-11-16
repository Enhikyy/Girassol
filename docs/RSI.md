# Girassol RSI — primeira etapa

## Escopo entregue nesta etapa

O RSI começa pela evolução mensurável do **harness**, não pela alteração automática dos pesos neurais. O motor registra execuções reais do agente, compara perfis de execução e mantém um histórico local limitado a 500 turnos.

Perfis iniciais:
- **Base**: equilíbrio entre passos, memória recuperada e custo.
- **Eficiente**: reduz passos máximos e quantidade de memórias recuperadas.
- **Deliberativo**: amplia passos e recuperação de memória para tarefas longas.

Cada turno registra sucesso operacional (resposta final sem falha fatal), chamadas/falhas de ferramentas, tokens e latência. Essa métrica não prova correção factual; mede apenas o resultado operacional observado.

## Critério de comparação

A recomendação exige pelo menos cinco execuções no perfil Base e cinco no candidato. O candidato precisa manter a taxa de sucesso dentro de 2 pontos percentuais do baseline, não exceder 15% em tokens médios, 20% em latência média e 5 pontos percentuais na taxa de falha de ferramentas.

A escolha é sempre explícita. Nenhum perfil é promovido automaticamente. O perfil ativo pode ser revertido para Base a qualquer momento.

## Limites deliberados

- Não modifica arquivos de código, prompts arbitrários ou pesos GGUF.
- Não executa treinamento em segundo plano.
- Não trata avaliação do próprio modelo como verdade; sucesso operacional é apenas um sinal.
- Não incorpora código de HarnessPro, LM2LM, Sapience ou outros projetos. Implementa conceitos gerais em Kotlin, mantendo suas licenças e dependências fora do APK.
- Os dados ficam no armazenamento privado do aplicativo.

## Próximas fases

1. Adicionar avaliações de tarefa com testes de resultado e conjunto held-out.
2. Introduzir geração de propostas de harness com diff revisável e rollback.
3. Integrar memória episódica à seleção de experiências com evidência rastreável.
4. Criar pipeline externo para SFT/DPO/LoRA, com benchmark antes/depois e promoção versionada do GGUF.
5. Avaliar LM2LM/RWM somente com orçamento externo de GPU e experimentos replicáveis.


## Segunda etapa — laboratório nativo no Android

O módulo `RsiResearchLab` adiciona persistência local para:
- **Skills versionadas:** candidatas em texto, validação estrutural, aprovação explícita e rollback para a skill aprovada anteriormente. Somente a skill aprovada é anexada ao prompt ReAct; o prompt-base, o caderno e as regras de segurança permanecem preservados.
- **Replay:** registra hash SHA-256 da tarefa, perfil, sucesso operacional, latência, tokens e uso/falha de ferramentas. O texto original da tarefa não é copiado para o ledger RSI.
- **Gate held-out:** registra pontuações baseline/candidato, amostras e amostras privadas de validação. Uma comparação só fica elegível para revisão quando tem pelo menos cinco amostras, três amostras holdout e ganho sobre o baseline. Isso não executa nem falsifica benchmarks: os resultados precisam vir de avaliações realmente executadas.
- **Painel nativo:** a tela Autonomia permite criar skills, validar/aprovar, reverter, consultar replay e registrar resultados de experimentos.

### Limites de implementação

Esta etapa implementa nativamente no Kotlin os mecanismos de harness/skills/replay/controle de promoção. Ela **não** incorpora os pesos nem reproduz as redes neurais de Mythic-RDT, Modus_X ou Lunaris. Essas arquiteturas exigem implementações de camadas/tensores, pesos treinados compatíveis, conversão e backend de inferência específico; um controlador de loops no app não seria uma reprodução fiel. MASS/treinamento LoRA também não é executado no A22: o app não inicia treino pesado nem altera pesos automaticamente.

Nenhuma skill executável, código-fonte do próprio app ou arquivo GGUF é autoalterado. A validação estrutural de skills é um gate de segurança, não um avaliador semântico de qualidade. A avaliação de qualidade exige tarefas congeladas, holdout realmente separado e resultados reproduzíveis.
