// Girassol — Figma design system generator
// Run this plugin in any Figma file. It creates:
//   00 · Capa, 01 · Design System, 02 · Telas, 03 · Fluxo
// All frames, colors, components and phone screens are native Figma layers.

const C = {
  bg: "#070805",
  bgElev: "#0C0E09",
  surface: "#13160F",
  surfaceHigh: "#1C2015",
  outline: "#3A4028",
  text: "#FBF6E7",
  muted: "#B4B89F",
  gold: "#F2C14E",
  goldDeep: "#C6922A",
  lime: "#A9D96B",
  green: "#84D98A",
  orange: "#FF9D5C",
  amber: "#FFD166",
  red: "#FF6B6B",
  white: "#FFFFFF",
};

function rgb(hex, alpha = 1) {
  const h = hex.replace("#", "");
  return {
    r: parseInt(h.slice(0, 2), 16) / 255,
    g: parseInt(h.slice(2, 4), 16) / 255,
    b: parseInt(h.slice(4, 6), 16) / 255,
    a: alpha,
  };
}

function solid(hex, alpha = 1) {
  return { type: "SOLID", color: rgb(hex, alpha), opacity: alpha };
}

function gradient(stops) {
  return {
    type: "GRADIENT_LINEAR",
    gradientTransform: [[1, 0, 0], [0, 1, 0]],
    gradientStops: stops.map(([position, hex, alpha = 1]) => ({
      position,
      color: rgb(hex, alpha),
    })),
  };
}

const FONT = {
  regular: { family: "Inter", style: "Regular" },
  medium: { family: "Inter", style: "Medium" },
  semibold: { family: "Inter", style: "Semi Bold" },
  bold: { family: "Inter", style: "Bold" },
};

async function loadFonts() {
  for (const style of Object.values(FONT)) {
    try {
      await figma.loadFontAsync(style);
    } catch (e) {
      // Fall back to Roboto if Inter is unavailable.
      style.family = "Roboto";
      try {
        await figma.loadFontAsync(style);
      } catch (e2) {
        style.family = "Arial";
        await figma.loadFontAsync(style);
      }
    }
  }
}

function frame(parent, name, x, y, w, h, fill = null, radius = 0) {
  const node = figma.createFrame();
  node.name = name;
  parent.appendChild(node);
  node.x = x;
  node.y = y;
  node.resize(w, h);
  node.fills = fill ? [solid(fill)] : [];
  node.cornerRadius = radius;
  node.clipsContent = radius > 0;
  return node;
}

function rect(parent, name, x, y, w, h, fill, radius = 0) {
  const node = figma.createRectangle();
  node.name = name;
  parent.appendChild(node);
  node.x = x;
  node.y = y;
  node.resize(w, h);
  node.fills = fill ? [solid(fill)] : [];
  node.cornerRadius = radius;
  return node;
}

function ellipse(parent, name, x, y, w, h, fill, stroke = null, weight = 1) {
  const node = figma.createEllipse();
  node.name = name;
  parent.appendChild(node);
  node.x = x;
  node.y = y;
  node.resize(w, h);
  node.fills = fill ? [solid(fill)] : [];
  if (stroke) {
    node.strokes = [solid(stroke)];
    node.strokeWeight = weight;
    node.strokeAlign = "INSIDE";
  }
  return node;
}

function text(parent, name, value, x, y, w, size, color, weight = "regular", opts = {}) {
  const node = figma.createText();
  node.name = name;
  parent.appendChild(node);
  node.fontName = FONT[weight];
  node.fontSize = Math.max(size, 11); // readable floor, mirrors the app
  node.fills = [solid(color)];
  node.characters = value;
  node.x = x;
  node.y = y;
  node.resize(w, Math.max(size + 4, 10));
  node.textAutoResize = "HEIGHT";
  node.lineHeight = { value: opts.lineHeight || size * 1.35, unit: "PIXELS" };
  if (opts.spacing !== undefined) {
    node.letterSpacing = { value: opts.spacing, unit: "PIXELS" };
  }
  if (opts.align) node.textAlignHorizontal = opts.align;
  if (opts.opacity !== undefined) node.opacity = opts.opacity;
  return node;
}

function shadow(node, alpha = 0.5, y = 20, radius = 40) {
  node.effects = [{
    type: "DROP_SHADOW",
    color: { r: 0, g: 0, b: 0, a: alpha },
    offset: { x: 0, y },
    radius,
    spread: 0,
    visible: true,
    blendMode: "NORMAL",
  }];
}

function card(parent, name, x, y, w, h, opts = {}) {
  const node = frame(
    parent,
    name,
    x,
    y,
    w,
    h,
    opts.fill || C.surface,
    opts.radius === undefined ? 18 : opts.radius
  );
  node.strokes = [solid(opts.stroke || C.outline, opts.strokeAlpha || 1)];
  node.strokeWeight = 1;
  node.strokeAlign = "INSIDE";
  if (opts.shadow) shadow(node, 0.35, 12, 28);
  return node;
}

function pill(parent, label, x, y, color = C.lime, filled = false) {
  const w = Math.max(64, label.length * 7.6 + 26);
  const node = frame(parent, `Pill / ${label}`, x, y, w, 22, filled ? color : null, 11);
  if (!filled) {
    node.strokes = [solid(color, 0.55)];
    node.strokeWeight = 1;
    node.strokeAlign = "INSIDE";
  }
  text(
    node,
    "label",
    label,
    0,
    -2,
    w,
    9,
    filled ? C.bg : color,
    "semibold",
    { align: "CENTER", spacing: 0.5 }
  );
  return node;
}

function button(parent, label, x, y, w, h = 46, primary = true) {
  const node = frame(parent, `Button / ${label}`, x, y, w, h, null, 14);
  node.fills = primary
    ? [gradient([[0, C.gold], [1, C.goldDeep]])]
    : [solid(C.surfaceHigh)];
  if (!primary) {
    node.strokes = [solid(C.outline)];
    node.strokeWeight = 1;
    node.strokeAlign = "INSIDE";
  }
  text(
    node,
    "label",
    label,
    0,
    h / 2 - 8,
    w,
    12,
    primary ? C.bg : C.text,
    "semibold",
    { align: "CENTER" }
  );
  return node;
}

