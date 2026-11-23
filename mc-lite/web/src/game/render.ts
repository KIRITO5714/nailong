import type { WorldView } from '../types';
import type { Mob } from './mobs';
import type { Player } from './physics';
import { canvasOps, paintPlayer, PLAYER_GRID_H, PLAYER_GRID_W, PLAYER_TILE_H, PLAYER_TILE_W } from './playerArt';
import type { Textures } from './textures';

export interface Camera {
  x: number;
  y: number;
  tile: number;
  w: number;
  h: number;
}

// 挥砍动画：t 从 1 递减到 0，画一道扇形刀光。
export interface Swing {
  t: number;
  facing: 1 | -1;
  range: number;
}

export function drawScene(
  ctx: CanvasRenderingContext2D,
  view: WorldView,
  player: Player,
  cam: Camera,
  tex: Textures,
  mobs: Mob[],
  swing: Swing | null,
  lightOf: (id: number) => number = () => 0,
) {
  const { tile } = cam;
  ctx.imageSmoothingEnabled = false;
  drawSky(ctx, cam, view);

  const x0 = Math.max(0, Math.floor(cam.x));
  const x1 = Math.min(view.width - 1, Math.ceil(cam.x + cam.w / tile));
  const y0 = Math.max(0, Math.floor(cam.y));
  const y1 = Math.min(view.height - 1, Math.ceil(cam.y + cam.h / tile));

  // 荧光晶是地表风景灯：先扫一遍可见格收集光源，再算每格黑暗度。
  const lights = collectLights(view, x0, y0, x1, y1, lightOf);
  for (let ty = y0; ty <= y1; ty++) {
    for (let tx = x0; tx <= x1; tx++) drawCell(ctx, view, tx, ty, cam, tex, player, lights, lightOf);
  }

  for (const mob of mobs) drawMob(ctx, mob, cam, tex);
  drawPlayer(ctx, player, cam, swing !== null);
  if (swing) drawSwing(ctx, player, cam, swing);
}

interface Light {
  x: number;
  y: number;
  r: number;
}

function collectLights(view: WorldView, x0: number, y0: number, x1: number, y1: number,
                       lightOf: (id: number) => number): Light[] {
  const lights: Light[] = [];
  for (let ty = y0; ty <= y1; ty++) {
    for (let tx = x0; tx <= x1; tx++) {
      const r = lightOf(view.blocks[ty * view.width + tx]);
      if (r > 0) lights.push({ x: tx + 0.5, y: ty + 0.5, r });
    }
  }
  return lights;
}

function drawCell(
  ctx: CanvasRenderingContext2D,
  view: WorldView,
  tx: number,
  ty: number,
  cam: Camera,
  tex: Textures,
  player: Player,
  lights: Light[],
  lightOf: (id: number) => number,
) {
  const { tile } = cam;
  const sx = Math.round((tx - cam.x) * tile);
  const sy = Math.round((ty - cam.y) * tile);
  const id = view.blocks[ty * view.width + tx];
  const depth = ty - view.surface[tx];
  if (id !== 0) {
    const texture = tex.blocks.get(id);
    if (texture) ctx.drawImage(texture, sx, sy, tile, tile);
    const glow = lightOf(id);
    if (glow > 0) drawGlow(ctx, sx + tile / 2, sy + tile / 2, glow * tile);
  } else if (depth >= 0) {
    ctx.fillStyle = depth < 6 ? '#3a2c22' : depth < 24 ? '#241d1a' : '#15111a';
    ctx.fillRect(sx, sy, tile, tile);
  } else {
    return;
  }
  if (depth <= 0) return;
  // 越深越暗，但玩家和荧光晶身边留一圈光，不然地层断面糊成一片。
  let falloff = Math.max(0.16, Math.min(1, Math.hypot(tx + 0.5 - player.x, ty + 0.5 - player.y) / 9));
  for (const l of lights) {
    falloff = Math.min(falloff, Math.max(0.1, Math.min(1, Math.hypot(tx + 0.5 - l.x, ty + 0.5 - l.y) / l.r)));
  }
  const dark = Math.min(0.66, depth / 44) * falloff;
  ctx.fillStyle = `rgba(5,3,10,${dark.toFixed(3)})`;
  ctx.fillRect(sx, sy, tile, tile);
}

function drawGlow(ctx: CanvasRenderingContext2D, cx: number, cy: number, radius: number) {
  ctx.save();
  ctx.globalCompositeOperation = 'lighter';
  const gradient = ctx.createRadialGradient(cx, cy, 0, cx, cy, radius);
  gradient.addColorStop(0, 'rgba(126,240,255,0.30)');
  gradient.addColorStop(0.4, 'rgba(126,240,255,0.12)');
  gradient.addColorStop(1, 'rgba(126,240,255,0)');
  ctx.fillStyle = gradient;
  ctx.fillRect(cx - radius, cy - radius, radius * 2, radius * 2);
  ctx.restore();
}

