# Ponytail no Girassol

O Girassol aplica ao Codex interno uma adaptação da skill MIT de DietrichGebert/ponytail:
https://github.com/DietrichGebert/ponytail

A política está em `CodeConcisionPolicy.kt` e orienta o agente a:
- inspecionar callers, testes e configurações antes de editar;
- reutilizar código e recursos existentes;
- entregar o menor diff completo, sem abstrações prematuras;
- preservar segurança, validação, acessibilidade e tratamento de erros;
- deixar testes para lógica não trivial.

Os percentuais publicados pelo projeto Ponytail são resultados reportados pelos próprios autores em tarefas/modelo/harness específicos; não são promessa de redução igual no Girassol. O app deverá medir tokens, tempo, tamanho do diff e taxa de testes aprovados em tarefas pareadas antes de afirmar economia própria.

Licença da referência: MIT. Esta é uma adaptação em texto, sem copiar runtime externo ou dependências.