function mark(parent, x, y, size) {
  const petals = 12;
  for (let i = 0; i < petals; i++) {
    const petalW = size * 0.17;
    const petalH = size * 0.42;
    const p = ellipse(
      parent,
      `Petal ${i + 1}`,
      x + size / 2 - petalW / 2,
      y + size / 2 - petalH / 2,
      petalW,
      petalH,
      C.gold
    );
    p.rotation = (360 / petals) * i;
  }
  ellipse(parent, "Center", x + size * 0.35, y + size * 0.35, size * 0.3, size * 0.3, C.goldDeep);
  ellipse(parent, "Center dot", x + size * 0.43, y + size * 0.43, size * 0.14, size * 0.14, C.bg);
  ellipse(parent, "Leaf A", x + size * 0.55, y + size * 0.82, size * 0.30, size * 0.13, C.green);
  ellipse(parent, "Leaf B", x + size * 0.18, y + size * 0.82, size * 0.30, size * 0.13, C.lime);
}

function phone(page, name, x, y) {
  const p = frame(page, name, x, y, 390, 844, C.bg, 30);
  p.clipsContent = true;
  shadow(p, 0.55, 24, 60);
  // status bar
  text(p, "clock", "9:41", 22, 14, 40, 10, C.text, "semibold");
  text(p, "status", "5G  ▮▮▮", 302, 14, 70, 9, C.muted, "medium", { align: "RIGHT" });
  return p;
}

function bottomNav(p, active) {
  const bar = frame(p, "Bottom navigation", 12, 772, 366, 60, "#1B1E14", 22);
  bar.strokes = [solid(C.white, 0.07)];
  bar.strokeWeight = 1;
  bar.strokeAlign = "INSIDE";
  const tabs = [
    ["Início", "home"],
    ["Modelos", "models"],
    ["Lab", "lab"],
    ["Agentes", "agents"],
    ["Ajustes", "settings"],
  ];
  const width = 366 / tabs.length;
  tabs.forEach(([label], i) => {
    const on = i === active;
    const x = i * width;
    ellipse(bar, `dot ${label}`, x + width / 2 - 3, 9, 6, 6, on ? C.gold : C.muted);
    text(bar, label, label, x, 23, width, 11, on ? C.gold : C.muted, on ? "semibold" : "medium", {
      align: "CENTER",
    });
  });
  return bar;
}

function sectionTitle(p, title, y, action = null) {
  text(p, title, title, 18, y, 260, 15, C.text, "semibold");
  if (action) text(p, `${title} action`, action, 260, y + 2, 112, 9, C.muted, "medium", { align: "RIGHT" });
}

function sourceRow(p, i, title, meta, color) {
  const y = 414 + i * 34;
  ellipse(p, `source dot ${i}`, 20, y + 7, 6, 6, color);
  text(p, `source ${i}`, title, 34, y, 250, 10, C.text, "medium");
  text(p, `source meta ${i}`, meta, 300, y + 1, 70, 8, C.muted, "regular", { align: "RIGHT" });
}

// ---------------------------------------------------------------- screens

function screenHome(page, x, y) {
  const p = phone(page, "Home", x, y);

  // header
  mark(p, 18, 30, 34);
  text(p, "brand", "Girassol", 60, 30, 120, 17, C.text, "bold");
  text(p, "tagline", "pesquisa · dados · LoRA · agentes", 60, 50, 210, 8, C.muted, "regular");
  const mem = frame(p, "memory pill", 268, 34, 62, 22, C.surface, 11);
  ellipse(mem, "status dot", 10, 8, 6, 6, C.green);
  text(mem, "memory", "6.1 GB", 20, 5, 40, 8, C.text, "medium");
  const gear = frame(p, "settings", 338, 32, 28, 28, C.surface, 14);
  text(gear, "gear icon", "⚙", 7, 5, 16, 12, C.muted, "regular", { align: "CENTER" });

  // hero
  const hero = frame(p, "Hero", 16, 82, 358, 226, null, 26);
  hero.fills = [gradient([[0, "#3B2E0B"], [0.55, "#1B1C0C"], [1, "#0D1B12"]])];
  hero.strokes = [solid(C.white, 0.08)];
  hero.strokeWeight = 1;
  hero.strokeAlign = "INSIDE";
  pill(hero, "AUTÔNOMO", 18, 18, C.green, true);
  pill(hero, "llama.cpp", 106, 18, C.lime);
  text(hero, "headline", "Pesquise. Aprenda.\nFaça florescer.", 18, 52, 270, 25, C.white, "bold", {
    lineHeight: 29,
    spacing: -0.7,
  });
  text(
    hero,
    "sub",
    "O agente pesquisa em 6 APIs gratuitas, monta o currículo, gera o dataset e ajusta os hiperparâmetros.",
    18,
    112,
    250,
    9.5,
    C.white,
    "regular",
    { lineHeight: 14, opacity: 0.66 }
  );
  button(hero, "Pesquisar tema", 18, 166, 122, 38);
  const codex = frame(hero, "Codex button", 150, 166, 106, 38, null, 14);
  codex.strokes = [solid(C.white, 0.18)];
  codex.strokeWeight = 1;
  codex.strokeAlign = "INSIDE";
  text(codex, "label", "Abrir Codex", 0, 12, 106, 10, C.white, "medium", { align: "CENTER" });

  // bento
  sectionTitle(p, "Visão do laboratório", 326, "tempo real");
  const stats = [
    ["RAM livre", "6.1 GB", "boost +1.2 GB", C.green],
    ["Pesquisa", "6 APIs", "sem chave", C.lime],
    ["Pipeline", "4 etapas", "pronto", C.gold],
    ["Modelos", "3 GGUF", "arm64", C.orange],
  ];
  stats.forEach(([label, value, meta, color], i) => {
    const col = i % 2;
    const row = Math.floor(i / 2);
    const c = card(p, `Stat ${label}`, 16 + col * 182, 352 + row * 92, 176, 82, { radius: 18 });
    ellipse(c, "accent", 14, 14, 7, 7, color);
    text(c, "label", label, 28, 10, 100, 9, C.muted, "medium");
    text(c, "value", value, 14, 30, 140, 18, C.text, "bold");
    text(c, "meta", meta, 14, 57, 140, 8, C.muted, "regular");
  });

  // continue
  sectionTitle(p, "Continuar", 548, "ver tudo");
  const cont = card(p, "Continue card", 16, 574, 358, 82, { radius: 20 });
  const icon = frame(cont, "icon", 14, 14, 44, 44, C.surfaceHigh, 14);
  text(icon, "spark", "✦", 0, 8, 44, 20, C.gold, "regular", { align: "CENTER" });
  text(cont, "title", "SQL e dbt para engenharia de dados", 70, 13, 250, 11, C.text, "semibold");
  text(cont, "meta", "28 fontes · dataset 312 pares · preset pronto", 70, 32, 250, 8.5, C.muted, "regular");
  const bar = frame(cont, "progress", 70, 54, 250, 4, C.surfaceHigh, 2);
  rect(bar, "fill", 0, 0, 188, 4, C.gold, 2);

  bottomNav(p, 0);
}