function drawSky(ctx: CanvasRenderingContext2D, cam: Camera, view: WorldView) {
  const gradient = ctx.createLinearGradient(0, 0, 0, cam.h);
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

function drawMob(ctx: CanvasRenderingContext2D, mob: Mob, cam: Camera, tex: Textures) {
  const { tile } = cam;
  const w = mob.def.w * tile;
  const h = mob.def.h * tile;
  const bob = Math.sin(mob.phase) * (mob.def.flying ? tile * 0.08 : tile * 0.03);
  const left = Math.round((mob.x - cam.x) * tile - w / 2);
  const top = Math.round((mob.y - cam.y) * tile - h / 2 + bob);
  const sprite = tex.mobs.get(mob.def.key);

  ctx.save();
  if (mob.dying > 0) ctx.globalAlpha = Math.max(0, mob.dying / 0.4);
  if (!sprite) {
    ctx.fillStyle = mob.def.color;
    ctx.fillRect(left, top, Math.round(w), Math.round(h));
  } else if (mob.facing === 1) {
    ctx.drawImage(sprite, left, top, Math.round(w), Math.round(h));
  } else {
    // 贴图只画了朝右的样子，朝左时水平翻转，省掉一套镜像素材。
    ctx.translate(left + Math.round(w), top);
    ctx.scale(-1, 1);
    ctx.drawImage(sprite, 0, 0, Math.round(w), Math.round(h));
  }
  if (mob.hurt > 0) {
    ctx.fillStyle = `rgba(255,90,80,${(mob.hurt / 0.26) * 0.45})`;
    ctx.fillRect(left, top, Math.round(w), Math.round(h));
  }
  ctx.restore();

  if (mob.dying === 0 && mob.hp < mob.def.hp) drawMobBar(ctx, mob, left, top, w);
}

function drawMobBar(ctx: CanvasRenderingContext2D, mob: Mob, left: number, top: number, w: number) {
  const barW = Math.max(22, w);
  const barH = 4;
  const x = left + (w - barW) / 2;
  const y = top - 8;
  ctx.fillStyle = 'rgba(12,14,20,0.8)';
  ctx.fillRect(x, y, barW, barH);
  ctx.fillStyle = '#e0574a';
  ctx.fillRect(x + 1, y + 1, (barW - 2) * Math.max(0, mob.hp / mob.def.hp), barH - 2);
}

function drawPlayer(ctx: CanvasRenderingContext2D, p: Player, cam: Camera, swinging: boolean) {
  const w = PLAYER_TILE_W * cam.tile;
  const h = PLAYER_TILE_H * cam.tile;
  const left = Math.round((p.x - cam.x) * cam.tile - w / 2);
  const top = Math.round((p.y - cam.y) * cam.tile - h / 2);

  ctx.save();
  if (p.hurt > 0) ctx.globalAlpha = 0.55 + Math.sin(p.hurt * 40) * 0.35;
  if (p.onGround) {
    ctx.fillStyle = 'rgba(0,0,0,0.22)';
    ctx.beginPath();
    ctx.ellipse(left + w / 2, top + h - 1, w * 0.52, 3, 0, 0, Math.PI * 2);
    ctx.fill();
  }
  const ops = canvasOps(ctx, left, top, w / PLAYER_GRID_W, h / PLAYER_GRID_H);
  paintPlayer(ops, { walk: p.walk, onGround: p.onGround, moving: Math.abs(p.vx) > 0.2, swinging, facing: p.facing });
  ctx.restore();
}

function drawSwing(ctx: CanvasRenderingContext2D, p: Player, cam: Camera, swing: Swing) {
  const progress = 1 - swing.t;
  const radius = swing.range * cam.tile;
  const cx = (p.x - cam.x) * cam.tile;
  const cy = (p.y - cam.y) * cam.tile;
  const start = swing.facing === 1 ? -Math.PI * 0.42 : Math.PI * 0.58;
  const sweep = (swing.facing === 1 ? 1 : -1) * Math.PI * 0.84 * Math.min(1, progress * 1.6);
  ctx.save();
  ctx.globalAlpha = Math.max(0, swing.t) * 0.85;
  ctx.strokeStyle = 'rgba(255,255,255,0.9)';
  ctx.lineWidth = Math.max(3, cam.tile * 0.14);
  ctx.beginPath();
  ctx.arc(cx, cy, radius * 0.82, start, start + sweep, swing.facing !== 1);
  ctx.stroke();
  ctx.strokeStyle = 'rgba(126,240,255,0.55)';
  ctx.lineWidth = Math.max(2, cam.tile * 0.06);
  ctx.beginPath();
  ctx.arc(cx, cy, radius * 0.66, start, start + sweep, swing.facing !== 1);
  ctx.stroke();
  ctx.restore();
}
