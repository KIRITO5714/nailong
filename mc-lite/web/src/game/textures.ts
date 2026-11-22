import type { BlockDef, Content } from '../types';
import { canvas, PX, rng, shade } from './art';
import { paintMob } from './mobArt';

// 全部贴图都是代码画出来的 16×16 像素图，不依赖任何外部美术资源。
// 方块与物品在这里画，怪物造型分到了 mobArt.ts（7 种形状复用给 15 只怪）。

function frame(c: HTMLCanvasElement, g: CanvasRenderingContext2D) {
  g.fillStyle = 'rgba(255,255,255,0.10)';
  g.fillRect(0, 0, PX, 1);
  g.fillRect(0, 0, 1, PX);
  g.fillStyle = 'rgba(0,0,0,0.24)';
  g.fillRect(0, PX - 1, PX, 1);
  g.fillRect(PX - 1, 0, 1, PX);
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
  return frame(c, g);
}

function paintSword(g: CanvasRenderingContext2D, color: string, accent: string) {
  g.fillStyle = '#6b4a2a';
  for (let i = 0; i < 5; i++) g.fillRect(2 + i, 13 - i, 2, 2);
  g.fillStyle = accent;
  g.fillRect(5, 9, 5, 2);
  g.fillRect(6, 8, 2, 4);
  g.fillStyle = color;
  for (let i = 0; i < 7; i++) g.fillRect(7 + i, 8 - i, 2, 2);
  g.fillStyle = shade(color, 50);
  for (let i = 0; i < 6; i++) g.fillRect(8 + i, 8 - i, 1, 1);
}

function paintMaterial(g: CanvasRenderingContext2D, key: string, color: string, accent: string) {
  const rnd = rng(key.length * 104729 + 7);
  g.fillStyle = color;
  if (key === 'gel') {
    g.fillRect(4, 7, 8, 6);
    g.fillRect(3, 9, 10, 3);
    g.fillStyle = accent;
    g.fillRect(5, 8, 3, 2);
    return;
  }
  if (key === 'hide') {
    g.fillRect(3, 4, 10, 9);
    g.fillRect(4, 3, 8, 11);
    g.fillStyle = shade(color, -40);
    for (let i = 0; i < 8; i++) g.fillRect(4 + Math.floor(rnd() * 8), 4 + Math.floor(rnd() * 8), 1, 1);
    g.fillStyle = accent;
    g.fillRect(4, 5, 8, 1);
    return;
  }
  if (key === 'bone') {
    g.fillRect(4, 6, 8, 3);
    g.fillRect(3, 5, 3, 5);
    g.fillRect(10, 5, 3, 5);
    g.fillStyle = accent;
    g.fillRect(4, 9, 8, 1);
    return;
  }
  if (key === 'wing') {
    g.fillRect(4, 6, 8, 2);
    g.fillRect(5, 8, 7, 2);
    g.fillRect(7, 10, 5, 2);
    g.fillStyle = accent;
    g.fillRect(4, 5, 8, 1);
    g.fillRect(6, 7, 5, 1);
    return;
  }
  // 晶核：一颗发亮的菱形
  g.fillRect(6, 3, 4, 2);
  g.fillRect(4, 5, 8, 6);
  g.fillRect(6, 11, 4, 2);
  g.fillStyle = accent;
  g.fillRect(6, 5, 3, 3);
  g.fillRect(7, 8, 2, 2);
}

function paintItem(key: string, color: string, accent: string, weapon?: number) {
  const c = canvas();
  const g = c.getContext('2d')!;
  if (weapon !== undefined) paintSword(g, color, accent);
  else paintMaterial(g, key, color, accent);
  return c;
}

export function buildTextures(content: Content) {
  const blocks = new Map<number, HTMLCanvasElement>();
  for (const def of content.blocks) blocks.set(def.id, paintBlock(def));
  const items = new Map<string, HTMLCanvasElement>();
  for (const [key, def] of Object.entries(content.items)) {
    items.set(key, paintItem(key, def.color, def.accent, def.weapon));
  }
  const mobs = new Map<string, HTMLCanvasElement>();
  for (const def of content.mobs) mobs.set(def.key, paintMob(def));
  return { blocks, items, mobs };
}

export type Textures = ReturnType<typeof buildTextures>;
