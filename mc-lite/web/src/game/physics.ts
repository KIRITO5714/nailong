// 客户端物理：重力 + AABB 碰撞。服务端只记录位置与血量，不参与逐帧模拟。
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
  hurt: number;
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
  return { x, y, vx: 0, vy: 0, onGround: false, facing: 1, walk: 0, hurt: 0 };
}

// 树干和树叶是纯风景：世界不能挖，挡路的树会把地图永久截断，所以内容表把它们标成可穿过。
let passable = new Set<number>();

export function setPassableBlocks(ids: Iterable<number>) {
  passable = new Set(ids);
}

export function solid(world: SolidWorld, x: number, y: number) {
  if (x < 0 || y < 0 || x >= world.width || y >= world.height) return true;
  const id = world.blocks[y * world.width + x];
  return id !== 0 && !passable.has(id);
}

// 通用 AABB 推进：玩家和地面怪共用同一套碰撞，省掉两份「穿墙」bug。
export function moveBox(
  world: SolidWorld,
  box: { x: number; y: number; vx: number; vy: number; onGround: boolean },
  w: number,
  h: number,
  dt: number,
  gravity: number,
) {
  box.vy = Math.min(MAX_FALL, box.vy + gravity * dt);
  const distance = Math.max(Math.abs(box.vx * dt), Math.abs(box.vy * dt));
  const steps = Math.max(1, Math.ceil(distance / 0.2));
  const sx = (box.vx * dt) / steps;
  const sy = (box.vy * dt) / steps;
  box.onGround = false;
  for (let i = 0; i < steps; i++) {
    box.x += sx;
    if (hits(world, box.x, box.y, w, h)) {
      box.x -= sx;
      box.vx = 0;
    }
    box.y += sy;
    if (hits(world, box.x, box.y, w, h)) {
      box.y -= sy;
      if (sy > 0) box.onGround = true;
      box.vy = 0;
    }
  }
  box.x = Math.max(w / 2, Math.min(world.width - w / 2, box.x));
  box.y = Math.max(h / 2, Math.min(world.height - h / 2, box.y));
}

export function hits(world: SolidWorld, x: number, y: number, w: number, h: number) {
  const x0 = Math.floor(x - w / 2);
  const x1 = Math.floor(x + w / 2 - 1e-6);
  const y0 = Math.floor(y - h / 2);
  const y1 = Math.floor(y + h / 2 - 1e-6);
  for (let ty = y0; ty <= y1; ty++) {
    for (let tx = x0; tx <= x1; tx++) {
      if (solid(world, tx, ty)) return true;
    }
  }
  return false;
}

// 这一格能不能站：给刷怪器用，怪不能刷在方块里或半空中。
export function roomFor(world: SolidWorld, x: number, y: number, w: number, h: number) {
  return !hits(world, x, y, w, h) && hits(world, x, y + h / 2 + 0.2, w, 0.2);
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
  moveBox(world, p, PLAYER_W, PLAYER_H, dt, GRAVITY);
  p.walk += Math.abs(p.vx) * dt;
  p.hurt = Math.max(0, p.hurt - dt);
}

export function knockback(p: Player, fromX: number, power: number) {
  const dir = p.x >= fromX ? 1 : -1;
  p.vx = dir * power;
  p.vy = -power * 0.7;
  p.onGround = false;
  p.hurt = 0.32;
}