function screenAutodidact(page, x, y) {
  const p = phone(page, "Autoestudo", x, y);

  text(p, "title", "Autoestudo", 18, 28, 220, 23, C.text, "bold", { spacing: -0.5 });
  text(p, "sub", "pesquisa → currículo → dataset → preset", 18, 56, 300, 9, C.muted, "regular");
  pill(p, "AO VIVO", 310, 31, C.green, true);

  const stages = ["Pesquisa", "Síntese", "Dataset", "Preset"];
  stages.forEach((label, i) => {
    const x0 = 16 + i * 90;
    const c = frame(p, `stage ${label}`, x0, 82, 86, 38, i < 2 ? "#20260F" : C.surface, 13);
    c.strokes = [solid(i < 2 ? C.gold : C.outline)];
    c.strokeWeight = 1;
    c.strokeAlign = "INSIDE";
    ellipse(c, "dot", 9, 8, 7, 7, i < 2 ? C.gold : C.muted);
    text(c, "label", label, 21, 8, 60, 9, i < 2 ? C.text : C.muted, i < 2 ? "semibold" : "medium");
    text(c, "n", `0${i + 1}`, 9, 23, 30, 7, i < 2 ? C.gold : C.muted, "medium");
  });

  const research = card(p, "Research status", 16, 134, 358, 156, { radius: 22 });
  text(research, "label", "Pesquisando · 3 provedores", 16, 14, 230, 11, C.text, "semibold");
  text(research, "query", "“SQL e dbt para engenharia de dados”", 16, 34, 230, 8.5, C.muted, "regular");
  ellipse(research, "ring bg", 270, 30, 72, 72, null, C.surfaceHigh, 8);
  const ring = ellipse(research, "ring", 270, 30, 72, 72, null, C.gold, 8);
  ring.arcData = { startingAngle: -Math.PI / 2, endingAngle: Math.PI * 0.95, innerRadius: 0.78 };
  text(research, "pct", "72%", 276, 53, 60, 17, C.text, "bold", { align: "CENTER" });
  text(research, "eta", "ETA 00:42", 16, 118, 100, 8, C.muted, "regular");
  const bar = frame(research, "bar", 16, 132, 326, 4, C.surfaceHigh, 2);
  rect(bar, "fill", 0, 0, 236, 4, C.gold, 2);

  text(p, "sources title", "Fontes consolidadas", 18, 306, 200, 11, C.text, "semibold");
  text(p, "sources meta", "3 fontes · 1 provedor", 236, 307, 136, 8, C.muted, "regular", { align: "RIGHT" });
  sourceRow(p, 0, "SQL Style Guide", "sqlstyle.guide", C.lime);
  sourceRow(p, 1, "dbt Docs — Models", "docs.getdbt.com", C.green);
  sourceRow(p, 2, "Data Engineering Design", "openalex.org", C.gold);

  text(p, "preset title", "Preset calculado", 18, 510, 200, 11, C.text, "semibold");
  const specs = [
    ["rank", "16"],
    ["epochs", "3"],
    ["lr", "1.8e-4"],
    ["ctx", "2048"],
  ];
  specs.forEach(([k, v], i) => {
    const c = card(p, `Spec ${k}`, 16 + i * 90, 532, 86, 58, { radius: 14 });
    text(c, "k", k, 10, 9, 60, 8, C.muted, "medium");
    text(c, "v", v, 10, 25, 66, 14, C.text, "semibold");
  });

  const ds = card(p, "Dataset", 16, 604, 358, 64, { radius: 18 });
  text(ds, "t", "Dataset", 14, 10, 100, 10, C.text, "semibold");
  text(ds, "m", "312 pares · 48 KB · chat JSONL", 14, 28, 240, 8.5, C.muted, "regular");
  text(ds, "open", "Abrir", 300, 22, 44, 9, C.gold, "semibold", { align: "RIGHT" });

  button(p, "Iniciar treino LoRA", 16, 684, 358, 46);
  text(p, "hint", "modelo base precisa estar em F32/F16", 16, 737, 358, 8, C.muted, "regular", {
    align: "CENTER",
  });

  bottomNav(p, 2);
}

