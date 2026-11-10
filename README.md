# Girassol

Girassol é um laboratório Android local-first para conversar com modelos GGUF, pesquisar fontes públicas, preparar datasets, consolidar memória operacional e criar aplicativos Android em um Studio integrado. Esta árvore é uma reconstrução completa: não reutiliza a interface, o código ou os assets da implementação anterior.

## Superfícies

- **Início**: saúde do laboratório, operações ativas e histórico real.
- **Mundo**: campus 3D nativo em OpenGL ES 3.0, com câmera isométrica, pan, zoom e foco por área. O agente se desloca conforme o barramento de atividades.
- **Chat**: inferência local com streaming e cancelamento via Llamatik/llama.cpp. Exige importar um GGUF; não há resposta simulada.
- **Estudo**: pesquisa real na Wikipédia e no Crossref, notas com origem, datasets JSONL e consolidação auditável.
- **Studio**: projetos Compose ou Views, editor, importação/exportação ZIP, Codex com proposta revisável, diagnóstico e build Gradle local.

## Mapa do Mundo

| Operação | Zona |
| --- | --- |
| Coordenação e chat | Átrio |
| Autoestudo e pesquisa | Biblioteca |
| Preparação de especialização e futuro treino validado | Faculdade |
| Sono profundo e consolidação | Casa |
| Diagnóstico, falhas e correções | Consultório |
| Codex, projetos e compilação | Studio |

O renderizador consome `ActivityHub`; ele não inventa progresso nem transforma animações em evidência de execução. Eventos concluídos recentemente também orientam uma visita curta à zona correspondente.

## Requisitos

- Android 8.0 (API 26) ou superior.
- OpenGL ES 3.0.
- Para o build Android **no aparelho**: ARM64, aproximadamente 3 GB livres, conexão durante a instalação do toolchain e tempo para baixar JDK/Gradle/SDK.
- Para Chat e Codex: um modelo `.gguf` compatível e memória suficiente. O tamanho do modelo não é embutido no APK.

## Compilar esta árvore no desktop

Requer JDK 17 e Android SDK 35.

```bash
./gradlew testDebugUnitTest
./gradlew :app:assembleDebug
```

O APK de debug usa o applicationId `app.girassol.debug`; a variante release usa `app.girassol`.

## Toolchain privado no Android

O Studio instala um bootstrap Termux, OpenJDK 21, Gradle, Android SDK 35 e AAPT2 ARM64 em `files/toolchain`. Downloads críticos têm SHA-256 fixado; arquivos são extraídos com limites e validação de caminhos. O build executa `gradle :app:assembleDebug`, localiza o APK produzido e somente então habilita a instalação pelo fluxo de confirmação do Android.

Espera-se que o aparelho permita execução de binários no sandbox privado. Políticas de fabricante, SELinux, memória e mudanças nos pacotes podem impedir o toolchain. Uma falha deve aparecer no log e no histórico; não é convertida em sucesso visual.

## Limites explícitos

- **LoRA/fine-tuning está bloqueado** até que um motor compatível seja instalado e validado. Criar JSONL não altera pesos.
- **Sono profundo** consolida eventos em memória JSON e métricas determinísticas; não altera pesos.
- **Codex** gera uma proposta com o mesmo GGUF local. Aplicar envia ao editor; salvar grava no projeto.
- Não há fallback remoto ativo nesta revisão. Groq e Hugging Face não recebem dados.
- A instalação de APK exige autorização de fontes desconhecidas e confirmação do sistema.
- O comportamento do toolchain e da inferência deve ser validado em aparelhos reais antes de uma release de produção.
- O Android Lint sinalizou que `libggml-base.so` da dependência Llamatik 0.12.0 não tem alinhamento ELF de 16 KB; dispositivos com páginas de 16 KB exigem uma distribuição nativa atualizada ou recompilada antes de suportar inferência nessa configuração.

## Segurança e privacidade

Sem root, sem desativação do SELinux e sem execução de código copiado de READMEs. O workspace é canonicalizado contra path traversal; imports têm limites de entrada e tamanho; modelos e toolchain são excluídos do backup. O histórico de chat e as notas ficam no armazenamento privado. Consulte `docs/ARCHITECTURE.md` e `docs/SECURITY.md`.

## Licença

MIT. Componentes de terceiros mantêm suas próprias licenças; veja `THIRD_PARTY_NOTICES.md`.
