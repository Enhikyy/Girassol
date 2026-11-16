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
