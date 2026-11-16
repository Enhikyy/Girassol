# Design System nativo do Girassol

## Tokens

`ui/theme/Theme.kt` concentra tokens de espaçamento, raios e duração de movimento em `DesignTokens`, além do tema Material 3 e da tipografia existente. Componentes novos devem reutilizar esses valores em vez de definir medidas isoladas.

## Movimento

O Compose já oferece as APIs de estado e layout usadas pelo app. O painel RSI agora usa `AnimatedVisibility`, fade/slide e `animateContentSize` com spring; transições de estado usam durations centralizadas. Para movimento espacial, prefira spring; para opacidade e cor, tween curto.

## AGSL e fallback

`AnimatedAuroraBackground` usa `RuntimeShader`/AGSL em Android 13+ (API 33) e mantém o Canvas radial como fallback em versões anteriores ou se o shader falhar ao compilar. O shader não requer biblioteca de terceiros. Pure Black OLED continua interrompendo o desenho de brilho.

## Renderização e desempenho

- Reutiliza `RuntimeShader` e `Paint` via `remember`, evitando criar objetos em cada frame.
- O shader desenha uma única superfície por frame; não cria partículas individuais nem camadas de blur caras.
- Canvas e AGSL usam o pipeline gráfico Android/Skia já utilizado pelo Compose; SkiaSharp foi deliberadamente omitido para evitar outra engine/binding e cópia de superfície.
- Verificar no Android Studio Layout Inspector/Animation Preview e em hardware físico; CI compila o código, mas não substitui medição de frame time no A22.
