// 像素画共用的小工具：方块、物品、怪物贴图都从这里拿画布、伪随机和调色。
export const PX = 16;

// 带种子的 LCG：同一份内容表每次画出来的贴图都一样，不会每帧抖动。
export function rng(seed: number) {
  let s = seed >>> 0;
  return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296);
}

export function shade(hex: string, amount: number) {
  const n = parseInt(hex.slice(1), 16);
  const clamp = (v: number) => Math.max(0, Math.min(255, v));
  const r = clamp(((n >> 16) & 255) + amount);
  const g = clamp(((n >> 8) & 255) + amount);
  const b = clamp((n & 255) + amount);
  return `rgb(${r},${g},${b})`;
}

export function canvas() {
  const c = document.createElement('canvas');
  c.width = PX;
  c.height = PX;
  return c;
}