function screenModels(page, x, y) {
  const p = phone(page, "Modelos", x, y);
  text(p, "title", "Modelos", 18, 28, 180, 23, C.text, "bold", { spacing: -0.5 });
  text(p, "sub", "3 GGUF no dispositivo", 18, 56, 220, 9, C.muted, "regular");
  const add = frame(p, "add", 336, 30, 38, 30, C.surfaceHigh, 12);
  text(add, "plus", "+", 0, 4, 38, 18, C.gold, "regular", { align: "CENTER" });

  const search = frame(p, "search", 16, 84, 358, 40, C.surface, 13);
  text(search, "icon", "⌕", 12, 8, 20, 15, C.muted, "regular");
  text(search, "placeholder", "Buscar modelo, quantização…", 34, 12, 280, 9, C.muted, "regular");

  ["Todos", "Q4_K_M", "Q5_K_M", "F16"].forEach((f, i) => {
    pill(p, f, 16 + i * (i === 0 ? 0 : 0) + [0, 62, 136, 214][i], 136, i === 0 ? C.gold : C.muted, i === 0);
  });

  const models = [
    ["Qwen2.5-1.5B-Instruct", "Q4_K_M", "1.1 GB", "loss 0.842", C.gold, 0.82],
    ["Llama-3.2-1B-Instruct", "Q5_K_M", "0.9 GB", "loss 0.911", C.lime, 0.71],
    ["SmolLM2-360M", "F16", "0.7 GB", "base p/ LoRA", C.orange, 0.94],
  ];
  models.forEach(([name, quant, size, loss, color, progress], i) => {
    const c = card(p, `Model ${name}`, 16, 168 + i * 118, 358, 104, { radius: 20 });
    const badge = frame(c, "badge", 14, 14, 38, 38, C.surfaceHigh, 12);
    text(badge, "glyph", "◈", 0, 8, 38, 16, color, "regular", { align: "CENTER" });
    text(c, "name", name, 64, 13, 220, 11, C.text, "semibold");
    text(c, "meta", `${quant} · ${size}`, 64, 31, 160, 8.5, C.muted, "regular");
    text(c, "loss", loss, 250, 13, 90, 8.5, color, "medium", { align: "RIGHT" });
    const bar = frame(c, "bar bg", 64, 54, 278, 4, C.surfaceHigh, 2);
    rect(bar, "bar", 0, 0, 278 * progress, 4, color, 2);
    text(c, "arch", "llama · 1.5B · 32k ctx", 64, 70, 260, 8, C.muted, "regular");
    text(c, "action", "abrir", 286, 70, 54, 8, C.gold, "semibold", { align: "RIGHT" });
  });

  const storage = card(p, "Storage", 16, 530, 358, 78, { radius: 18 });
  text(storage, "t", "Armazenamento local", 14, 11, 200, 10, C.text, "semibold");
  text(storage, "m", "2.7 GB usados · 41 GB livres", 14, 30, 230, 8.5, C.muted, "regular");
  const bar = frame(storage, "bar bg", 14, 54, 328, 5, C.surfaceHigh, 3);
  rect(bar, "bar", 0, 0, 42, 5, C.green, 3);

  bottomNav(p, 1);
}

function screenTrain(page, x, y) {
  const p = phone(page, "Treinar", x, y);
  text(p, "title", "Laboratório", 18, 28, 220, 23, C.text, "bold", { spacing: -0.5 });
  text(p, "sub", "quantizar · imatrix · treinar", 18, 56, 260, 9, C.muted, "regular");

  const tabs = ["Quantizar", "Imatrix", "Treinar"];
  const tab = frame(p, "tabs", 16, 82, 358, 38, C.surface, 13);
  tabs.forEach((label, i) => {
    const w = 358 / 3;
    const active = i === 2;
    if (active) rect(tab, "active", i * w + 4, 4, w - 8, 30, C.surfaceHigh, 10);
    text(tab, label, label, i * w, 12, w, 9, active ? C.gold : C.muted, active ? "semibold" : "medium", {
      align: "CENTER",
    });
  });

  const preset = card(p, "Preset", 16, 138, 358, 148, { radius: 22 });
  text(preset, "t", "Preset do autoestudo", 16, 14, 240, 11, C.text, "semibold");
  pill(preset, "F32/F16", 270, 12, C.green);
  const rows = [
    ["rank", "16", "alpha", "32"],
    ["epochs", "3", "lr", "1.8e-4"],
    ["ctx", "2048", "alvos", "q/k/v/o"],
  ];
  rows.forEach((r, i) => {
    text(preset, "k", r[0], 16, 44 + i * 28, 60, 8.5, C.muted, "medium");
    text(preset, "v", r[1], 76, 42 + i * 28, 90, 11, C.text, "semibold");
    text(preset, "k2", r[2], 190, 44 + i * 28, 60, 8.5, C.muted, "medium");
    text(preset, "v2", r[3], 250, 42 + i * 28, 90, 11, C.text, "semibold");
  });

  const loss = card(p, "Loss", 16, 302, 358, 152, { radius: 22 });
  text(loss, "t", "Loss · LoRA", 16, 13, 160, 11, C.text, "semibold");
  text(loss, "v", "1.727 → 0.863", 210, 13, 130, 9, C.green, "medium", { align: "RIGHT" });
  const bars = [52, 60, 58, 70, 76, 82, 90, 88, 96, 104, 110, 118];
  bars.forEach((h, i) => {
    const b = rect(loss, `bar ${i}`, 18 + i * 27, 122 - h, 14, h, i < 6 ? C.goldDeep : C.green, 4);
    b.opacity = 0.55 + i * 0.035;
  });
  text(loss, "x", "epoch 1", 16, 130, 70, 7, C.muted, "regular");
  text(loss, "x2", "epoch 3", 300, 130, 42, 7, C.muted, "regular", { align: "RIGHT" });

  const sample = card(p, "Sample", 16, 470, 358, 92, { radius: 18 });
  text(sample, "t", "Última amostra", 14, 10, 160, 9, C.muted, "medium");
  text(sample, "line1", "SELECT customer_id, SUM(total)", 14, 28, 320, 9, C.text, "regular");
  text(sample, "line2", "FROM orders GROUP BY 1;", 14, 44, 320, 9, C.text, "regular");
  text(sample, "meta", "perplexity 2.41 · 38 tok/s", 14, 66, 300, 8, C.green, "regular");

  button(p, "Iniciar treino", 16, 584, 358, 48);
  button(p, "Exportar adapter", 16, 642, 358, 44, false);
  text(p, "hint", "checkpoint automático a cada epoch", 16, 700, 358, 8, C.muted, "regular", {
    align: "CENTER",
  });

  bottomNav(p, 2);
}

