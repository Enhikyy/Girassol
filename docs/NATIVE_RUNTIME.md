<!-- coderabbit-ai-generated:v1 -->

# Runtime nativo do Girassol

## Inferência

Em Ajustes, selecione um GGUF instruct com chat template suportado pelo llama.cpp. O padrão agora prioriza inferência local. O motor JNI executa em CPU ARM64, gera tokens, respeita cancelamento e usa a janela de contexto configurada. Chat, Codex, Llamatik e autoestudo compartilham essa configuração.

Um caminho de adaptador LoRA aplica uma adaptação. Uma lista JSON permite compor deltas de vários adaptadores compatíveis: `[{"path":"/caminho/antigo.gguf","scale":0.5},{"path":"/caminho/novo.gguf","scale":0.5}]`. Essa composição ocorre em inferência; não exporta um adaptador fundido nem implementa SRC/SVC.

O fallback envia a conversa ao endpoint configurado somente quando ativado em Ajustes e antes de qualquer token local entregue. Os atalhos Groq/Hugging Face configuram endpoints; exigem credenciais e um ID de modelo válido do provedor. Cotas são definidas pelo provedor.

<!-- coderabbit-ai-generated:v1 -->

<!-- coderabbit-ai-generated-section:v1:dd868fd1ff12f3231f46de59404c61888df15cfffa029271dbc547a2b429a913 -->

## Android sem root

O dispositivo não tem root. Essa informação substitui a premissa inicial do anexo. O terminal comum executa `/system/bin/sh` com o UID do Girassol. Caminhos absolutos continuam disponíveis nas ferramentas, mas o Android determina quais arquivos o aplicativo pode acessar. Não há `su`, `input`, `uiautomator` nem instalação silenciosa por `pm`.

- `android_control`: lê a hierarquia da janela ativa, executa gestos, preenche campos Unicode por foco ou ID, envia HOME/BACK/RECENTS, inicia aplicativos e mede RAM/bateria. Captura de tela requer Android 11 ou superior; janelas protegidas podem recusá-la.
- `android_ui_test`: executa passos sequenciais; `assert_text` procura texto ou descrição na hierarquia e espera até `timeout_ms` (padrão 5 segundos, máximo 30 segundos).
- `termux_command`: executa bash no Termux por `RUN_COMMAND`, aguardando stdout, stderr e exitCode pelo PendingIntent oficial.
- `android_build`: clona projetos, diagnostica ferramentas, executa assembleDebug/testDebugUnitTest/lintDebug e abre APKs no instalador Android. A abertura do instalador não confirma que o APK foi instalado.

### Preparação no aparelho

1. Em **Ajustes → Android sem root → Configurar Acessibilidade**, ative Girassol. O Android exige ativação manual. Em instalações externas, algumas versões também exigem liberar as configurações restritas do aplicativo na tela de informações do Android.
2. Instale e abra o Termux. Prepare nele git, bash, JDK 21, SDK com a plataforma exigida pelo projeto e aapt2 ARM64. Defina `ANDROID_HOME` no perfil bash do Termux. O Gradle usa `$PREFIX/bin/aapt2` para não tentar executar o artefato Linux x86 do Maven.
3. No Termux, habilite a integração externa:

   ```bash
   mkdir -p ~/.termux
   touch ~/.termux/termux.properties
   # Edite o arquivo para manter uma única definição:
   # allow-external-apps=true
   ```

4. No Girassol, toque **Autorizar comandos no Termux** e conceda a permissão. Configure o caminho do projeto e salve. O padrão é `/data/data/com.termux/files/home/girassol`; `~/girassol` também é aceito.
5. Para instalar um APK, autorize a fonte na configuração do Android e confirme a instalação. APKs privados do Girassol são compartilhados por FileProvider; APKs do Termux são abertos por `termux-open` e podem exigir autorização da fonte Termux.

Os dois aplicativos têm UIDs e arquivos privados distintos. Um projeto editado no workspace privado do Girassol não aparece automaticamente no Termux. Clone e edite o projeto da compilação por `termux_command`. Essa integração utiliza o Termux instalado separadamente; não o embute no APK.

Exemplo de ferramentas do agente:

```json
{"tool":"android_build","arguments":{"action":"clone","repository":"https://github.com/Enhikyy/Girassol.git"}}
{"tool":"android_build","arguments":{"action":"diagnose"}}
{"tool":"android_build","arguments":{"action":"build"}}
{"tool":"android_ui_test","arguments":{"steps":[{"action":"launch","package":"app.girassol"},{"action":"assert_text","text":"Girassol","timeout_ms":5000}]}}
```

Se o Termux não devolver resultado, a chamada expira com erro e informa que o processo externo pode continuar. Cancelar o agente interrompe a espera; não garante o encerramento de processos em outro aplicativo. O Termux pode limitar a saída devolvida. O Android também pode restringir início de serviços e atividades em segundo plano.

### Memória e energia

O modo sono usa WorkManager sem privilégios elevados. A limpeza de memória remove caches do Girassol e mede a variação da RAM disponível. No Android 14+, não tenta encerrar aplicativos de terceiros. Em versões anteriores, as solicitações públicas podem ser ignoradas pelo sistema. Uma variação positiva de RAM não comprova que um processo foi encerrado.

### Fontes e validação

A integração segue [AccessibilityService do Android](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService) e [RUN_COMMAND do Termux](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent). A restrição de processos está documentada em [ActivityManager](https://developer.android.com/reference/android/app/ActivityManager#killBackgroundProcesses(java.lang.String)).

Testes locais cobrem o protocolo de intenção/callback, erros, timeout, cancelamento e ausência de Acessibilidade. Gestos, captura, compilação dentro do Termux e instalação ainda exigem validação no aparelho físico. O build do APK no sandbox não demonstra a disponibilidade do SDK ARM64 no telefone.

<!-- /coderabbit-ai-generated:v1 -->

## Aprendizado e sono

Falhas de ferramentas são registradas no caderno com contexto e resultado observado. O modo sono consolida lições em um corpus JSONL estável, excluindo falhas do material de repetição. O autoestudo combina esse corpus com os dados novos. Com rede disponível, o sono pesquisa o tema e uma pergunta derivada de falhas registradas. Isso não garante ausência de esquecimento e não inicia automaticamente treino em segundo plano.

## Build

`scripts/build-inference.sh` compila a ponte adicional contra os headers do llama.cpp f7591a6a01f9273de03331732bdc0b39721168be e as bibliotecas ARM64 existentes. Requer NDK 26.1.10909125. O APK inclui `libgirassol_inference.so`; as bibliotecas do motor de treino são preservadas.

## Escopo das referências

O runtime implementa funções em Kotlin/C++, usando llama.cpp. Não incorpora integralmente MLC/Vulkan, LlamaFactory, aider, Semgrep, Maestro, Optuna, MindSpore, MobileFineTuner, SnowDrift, MuonClip, KDA/MoE ou as arquiteturas de pesquisa listadas. Também não importa os arquivos Figma. Essas integrações continuam pendentes e não são demonstradas por um build do APK.
