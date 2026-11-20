import type { BlockDef, Content } from '../types';

// 全部贴图都是代码画出来的 16×16 像素图，不依赖任何外部美术资源。
const PX = 16;

function rng(seed: number) {
  let s = seed >>> 0;
  return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296);
}

function shade(hex: string, amount: number) {
  const n = parseInt(hex.slice(1), 16);
  const clamp = (v: number) => Math.max(0, Math.min(255, v));
  const r = clamp(((n >> 16) & 255) + amount);
  const g = clamp(((n >> 8) & 255) + amount);
  const b = clamp((n & 255) + amount);
  return `rgb(${r},${g},${b})`;
}

function canvas() {
  const c = document.createElement('canvas');
  c.width = PX;
  c.height = PX;
  return c;
}

function paintBlock(def: BlockDef) {
  const c = canvas();
  const g = c.getContext('2d')!;
  const rnd = rng(def.id * 7919 + 13);
  g.fillStyle = def.color;
  g.fillRect(0, 0, PX, PX);
  for (let i = 0; i < 52; i++) {
    g.fillStyle = shade(def.color, rnd() < 0.5 ? -16 : 14);
    g.fillRect(Math.floor(rnd() * PX), Math.floor(rnd() * PX), 1, 1);
  }
  if (def.ore) {
    for (let i = 0; i < 5; i++) {
      const x = 1 + Math.floor(rnd() * (PX - 4));
      const y = 1 + Math.floor(rnd() * (PX - 4));
      g.fillStyle = def.accent;
      g.fillRect(x, y, 3, 2);
      g.fillRect(x + 1, y - 1, 2, 1);
      g.fillStyle = shade(def.accent, 46);
      g.fillRect(x, y, 1, 1);
    }
  }
  if (def.key === 'leaves') {
    for (let i = 0; i < 26; i++) {
      g.fillStyle = rnd() < 0.5 ? shade(def.color, -26) : def.accent;
      g.fillRect(Math.floor(rnd() * PX), Math.floor(rnd() * PX), 2, 2);
    }
  }
  if (def.vertical) {
    g.fillStyle = shade(def.color, -28);
    for (const x of [2, 3, 8, 12]) g.fillRect(x, 0, 1, PX);
    g.fillStyle = def.accent;
    g.fillRect(5, 0, 2, PX);
    g.fillRect(10, 0, 1, PX);
  }
  if (def.key === 'planks') {
    g.fillStyle = shade(def.color, -34);
    for (const y of [0, 5, 10, 15]) g.fillRect(0, y, PX, 1);
    g.fillRect(7, 0, 1, 5);
    g.fillRect(3, 5, 1, 5);
    g.fillRect(11, 10, 1, 5);
  }
  if (def.top) {
    g.fillStyle = def.accent;
    g.fillRect(0, 0, PX, def.key === 'grass' ? 3 : 4);
    if (def.key === 'grass') {
      for (let x = 0; x < PX; x++) {
        if (rnd() < 0.45) g.fillRect(x, 3, 1, 1 + Math.floor(rnd() * 2));
      }
      g.fillStyle = shade(def.accent, 34);
      g.fillRect(0, 0, PX, 1);
    } else {
      g.fillStyle = shade(def.accent, -30);
      g.fillRect(0, 3, PX, 1);
      g.fillRect(5, 0, 1, 3);
      g.fillRect(10, 0, 1, 3);
      g.fillStyle = shade(def.color, -40);
      g.fillRect(2, 6, 12, 1);
      g.fillRect(2, 11, 12, 1);
    }
  }
  if (def.key === 'bedrock') {
    for (let i = 0; i < 30; i++) {
      g.fillStyle = rnd() < 0.5 ? '#15151b' : '#4a4a55';
      g.fillRect(Math.floor(rnd() * PX), Math.floor(rnd() * PX), 2, 2);
    }
  }
  if (def.key === 'sand') {
    for (let i = 0; i < 40; i++) {
      g.fillStyle = rnd() < 0.5 ? shade(def.color, -22) : shade(def.color, 20);
      g.fillRect(Math.floor(rnd() * PX), Math.floor(rnd() * PX), 1, 1);
    }
    g.fillStyle = def.accent;
    for (let i = 0; i < 6; i++) g.fillRect(Math.floor(rnd() * (PX - 1)), Math.floor(rnd() * (PX - 1)), 2, 1);
  }
  if (def.key === 'gravel') {
    for (let i = 0; i < 14; i++) {
      const x = Math.floor(rnd() * (PX - 3));
      const y = Math.floor(rnd() * (PX - 3));
      g.fillStyle = rnd() < 0.5 ? def.accent : shade(def.color, 26);
      g.fillRect(x, y, 3, 2);
      g.fillStyle = shade(def.color, -30);
      g.fillRect(x, y + 2, 3, 1);
    }
  }
  if (def.key === 'glow_crystal') {
    g.fillStyle = shade(def.color, -14);
    g.fillRect(0, 0, PX, PX);
    for (const [x, y, h] of [[3, 5, 8], [7, 3, 10], [11, 6, 7]] as const) {
      g.fillStyle = def.accent;
      g.fillRect(x, y, 2, h);
      g.fillRect(x - 1, y + 2, 1, h - 3);
      g.fillStyle = shade(def.accent, 60);
      g.fillRect(x, y, 1, h - 2);
    }
    g.fillStyle = 'rgba(126,240,255,0.35)';
    g.fillRect(0, 12, PX, 2);
  }
  g.fillStyle = 'rgba(255,255,255,0.10)';
  g.fillRect(0, 0, PX, 1);
  g.fillRect(0, 0, 1, PX);
  g.fillStyle = 'rgba(0,0,0,0.24)';
  g.fillRect(0, PX - 1, PX, 1);
  g.fillRect(PX - 1, 0, 1, PX);
  return c;
}

