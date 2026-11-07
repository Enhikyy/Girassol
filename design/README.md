# Design — Girassol 1.3.1

Este diretório contém o sistema visual e os mockups do app.

## Conteúdo

```text
design/
├── preview.html            # mockups 390×844 (HTML/CSS)
├── screenshot.sh           # regenera os PNGs via Chrome headless
├── screenshots/            # 8 telas em PNG (2×)
├── board.png               # prancha única com as 8 telas
└── plugin/                 # plugin Figma que cria as páginas nativas
```

## Paleta

| Token | Hex | Uso |
|---|---|---|
| Background | `#070805` | fundo |
| Surface | `#13160F` | cards |
| Surface High | `#1C2015` | inputs, chips |
| Outline | `#2E331F` | bordas |
| Text | `#FBF6E7` | texto |
| Muted | `#A2A78E` | secundário |
| Gold | `#F2C14E` | CTA, marca |
| Gold Deep | `#C6922A` | gradiente |
| Lime | `#A9D96B` | pesquisa/links |
| Green | `#84D98A` | sucesso |
| Orange | `#FF9D5C` | modelos |
| Red | `#FF6B6B` | erro |

## Tipografia

Inter (fallback Roboto/Arial): display 34/28, headline 24/20, title 17/15, body 15/13/11, label 13/11/10.

## Figma

O plugin em `plugin/` gera **Capa**, **Design System**, **8 Telas** e **Fluxo** como camadas nativas. Instruções em [`../docs/FIGMA.md`](../docs/FIGMA.md).
