// 客户端物理：重力 + AABB 碰撞。服务端只记录位置，不参与逐帧模拟。
export const PLAYER_W = 0.6;
export const PLAYER_H = 1.8;

const SPEED = 6.2;
const GRAVITY = 34;
const JUMP = 11.4;
const MAX_FALL = 32;

export interface Player {
  x: number;
  y: number;
  vx: number;
  vy: number;
  onGround: boolean;
  facing: 1 | -1;
  walk: number;
}

export interface Input {
  left: boolean;
  right: boolean;
  jump: boolean;
}

export interface SolidWorld {
  width: number;
  height: number;
  blocks: number[];
}

export function createPlayer(x: number, y: number): Player {
  return { x, y, vx: 0, vy: 0, onGround: false, facing: 1, walk: 0 };
}

function solid(world: SolidWorld, x: number, y: number) {
  if (x < 0 || y < 0 || x >= world.width || y >= world.height) return true;
  return world.blocks[y * world.width + x] !== 0;
}

function hits(world: SolidWorld, x: number, y: number) {
  const x0 = Math.floor(x - PLAYER_W / 2);
  const x1 = Math.floor(x + PLAYER_W / 2 - 1e-6);
  const y0 = Math.floor(y - PLAYER_H / 2);
  const y1 = Math.floor(y + PLAYER_H / 2 - 1e-6);
  for (let ty = y0; ty <= y1; ty++) {
    for (let tx = x0; tx <= x1; tx++) {
      if (solid(world, tx, ty)) return true;
    }
  }
  return false;
}

export function step(p: Player, input: Input, world: SolidWorld, dt: number) {
  const dir = (input.right ? 1 : 0) - (input.left ? 1 : 0);
  if (dir !== 0) p.facing = dir as 1 | -1;
  const target = dir * SPEED;
  const accel = (p.onGround ? 70 : 34) * dt;
  p.vx += Math.max(-accel, Math.min(accel, target - p.vx));
  if (input.jump && p.onGround) {
    p.vy = -JUMP;
    p.onGround = false;
  }
  p.vy = Math.min(MAX_FALL, p.vy + GRAVITY * dt);

  // 分成小步推进，避免高速时直接穿过一格厚的墙。
  const distance = Math.max(Math.abs(p.vx * dt), Math.abs(p.vy * dt));
  const steps = Math.max(1, Math.ceil(distance / 0.2));
  const sx = (p.vx * dt) / steps;
  const sy = (p.vy * dt) / steps;
  p.onGround = false;
  for (let i = 0; i < steps; i++) {
    p.x += sx;
    if (hits(world, p.x, p.y)) {
      p.x -= sx;
      p.vx = 0;
    }
    p.y += sy;
    if (hits(world, p.x, p.y)) {
      p.y -= sy;
      if (sy > 0) p.onGround = true;
      p.vy = 0;
    }
  }
  p.x = Math.max(PLAYER_W / 2, Math.min(world.width - PLAYER_W / 2, p.x));
  p.y = Math.max(PLAYER_H / 2, Math.min(world.height - PLAYER_H / 2, p.y));
  p.walk += Math.abs(p.vx) * dt;
}
