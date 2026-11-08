# Autonomia de dispositivo

A partir da 2.0 o Girassol deixa de agir apenas dentro do próprio workspace. Com o serviço
de acessibilidade ativo, o agente lê a árvore da tela e executa gestos em outros apps.

Nada disso é automático: o Android exige que **você** habilite o serviço em
Configurações → Acessibilidade → Girassol — Autonomia. O app não consegue se autoautorizar.

## Camadas

```text
AgentTool (ReAct/Codex)
   └── device_*  ──►  DeviceController  ──►  AccessibilityService
                         │                        ├── AccessibilityNodeInfo (leitura)
                         │                        ├── dispatchGesture     (toque/swipe)
                         │                        └── takeScreenshot      (Android 11+)
                         └── ActionJournal (JSONL, 500 linhas)
```

- `core/device/DeviceModels.kt` — `UiNode`, `Bounds`, `UiSelector`, `UiSnapshot`. Puro Kotlin,
  testável na JVM.
- `core/device/AccessibilityDeviceController.kt` — implementação real, defensiva por OEM.
- `core/device/DeviceTools.kt` — 9 ferramentas expostas ao modelo.
- `core/device/DeviceScript.kt` — roteiros declarativos + runner (auto-teste).
- `core/device/ActionJournal.kt` — evidência local, rotativa.

## Ferramentas

| Ferramenta | Para quê |
|---|---|
| `device_ui` | árvore da tela (pacote, textos, ids, posições, flags) |
| `device_tap` | toque por seletor (`text`/`desc`/`id`) ou coordenada |
| `device_type` | texto no campo indicado ou no campo com foco |
| `device_swipe` | rolagem `up`/`down`/`left`/`right` |
| `device_key` | `back`, `home`, `recents`, `notifications` |
| `device_open` | abre app por package name |
| `device_wait` | espera um nó aparecer (timeout) |
| `device_screenshot` | PNG da tela atual (Android 11+) |
| `device_script` | executa um roteiro e devolve relatório |

O agente é instruído a sempre ler a tela antes de agir e a reler depois para confirmar.
Toda chamada grava antes/depois no diário (assinatura SHA-1 curta da árvore).

## Roteiros (auto-teste)

```json
{
  "name": "abrir wi-fi",
  "steps": [
    {"action": "open", "package": "com.android.settings"},
    {"action": "wait", "text": "Rede e internet", "timeout_ms": 5000},
    {"action": "tap", "text": "Rede e internet"},
    {"action": "assert", "text": "Wi-Fi"},
    {"action": "assert", "text": "Bluetooth", "absent": true}
  ]
}
```

Ações: `open`, `wait`, `tap`, `type`, `swipe`, `key`, `assert` (`absent: true` para negar),
`sleep` (`ms`). Máximo de 25 passos. O runner para no primeiro erro e devolve o relatório
com o motivo — é isso que o Codex usa como evidência de teste de UI.

## Ciclo noturno

`NightWorker` (WorkManager) roda a cada 6 h **se** o aparelho estiver ocioso, carregando,
com bateria e armazenamento ok. Antes de executar:

1. `CycleBudget` checa RAM livre (≥ 0,35 GB), bateria (≥ 15% ou carregando) e térmica;
2. `ThermalGate` bloqueia em `THERMAL_STATUS_MODERATE` ou acima;
3. o worker consolida a memória de aprendizado (`LearningContinuity.dream()`) e registra
   o resultado em `SharedPreferences`.

Treino de LoRA **não** roda no ciclo noturno: exige modelo e dataset escolhidos e uma
sessão em primeiro plano. O painel de Autonomia diz isso explicitamente em vez de prometer
o que o Android não entrega.

## Limites (de propósito)

- **Google Play:** automação por acessibilidade não é aceita para uso geral. Distribua por
  sideload/enterprise.
- **WorkManager:** melhor-esforço. Doze pode atrasar horas; não é um cron.
- **Assinatura de APK:** o app não troca o próprio pacote. Sem root, não há auto-update
  fora do PackageInstaller.
- **OEMs:** alguns fabricantes restringem acessibilidade em background. O diário mostra a
  falha em vez de escondê-la.
- **Prompt injection:** conteúdo lido pela pesquisa não deve, sozinho, autorizar ações de
  dispositivo. Mantenha `device_*` desligado durante ciclos automáticos.

## Ativação

1. Ajustes → Autonomia → **Abrir configurações**.
2. Em Acessibilidade, ative **Girassol — Autonomia**.
3. Volte ao app e ligue **Permitir device_\* no ReAct**.
4. Ligue **Sono e consolidação** se quiser o ciclo noturno.
5. Peça algo como: “abra as configurações, entre em Wi-Fi e me diga a rede conectada”.

O diário de ações fica em `files/labforge/workspace/.girassol/device-journal.jsonl`.