function screenCodex(page, x, y) {
  const p = phone(page, "Codex", x, y);
  text(p, "title", "Codex", 18, 28, 180, 23, C.text, "bold", { spacing: -0.5 });
  text(p, "sub", "agente de código autônomo", 18, 56, 250, 9, C.muted, "regular");
  pill(p, "SANDBOX", 298, 31, C.lime);

  const prompt = card(p, "Prompt", 16, 82, 358, 104, { radius: 20 });
  text(prompt, "label", "Tarefa", 14, 11, 100, 8, C.muted, "medium");
  text(prompt, "text", "Adicionar cache LRU ao repositório\nde modelos e cobrir com testes.", 14, 28, 320, 11, C.text, "medium", {
    lineHeight: 17,
  });
  pill(prompt, "repo: girassol", 14, 74, C.lime);
  pill(prompt, "branch: main", 122, 74, C.muted);

  text(p, "plan title", "Plano", 18, 208, 160, 11, C.text, "semibold");
  text(p, "plan meta", "3/5 passos", 300, 209, 72, 8, C.muted, "regular", { align: "RIGHT" });
  const steps = [
    ["Ler ModelStore.kt", true],
    ["Criar LruCache com TTL", true],
    ["Integrar no repositório", true],
    ["Adicionar testes unitários", false],
    ["Rodar gradle test", false],
  ];
  steps.forEach(([label, done], i) => {
    const y0 = 232 + i * 34;
    ellipse(p, `step ${i}`, 18, y0 + 2, 14, 14, done ? C.green : C.surfaceHigh);
    if (done) text(p, "check", "✓", 18, y0, 14, 9, C.bg, "bold", { align: "CENTER" });
    text(p, label, label, 42, y0, 300, 9.5, done ? C.muted : C.text, done ? "regular" : "medium");
  });

  const diff = card(p, "Diff", 16, 414, 358, 174, { radius: 20 });
  text(diff, "t", "app/src/main/java/.../ModelStore.kt", 14, 11, 300, 8, C.muted, "medium");
  const lines = [
    ["+ private val cache = LruCache<String, Model>(32)", C.green],
    ["+ fun get(path: String) = cache[path] ?: read(path)", C.green],
    ["- fun get(path: String) = read(path)", C.red],
    ["+ @Test fun evictsLeastRecentlyUsed() { ... }", C.green],
  ];
  lines.forEach(([line, color], i) => {
    rect(diff, `line bg ${i}`, 10, 34 + i * 29, 338, 24, color, 4).opacity = 0.10;
    text(diff, `line ${i}`, line, 16, 38 + i * 29, 326, 8.5, color, "regular");
  });
  text(diff, "summary", "+38 −4 · 2 arquivos", 14, 154, 200, 8, C.muted, "regular");

  button(p, "Executar alterações", 16, 608, 358, 46);
  text(p, "hint", "o agente pede confirmação para shell e git push", 16, 662, 358, 8, C.muted, "regular", {
    align: "CENTER",
  });

  bottomNav(p, 3);
}

function screenChat(page, x, y) {
  const p = phone(page, "Chat", x, y);
  text(p, "title", "Chat", 18, 28, 180, 23, C.text, "bold", { spacing: -0.5 });
  text(p, "sub", "ReAct · 12 ferramentas", 18, 56, 250, 9, C.muted, "regular");
  pill(p, "ONLINE", 316, 31, C.green, true);

  const user = frame(p, "user bubble", 84, 88, 290, 62, C.surfaceHigh, 18);
  user.cornerRadius = 18;
  text(user, "text", "Pesquise o estado da arte em\nquantização 2-bit e resuma.", 14, 12, 260, 9.5, C.text, "regular", {
    lineHeight: 14,
  });

  const agent = frame(p, "agent bubble", 16, 164, 330, 250, C.surface, 18);
  agent.strokes = [solid(C.outline)];
  agent.strokeWeight = 1;
  agent.strokeAlign = "INSIDE";
  ellipse(agent, "avatar", 12, 12, 26, 26, C.goldDeep);
  text(agent, "bot", "✦", 12, 16, 26, 12, C.bg, "bold", { align: "CENTER" });
  text(agent, "name", "Girassol", 46, 12, 160, 10, C.text, "semibold");
  pill(agent, "research_web", 216, 12, C.lime);
  text(
    agent,
    "reason",
    "Vou buscar em arXiv/OpenAlex e cruzar com\nos resultados do Stack Overflow.",
    14,
    50,
    300,
    8.5,
    C.muted,
    "regular",
    { lineHeight: 13 }
  );
  const tool = frame(agent, "tool card", 14, 92, 302, 52, C.bgElev, 12);
  text(tool, "t", "research_web", 12, 9, 120, 9, C.lime, "semibold");
  text(tool, "m", "arXiv · 8 resultados · 1.2 s", 12, 26, 250, 8, C.muted, "regular");
  text(tool, "status", "✓", 276, 8, 18, 10, C.green, "bold", { align: "RIGHT" });
  text(
    agent,
    "answer",
    "Os métodos 2-bit atuais combinam codebooks\ne merges de outliers. A perda fica em 2–4%\ncontra 4-bit, com ganho de memória de ~2×.",
    14,
    158,
    300,
    9,
    C.text,
    "regular",
    { lineHeight: 14 }
  );
  text(agent, "tokens", "1.8k tokens · 38 tok/s", 14, 222, 220, 7.5, C.muted, "regular");

  const input = frame(p, "input bar", 16, 694, 358, 56, C.surface, 18);
  input.strokes = [solid(C.outline)];
  input.strokeWeight = 1;
  input.strokeAlign = "INSIDE";
  text(input, "placeholder", "Pergunte ou peça uma pesquisa…", 16, 19, 250, 9.5, C.muted, "regular");
  const send = frame(input, "send", 306, 9, 36, 36, null, 18);
  send.fills = [gradient([[0, C.gold], [1, C.goldDeep]])];
  text(send, "arrow", "↑", 0, 6, 36, 16, C.bg, "bold", { align: "CENTER" });

  bottomNav(p, 3);
}