function paintItem(key: string, color: string, accent: string) {
  const c = canvas();
  const g = c.getContext('2d')!;
  const rnd = rng(key.length * 104729 + 7);
  if (key.endsWith('_pickaxe')) {
    g.fillStyle = '#6b4a2a';
    for (let i = 0; i < 9; i++) g.fillRect(3 + i, 13 - i, 2, 2);
    g.fillStyle = color;
    g.fillRect(7, 2, 7, 2);
    g.fillRect(6, 3, 3, 2);
    g.fillRect(12, 3, 3, 2);
    g.fillRect(5, 4, 2, 2);
    g.fillRect(14, 4, 1, 2);
    g.fillStyle = accent;
    g.fillRect(8, 2, 5, 1);
    return c;
  }
  if (key === 'diamond') {
    g.fillStyle = color;
    g.fillRect(5, 4, 6, 2);
    g.fillRect(3, 6, 10, 3);
    g.fillRect(5, 9, 6, 2);
    g.fillRect(7, 11, 2, 1);
    g.fillStyle = accent;
    g.fillRect(5, 5, 2, 2);
    g.fillRect(8, 6, 2, 1);
    return c;
  }
  if (key === 'coal') {
    g.fillStyle = color;
    g.fillRect(4, 5, 8, 7);
    g.fillRect(3, 7, 10, 4);
    g.fillRect(6, 3, 5, 2);
    g.fillStyle = accent;
    g.fillRect(5, 6, 2, 2);
    g.fillRect(9, 9, 2, 1);
    return c;
  }
  // 锭：一条梯形金属块
  g.fillStyle = color;
  g.fillRect(3, 7, 10, 5);
  g.fillRect(4, 6, 8, 1);
  g.fillStyle = accent;
  g.fillRect(4, 7, 8, 1);
  g.fillRect(5, 8, 3, 1);
  g.fillStyle = shade(color, -50);
  g.fillRect(3, 11, 10, 1);
  for (let i = 0; i < 4; i++) {
    g.fillStyle = shade(color, rnd() < 0.5 ? -20 : 20);
    g.fillRect(4 + Math.floor(rnd() * 8), 8 + Math.floor(rnd() * 3), 1, 1);
  }
  return c;
}

export function buildTextures(content: Content) {
  const blocks = new Map<number, HTMLCanvasElement>();
  for (const def of content.blocks) blocks.set(def.id, paintBlock(def));
  const items = new Map<string, HTMLCanvasElement>();
  for (const [key, def] of Object.entries(content.items)) {
    items.set(key, def.place !== undefined ? blocks.get(def.place)! : paintItem(key, def.color, def.accent));
  }
  return { blocks, items };
}

export type Textures = ReturnType<typeof buildTextures>;
