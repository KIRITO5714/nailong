import type { WorldView } from '../types';
import { PLAYER_H, PLAYER_W, type Player } from './physics';
import type { Textures } from './textures';

export interface Camera {
  x: number;
  y: number;
  tile: number;
  w: number;
  h: number;
}

export interface Target {
  x: number;
  y: number;
  progress: number;
  state: 'ok' | 'weak' | 'far' | 'place' | 'blocked';
}

const HIGHLIGHT: Record<Target['state'], string> = {
  ok: 'rgba(255,255,255,0.85)',
  place: 'rgba(120,230,140,0.9)',
  weak: 'rgba(240,120,90,0.9)',
  far: 'rgba(160,160,170,0.5)',
  blocked: 'rgba(240,120,90,0.9)',
};

export function drawScene(
  ctx: CanvasRenderingContext2D,
  view: WorldView,
  player: Player,
  cam: Camera,
  tex: Textures,
  target: Target | null,
) {
  const { tile } = cam;
  ctx.imageSmoothingEnabled = false;
  drawSky(ctx, cam, view);

  const x0 = Math.max(0, Math.floor(cam.x));
  const x1 = Math.min(view.width - 1, Math.ceil(cam.x + cam.w / tile));
  const y0 = Math.max(0, Math.floor(cam.y));
  const y1 = Math.min(view.height - 1, Math.ceil(cam.y + cam.h / tile));

  for (let ty = y0; ty <= y1; ty++) {
    for (let tx = x0; tx <= x1; tx++) {
      const sx = Math.round((tx - cam.x) * tile);
      const sy = Math.round((ty - cam.y) * tile);
      const id = view.blocks[ty * view.width + tx];
      const depth = ty - view.surface[tx];
      if (id !== 0) {
        const texture = tex.blocks.get(id);
        if (texture) ctx.drawImage(texture, sx, sy, tile, tile);
      } else if (depth >= 0) {
        ctx.fillStyle = depth < 6 ? '#3a2c22' : depth < 24 ? '#241d1a' : '#15111a';
        ctx.fillRect(sx, sy, tile, tile);
      } else {
        continue;
      }
      if (depth > 0) {
        // 越深越暗，但玩家身边留一圈光，不然深处什么都看不见。
        const dist = Math.hypot(tx + 0.5 - player.x, ty + 0.5 - player.y);
        const dark = Math.min(0.66, depth / 44) * Math.max(0.16, Math.min(1, dist / 9));
        ctx.fillStyle = `rgba(5,3,10,${dark.toFixed(3)})`;
        ctx.fillRect(sx, sy, tile, tile);
      }
    }
  }

  drawPlayer(ctx, player, cam);
  if (target) drawTarget(ctx, target, cam);
}

function drawSky(ctx: CanvasRenderingContext2D, cam: Camera, view: WorldView) {
  const horizon = (30 - cam.y) * cam.tile;
  const gradient = ctx.createLinearGradient(0, 0, 0, Math.max(cam.h, horizon + cam.h * 0.4));
  gradient.addColorStop(0, '#4d9fe0');
  gradient.addColorStop(0.55, '#8fc7ef');
  gradient.addColorStop(1, '#d7ecff');
  ctx.fillStyle = gradient;
  ctx.fillRect(0, 0, cam.w, cam.h);

  const sunX = cam.w * 0.78 - cam.x * cam.tile * 0.04;
  ctx.fillStyle = 'rgba(255,246,200,0.95)';
  ctx.fillRect(sunX, cam.h * 0.1 - cam.y * cam.tile * 0.06, cam.tile * 1.6, cam.tile * 1.6);

  ctx.fillStyle = 'rgba(255,255,255,0.72)';
  for (let i = 0; i < 5; i++) {
    const span = view.width * cam.tile;
    const cx = (((i * 337 - cam.x * cam.tile * 0.25) % span) + span) % span;
    const cy = (6 + (i % 3) * 4 - cam.y) * cam.tile;
    if (cy < -cam.tile * 3 || cy > cam.h) continue;
    ctx.fillRect(cx, cy, cam.tile * 4, cam.tile * 0.9);
    ctx.fillRect(cx + cam.tile, cy - cam.tile * 0.7, cam.tile * 2.2, cam.tile * 0.8);
  }
}