function screenLlamatik(page, x, y) {
  const p = phone(page, "Llamatik Runtime", x, y);
  text(p, "title", "Llamatik Runtime", 18, 28, 280, 21, C.text, "bold", { spacing: -0.5 });
  text(p, "sub", "sdk 2.2.2 · agent streaming", 18, 55, 260, 9, C.muted, "regular");
  pill(p, "STREAMING", 284, 31, C.green, true);

  const consoleCard = frame(p, "console", 16, 84, 358, 300, "#0A0C07", 20);
  consoleCard.strokes = [solid(C.green, 0.18)];
  consoleCard.strokeWeight = 1;
  consoleCard.strokeAlign = "INSIDE";
  text(consoleCard, "head", "agent.stream()", 14, 12, 200, 9, C.lime, "medium");
  ellipse(consoleCard, "live", 330, 14, 6, 6, C.green);
  const lines = [
    ["> tool: public_api_posts", C.muted],
    ["> GET jsonplaceholder.typicode.com", C.muted],
    ["✓ 200 · 100 posts", C.green],
    ["", C.text],
    ["O endpoint devolve 100 posts.", C.text],
    ["Cada um tem userId, id, title e body.", C.text],
    ["Posso agrupar por userId ou filtrar", C.text],
    ["por título. O que você prefere?", C.text],
  ];
  lines.forEach(([line, color], i) => {
    text(consoleCard, `line ${i}`, line || " ", 14, 42 + i * 24, 330, 9, color, i === 0 ? "semibold" : "regular");
  });
  rect(consoleCard, "cursor", 14, 246, 7, 14, C.gold);

  text(p, "tools title", "Tools executadas", 18, 406, 220, 11, C.text, "semibold");
  text(p, "tools meta", "2 chamadas", 300, 407, 72, 8, C.muted, "regular", { align: "RIGHT" });
  const tools = [
    ["public_api_posts", "Retrofit 3 · 200 OK · 184 ms", C.lime],
    ["research_web", "6 APIs · 12 fontes · 2.1 s", C.green],
  ];
  tools.forEach(([name, meta, color], i) => {
    const c = card(p, `Tool ${name}`, 16, 434 + i * 66, 358, 54, { radius: 16 });
    ellipse(c, "dot", 14, 20, 8, 8, color);
    text(c, "name", name, 30, 10, 210, 9.5, C.text, "semibold");
    text(c, "meta", meta, 30, 28, 270, 8, C.muted, "regular");
    text(c, "status", "✓", 316, 18, 26, 10, color, "bold", { align: "RIGHT" });
  });

  const memory = card(p, "Memory", 16, 578, 358, 76, { radius: 18 });
  text(memory, "t", "Memória persistente", 14, 10, 220, 9.5, C.text, "semibold");
  text(memory, "m", "conversation.txt · 4.2 KB · consolidada", 14, 28, 300, 8, C.muted, "regular");
  text(memory, "action", "consolidar", 278, 50, 64, 8, C.gold, "semibold", { align: "RIGHT" });

  button(p, "Nova execução", 16, 674, 358, 46);
  text(p, "hint", "core nativo do Llamatik excluído (conflito de SONAME)", 16, 728, 358, 7.5, C.muted, "regular", {
    align: "CENTER",
  });

  bottomNav(p, 4);
}

function screenSettings(page, x, _y) {
  const p = phone(page, "Ajustes", x, _y);
  text(p, "title", "Ajustes", 18, 28, 180, 23, C.text, "bold", { spacing: -0.5 });
  text(p, "sub", "runtime, pesquisa e treino", 18, 56, 250, 9, C.muted, "regular");

  const sections = [
    ["LLM local", [
      ["Endpoint", "http://127.0.0.1:8080/v1"],
      ["Modelo", "qwen2.5-1.5b-instruct"],
      ["API key", "••••••••••••"],
    ]],
    ["Pesquisa", [
      ["Fontes", "Wikipedia, OpenAlex, arXiv, SO"],
      ["Cache", "48 dossiês · 12 MB"],
      ["Sem LLM", "síntese extrativa"],
    ]],
    ["Treino", [
      ["Modelo base", "SmolLM2-360M F16"],
      ["Threads", "4 de 8"],
      ["Checkpoints", "a cada epoch"],
    ]],
  ];

  let y = 86;
  sections.forEach(([title, rows]) => {
    text(p, title, title, 18, y, 200, 10, C.muted, "semibold");
    y += 22;
    const c = card(p, `${title} card`, 16, y, 358, rows.length * 47 + 10, { radius: 18 });
    rows.forEach(([k, v], i) => {
      text(c, k, k, 14, 12 + i * 47, 120, 9, C.text, "medium");
      text(c, v, v, 140, 12 + i * 47, 200, 8, C.muted, "regular", { align: "RIGHT" });
      if (i < rows.length - 1) rect(c, `divider ${i}`, 14, 35 + i * 47, 330, 1, C.outline);
    });
    y += rows.length * 47 + 22;
  });

  const about = card(p, "About", 16, 670, 358, 64, { radius: 18 });
  mark(about, 14, 16, 28);
  text(about, "name", "Girassol 1.4.0", 54, 12, 180, 10, C.text, "semibold");
  text(about, "meta", "build 6 · arm64-v8a · Android 8+", 54, 30, 240, 8, C.muted, "regular");
  text(about, "lic", "MIT", 300, 24, 44, 8, C.gold, "semibold", { align: "RIGHT" });

  bottomNav(p, 4);
}