function drawPlayer(ctx: CanvasRenderingContext2D, p: Player, cam: Camera) {
  const w = PLAYER_W * cam.tile;
  const h = PLAYER_H * cam.tile;
  const left = Math.round((p.x - cam.x) * cam.tile - w / 2);
  const top = Math.round((p.y - cam.y) * cam.tile - h / 2);
  const swing = p.onGround ? Math.sin(p.walk * 3.4) * 0.16 : 0.14;
  const px = (v: number) => Math.round(v);

  if (p.onGround) {
    ctx.fillStyle = 'rgba(0,0,0,0.22)';
    ctx.fillRect(left - px(w * 0.1), top + h - 2, px(w * 1.2), 3);
  }
  ctx.fillStyle = '#2f3548';
  ctx.fillRect(left + px(w * 0.16), top + px(h * 0.66), px(w * 0.26), px(h * 0.34 + swing * h * 0.1));
  ctx.fillRect(left + px(w * 0.58), top + px(h * 0.66), px(w * 0.26), px(h * 0.34 - swing * h * 0.1));
  ctx.fillStyle = '#3f6fb5';
  ctx.fillRect(left + px(w * 0.14), top + px(h * 0.3), px(w * 0.72), px(h * 0.38));
  ctx.fillStyle = '#e8b98d';
  const armX = p.facing === 1 ? left + px(w * 0.68) : left + px(w * 0.14);
  ctx.fillRect(armX, top + px(h * (0.34 + swing * 0.12)), px(w * 0.2), px(h * 0.3));
  ctx.fillRect(left + px(w * 0.16), top, px(w * 0.68), px(h * 0.3));
  ctx.fillStyle = '#3a2a1c';
  ctx.fillRect(left + px(w * 0.14), top, px(w * 0.72), px(h * 0.09));
  ctx.fillRect(p.facing === 1 ? left + px(w * 0.14) : left + px(w * 0.72), top, px(w * 0.14), px(h * 0.2));
  ctx.fillStyle = '#20242e';
  const eyeX = p.facing === 1 ? left + px(w * 0.6) : left + px(w * 0.28);
  ctx.fillRect(eyeX, top + px(h * 0.15), Math.max(2, px(w * 0.12)), Math.max(2, px(h * 0.05)));
}

function drawTarget(ctx: CanvasRenderingContext2D, target: Target, cam: Camera) {
  const sx = Math.round((target.x - cam.x) * cam.tile);
  const sy = Math.round((target.y - cam.y) * cam.tile);
  const size = cam.tile;
  ctx.lineWidth = Math.max(2, size * 0.07);
  ctx.strokeStyle = HIGHLIGHT[target.state];
  ctx.strokeRect(sx + 1, sy + 1, size - 2, size - 2);

  if (target.progress > 0 && target.state === 'ok') {
    ctx.fillStyle = 'rgba(10,8,12,0.55)';
    const cracks = Math.ceil(target.progress * 6);
    for (let i = 0; i < cracks; i++) {
      const t = (i + 1) / 7;
      ctx.fillRect(sx + size * (0.15 + t * 0.5), sy + size * (0.1 + t * 0.6), Math.max(2, size * 0.08), size * 0.3);
      ctx.fillRect(sx + size * (0.7 - t * 0.45), sy + size * (0.55 - t * 0.3), size * 0.26, Math.max(2, size * 0.07));
    }
    const barW = size * 1.1;
    const barX = sx - size * 0.05;
    const barY = sy - Math.max(7, size * 0.24);
    ctx.fillStyle = 'rgba(12,14,20,0.8)';
    ctx.fillRect(barX, barY, barW, Math.max(5, size * 0.14));
    ctx.fillStyle = '#7ee08a';
    ctx.fillRect(barX + 1, barY + 1, (barW - 2) * target.progress, Math.max(3, size * 0.14 - 2));
  }
}