function screenCover(page) {
  const c = frame(page, "Capa", 0, 0, 1600, 900, C.bg, 0);
  c.clipsContent = true;
  const glow = ellipse(c, "glow", 900, -220, 900, 900, null, C.gold, 1);
  glow.opacity = 0.10;
  const glow2 = ellipse(c, "glow 2", 1080, 330, 620, 620, null, C.green, 1);
  glow2.opacity = 0.10;

  mark(c, 110, 120, 150);
  text(c, "brand", "Girassol", 110, 300, 700, 86, C.text, "bold", { spacing: -3.2 });
  text(c, "tagline", "Pesquise. Aprenda. Faça florescer.", 114, 410, 720, 26, C.gold, "medium", {
    spacing: -0.4,
  });
  text(
    c,
    "desc",
    "Estúdio Android de GGUF, importance matrix e LoRA nativo\ncom pesquisa autônoma, Codex e agentes no dispositivo.",
    114,
    462,
    660,
    16,
    C.muted,
    "regular",
    { lineHeight: 25 }
  );

  const chips = ["GGUF", "imatrix", "quantização", "LoRA", "autoestudo", "Codex", "Llamatik"];
  let x = 114;
  chips.forEach((label) => {
    const w = label.length * 10 + 32;
    const chip = frame(c, `chip ${label}`, x, 570, w, 38, C.surface, 19);
    chip.strokes = [solid(C.outline)];
    chip.strokeWeight = 1;
    chip.strokeAlign = "INSIDE";
    text(chip, "label", label, 0, 10, w, 11, C.text, "medium", { align: "CENTER" });
    x += w + 10;
  });

  text(c, "version", "v1.4.0  ·  arm64-v8a  ·  Android 8+  ·  36 MB", 114, 660, 620, 12, C.muted, "regular");
  button(c, "github.com/Enhikyy/girassol", 114, 704, 300, 48);
  text(c, "no server", "sem servidor  ·  sem telemetria  ·  MIT", 114, 774, 500, 11, C.muted, "regular");

  // right side: stylized phone stack
  const p1 = phone(page, "Cover phone", 930, 56);
  screenHomePreview(p1);
  p1.opacity = 0.96;
  const p2 = frame(page, "Cover phone 2", 1240, 160, 300, 650, C.surface, 28);
  p2.strokes = [solid(C.outline)];
  p2.strokeWeight = 1;
  p2.strokeAlign = "INSIDE";
  text(p2, "t", "autoestudo", 20, 24, 260, 14, C.text, "semibold");
  text(p2, "m", "pesquisa → dataset → preset", 20, 46, 260, 9, C.muted, "regular");
  ellipse(p2, "ring", 90, 88, 120, 120, null, C.gold, 10).arcData = {
    startingAngle: -Math.PI / 2,
    endingAngle: Math.PI * 0.9,
    innerRadius: 0.78,
  };
  text(p2, "pct", "78%", 100, 132, 100, 24, C.text, "bold", { align: "CENTER" });
  text(p2, "stage", "gerando dataset", 20, 230, 260, 10, C.green, "medium");
  const bar = frame(p2, "bar", 20, 254, 260, 6, C.surfaceHigh, 3);
  rect(bar, "fill", 0, 0, 204, 6, C.gold, 3);
  text(p2, "fonts", "3 fontes · 1 provedor", 20, 278, 260, 9, C.muted, "regular");
  text(p2, "preset", "rank 16 · 3 epochs · lr 1.8e-4", 20, 306, 260, 9, C.text, "medium");
  button(p2, "iniciar LoRA", 20, 560, 260, 44);
  return c;
}

function screenHomePreview(p) {
  mark(p, 18, 30, 34);
  text(p, "brand", "Girassol", 60, 30, 160, 17, C.text, "bold");
  text(p, "tagline", "pesquisa · dados · LoRA · agentes", 60, 50, 230, 8, C.muted, "regular");
  const hero = frame(p, "Hero", 16, 82, 358, 210, null, 26);
  hero.fills = [gradient([[0, "#3B2E0B"], [1, "#0D1B12"]])];
  pill(hero, "AUTÔNOMO", 18, 18, C.green, true);
  text(hero, "headline", "Pesquise. Aprenda.\nFaça florescer.", 18, 52, 270, 25, C.white, "bold", {
    lineHeight: 29,
    spacing: -0.7,
  });
  button(hero, "Pesquisar tema", 18, 154, 122, 38);
  const stats = [
    ["RAM livre", "6.1 GB", C.green],
    ["Pesquisa", "6 APIs", C.lime],
    ["Pipeline", "4 etapas", C.gold],
    ["Modelos", "3 GGUF", C.orange],
  ];
  stats.forEach(([label, value, color], i) => {
    const col = i % 2;
    const row = Math.floor(i / 2);
    const c = card(p, `Stat ${label}`, 16 + col * 182, 310 + row * 92, 176, 82, { radius: 18 });
    ellipse(c, "accent", 14, 14, 7, 7, color);
    text(c, "label", label, 28, 10, 110, 9, C.muted, "medium");
    text(c, "value", value, 14, 30, 140, 18, C.text, "bold");
  });
  bottomNav(p, 0);
}

// ---------------------------------------------------------------- pages

(async () => {
  await loadFonts();

  // color styles
  const styleDefs = [
    ["Background", C.bg], ["Surface", C.surface], ["Surface High", C.surfaceHigh],
    ["Outline", C.outline], ["Text", C.text], ["Muted", C.muted],
    ["Gold", C.gold], ["Gold Deep", C.goldDeep], ["Lime", C.lime],
    ["Green", C.green], ["Orange", C.orange], ["Red", C.red],
  ];
  styleDefs.forEach(([name, hex]) => {
    const style = figma.createPaintStyle();
    style.name = `Girassol/${name}`;
    style.paints = [solid(hex)];
  });

  // Starter plans allow only 3 pages: reuse an empty current page for the cover and
  // place the flow board on the screens page, below the phones.
  const coverPage = figma.currentPage.children.length === 0 ? figma.currentPage : figma.createPage();
  coverPage.name = "00 · Capa";
  const systemPage = figma.createPage();
  systemPage.name = "01 · Design System";
  const screensPage = figma.createPage();
  screensPage.name = "02 · Telas";

  if (figma.setCurrentPageAsync) await figma.setCurrentPageAsync(coverPage);
  else figma.currentPage = coverPage;

  const cover = screenCover(coverPage);

  // design system
  if (figma.setCurrentPageAsync) await figma.setCurrentPageAsync(systemPage);
  else figma.currentPage = systemPage;
  frame(systemPage, "Design System", 0, 0, 1600, 1100, C.bg, 0);
  mark(systemPage, 80, 70, 64);
  text(systemPage, "title", "Girassol — Design System", 160, 78, 900, 34, C.text, "bold", { spacing: -1 });
  text(systemPage, "sub", "sunflower lab · dark UI · 1.4.0", 160, 122, 600, 12, C.muted, "regular");

  text(systemPage, "colors", "Cores", 80, 200, 300, 16, C.text, "semibold");
  styleDefs.forEach(([name, hex], i) => {
    const x = 80 + (i % 6) * 250;
    const y = 238 + Math.floor(i / 6) * 120;
    const sw = frame(systemPage, `Swatch ${name}`, x, y, 220, 80, hex, 14);
    sw.strokes = [solid(C.outline)];
    sw.strokeWeight = 1;
    text(systemPage, `name ${name}`, name, x, y + 88, 150, 9, C.text, "medium");
    text(systemPage, `hex ${name}`, hex, x + 120, y + 88, 100, 8, C.muted, "regular", { align: "RIGHT" });
  });

  text(systemPage, "type", "Tipografia", 80, 500, 300, 16, C.text, "semibold");
  const types = [
    ["Display", 40, "bold", "Pesquise. Aprenda."],
    ["Headline", 28, "semibold", "Autoestudo autônomo"],
    ["Title", 17, "semibold", "Fontes consolidadas"],
    ["Body", 13, "regular", "Pesquisa em 6 APIs gratuitas, sem chave."],
    ["Label", 11, "medium", "RANK · EPOCHS · LR"],
  ];
  types.forEach(([label, size, weight, sample], i) => {
    text(systemPage, `label ${label}`, label, 80, 540 + i * 58, 100, 9, C.muted, "medium");
    text(systemPage, `sample ${label}`, sample, 200, 532 + i * 58, 600, size, C.text, weight, { spacing: -0.3 });
  });

  text(systemPage, "components", "Componentes", 900, 500, 300, 16, C.text, "semibold");
  button(systemPage, "Primário", 900, 540, 180, 46);
  button(systemPage, "Secundário", 1100, 540, 180, 46, false);
  pill(systemPage, "AUTÔNOMO", 900, 610, C.green, true);
  pill(systemPage, "llama.cpp", 1022, 610, C.lime);
  pill(systemPage, "F32/F16", 1140, 610, C.green);
  const demo = card(systemPage, "Card demo", 900, 660, 380, 100, { radius: 20 });
  text(demo, "t", "Glass card", 16, 14, 200, 12, C.text, "semibold");
  text(demo, "m", "superfície #13160F · borda #2E331F", 16, 36, 320, 9, C.muted, "regular");
  const bar = frame(demo, "progress", 16, 66, 330, 6, C.surfaceHigh, 3);
  rect(bar, "fill", 0, 0, 220, 6, C.gold, 3);
  mark(systemPage, 900, 790, 64);
  text(systemPage, "mark label", "Marca / girassol", 980, 810, 200, 10, C.text, "medium");
  text(systemPage, "mark meta", "12 pétalas · dourado + verde", 980, 828, 260, 8, C.muted, "regular");

  // screens
  if (figma.setCurrentPageAsync) await figma.setCurrentPageAsync(screensPage);
  else figma.currentPage = screensPage;
  text(screensPage, "title", "Telas · 1.4.0", 80, 50, 500, 28, C.text, "bold", { spacing: -1 });
  text(screensPage, "sub", "390 × 844 · dark · arm64", 80, 88, 400, 11, C.muted, "regular");
  const screens = [
    [screenHome, "Home"],
    [screenAutodidact, "Autoestudo"],
    [screenModels, "Modelos"],
    [screenTrain, "Treinar"],
    [screenCodex, "Codex"],
    [screenChat, "Chat"],
    [screenLlamatik, "Llamatik"],
    [screenSettings, "Ajustes"],
  ];
  screens.forEach(([fn, name], i) => {
    const col = i % 4;
    const row = Math.floor(i / 4);
    fn(screensPage, 80 + col * 440, 140 + row * 910);
  });

  // flow
  const flowPage = frame(screensPage, "03 · Fluxo", 0, 2050, 1600, 900, C.bg, 0);
  text(flowPage, "title", "Fluxo do autoestudo", 80, 60, 700, 30, C.text, "bold", { spacing: -1 });
  text(flowPage, "sub", "sem servidor · APIs gratuitas · LLM opcional", 80, 100, 700, 12, C.muted, "regular");
  const steps = [
    ["01", "Tema", "usuário define\nnível e escopo"],
    ["02", "Pesquisa", "6 APIs em paralelo\ndeduplica + BM25"],
    ["03", "Síntese", "LLM ou extrativa\nancorada nas fontes"],
    ["04", "Dataset", "currículo → JSONL\nchat format"],
    ["05", "Auto-tuning", "rank, epochs, lr\nctx e ETA"],
    ["06", "LoRA", "F32/F16\nbase congelada"],
  ];
  steps.forEach(([n, title, body], i) => {
    const x = 80 + i * 245;
    const c = card(flowPage, `Step ${n}`, x, 260, 215, 170, { radius: 22 });
    text(c, "n", n, 18, 14, 60, 12, C.gold, "bold");
    text(c, "t", title, 18, 48, 180, 16, C.text, "semibold");
    text(c, "b", body, 18, 78, 180, 9, C.muted, "regular", { lineHeight: 14 });
    if (i < 5) text(flowPage, `arrow ${i}`, "→", x + 218, 330, 24, 18, C.gold, "regular");
  });
  const note = card(flowPage, "note", 80, 510, 730, 120, { radius: 20 });
  text(note, "t", "Sem LLM configurado?", 18, 16, 300, 12, C.text, "semibold");
  text(note, "b", "O pipeline continua com síntese extrativa e Q&A ancorados nos trechos. Nada é inventado; o app informa a origem de cada trecho.", 18, 40, 680, 9.5, C.muted, "regular", { lineHeight: 15 });
  const note2 = card(flowPage, "note2", 840, 510, 680, 120, { radius: 20 });
  text(note2, "t", "Por que não auto-treinar quantizado?", 18, 16, 500, 12, C.text, "semibold");
  text(note2, "b", "O LoRA nativo congela a base e precisa de pesos F32/F16/BF16. Se o GGUF for Q4/Q5, o app ignora o auto-treino e explica o motivo.", 18, 40, 630, 9.5, C.muted, "regular", { lineHeight: 15 });

  figma.viewport.scrollAndZoomIntoView([cover]);
  figma.notify("Girassol: capa, design system, 8 telas e fluxo criados.");
  figma.closePlugin();
})().catch((err) => {
  figma.notify(`Girassol plugin: ${err.message}`, { error: true });
  figma.closePlugin(`Erro: ${err.message}`);
});
