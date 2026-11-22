import type { MobDef, Progression, WeaponDef, WorldView } from '../types';
import { hits, moveBox, roomFor, solid, PLAYER_H, PLAYER_W, type Player } from './physics';

// 怪物全在客户端模拟（和玩家物理同一层）：走位、追击、接触伤害都在这里算，
// 只有「这只怪死了」会上报服务端，由后端按掉落表结算材料与击杀数。
export interface Mob {
  uid: number;
  def: MobDef;
  x: number;
  y: number;
  vx: number;
  vy: number;
  hp: number;
  onGround: boolean;
  facing: 1 | -1;
  hurt: number;
  attack: number;
  think: number;
  dir: number;
  phase: number;
  // >0 播死亡动画，-1 表示动画放完等着被清掉。
  dying: number;
}

export interface MobEvents {
  onPlayerHurt(damage: number, fromX: number): void;
  onKill(def: MobDef): void;
}

export const MAX_MOBS = 14;
const SPAWN_NEAR = 22;
const SPAWN_FAR = 46;
const DESPAWN_AT = 110;
const GRAVITY = 34;
const CONTACT_COOLDOWN = 1.0;

let nextUid = 1;

export function createMob(def: MobDef, x: number, y: number): Mob {
  return {
    uid: nextUid++, def, x, y, vx: 0, vy: 0, hp: def.hp, onGround: false,
    facing: Math.random() < 0.5 ? -1 : 1, hurt: 0, attack: 0,
    think: 0.6 + Math.random() * 2, dir: Math.random() < 0.5 ? -1 : 1,
    phase: Math.random() * 6.28, dying: 0,
  };
}

function pickDef(defs: MobDef[]): MobDef {
  const total = defs.reduce((sum, d) => sum + d.weight, 0);
  if (total <= 0) return defs[0];
  let roll = Math.random() * total;
  for (const def of defs) {
    roll -= def.weight;
    if (roll <= 0) return def;
  }
  return defs[defs.length - 1];
}

// 难度分区：zone = min(zoneCount-1, floor(|x - 出生点| / zoneWidth))，越远越硬。
export function zoneOf(x: number, spawnX: number, rules: Progression) {
  return Math.min(rules.zoneCount - 1, Math.floor(Math.abs(x - spawnX) / rules.zoneWidth));
}

// 巢穴是地图最东端的 lairWidth 列：里面不刷普通怪，只有 Boss。
export function lairStart(view: WorldView, rules: Progression) {
  return view.width - rules.lairWidth;
}

// 本区招牌怪有 signatureChance 的概率出场，否则在已解锁的怪里按 weight 抽一只。
function pickForZone(defs: MobDef[], zone: number, rules: Progression): MobDef {
  const open = defs.filter((d) => !d.boss && d.zone <= zone);
  const signature = open.find((d) => d.zone === zone);
  if (signature && Math.random() < rules.signatureChance) return signature;
  return pickDef(open.length ? open : defs.filter((d) => !d.boss));
}

// 刷怪只挑玩家两侧、离得够远又站得住的列：不凭空出现在脸上，也不卡在方块里。
export function spawnOne(defs: MobDef[], view: WorldView, player: Player,
                         rules: Progression, spawnX: number): Mob | null {
  const zone = zoneOf(player.x, spawnX, rules);
  const lair = lairStart(view, rules);
  for (let attempt = 0; attempt < 12; attempt++) {
    const def = pickForZone(defs, zone, rules);
    const side = Math.random() < 0.5 ? -1 : 1;
    const distance = SPAWN_NEAR + Math.random() * (SPAWN_FAR - SPAWN_NEAR);
    const x = Math.round(player.x + side * distance);
    if (x < 2 || x >= lair) continue;
    const ground = view.surface[x];
    // 飞行怪悬在地表上方，树冠也在那一层；地面怪要能站住，飞行怪只要这块空间是空的。
    const y = def.flying ? ground - 3 - Math.random() * 3 : ground - def.h / 2 - 0.05;
    const free = def.flying
      ? !hits(view, x + 0.5, y, def.w, def.h)
      : roomFor(view, x + 0.5, y, def.w, def.h);
    if (free) return createMob(def, x + 0.5, y);
  }
  return null;
}

// Boss 从巢穴东端往西找一个站得住的位置落地，一次只有一只；玩家走出巢穴由 clearBoss 收回。
export function spawnBoss(defs: MobDef[], view: WorldView, rules: Progression): Mob | null {
  const boss = defs.find((d) => d.boss);
  if (!boss) return null;
  for (let x = view.width - 4; x >= lairStart(view, rules) + 2; x--) {
    const y = view.surface[x] - boss.h / 2 - 0.05;
    if (roomFor(view, x + 0.5, y, boss.w, boss.h)) return createMob(boss, x + 0.5, y);
  }
  return null;
}

export function bossOf(mobs: Mob[]): Mob | null {
  return mobs.find((m) => m.def.boss && m.dying === 0) ?? null;
}

export function clearBoss(mobs: Mob[]) {
  for (let i = mobs.length - 1; i >= 0; i--) {
    if (mobs[i].def.boss) mobs.splice(i, 1);
  }
}

export function stepMobs(mobs: Mob[], view: WorldView, player: Player, dt: number, events: MobEvents) {
  for (const mob of mobs) {
    mob.hurt = Math.max(0, mob.hurt - dt);
    mob.attack = Math.max(0, mob.attack - dt);
    mob.phase += dt * (mob.def.flying ? 6 : 3.2);
    if (mob.dying !== 0) {
      mob.dying = mob.dying - dt <= 0 ? -1 : mob.dying - dt;
      continue;
    }
    const dx = player.x - mob.x;
    const dy = player.y - mob.y;
    const near = Math.hypot(dx, dy) < mob.def.aggro;
    think(mob, view, near, dx, dy, dt);
    // 飞行怪不吃重力，翅膀的上下摆直接叠在 vy 上，碰撞仍走同一套 AABB。
    moveBox(view, mob, mob.def.w, mob.def.h, dt, mob.def.flying ? 0 : GRAVITY);
    if (near && touch(mob, player) && mob.attack <= 0) {
      mob.attack = CONTACT_COOLDOWN;
      events.onPlayerHurt(mob.def.damage, mob.x);
    }
  }
}

function think(mob: Mob, view: WorldView, near: boolean, dx: number, dy: number, dt: number) {
  mob.think -= dt;
  if (!near) {
    if (mob.think <= 0) {
      mob.think = 1.2 + Math.random() * 2.4;
      mob.dir = Math.random() < 0.35 ? 0 : Math.random() < 0.5 ? -1 : 1;
    }
    mob.vx = mob.dir * mob.def.speed * 0.45;
    if (mob.dir !== 0) mob.facing = mob.dir as 1 | -1;
    if (mob.def.flying) mob.vy = Math.sin(mob.phase) * 1.1;
    return;
  }
  const dir = dx >= 0 ? 1 : -1;
  mob.facing = dir as 1 | -1;
  mob.vx = dir * mob.def.speed;
  if (mob.def.flying) {
    mob.vy = Math.max(-3.6, Math.min(3.6, dy * 1.8)) + Math.sin(mob.phase) * 0.8;
  } else if (mob.onGround && (blockedAhead(view, mob) || dy < -1.2)) {
    // 追上玩家之前先要能翻过地形：前面有台阶或者玩家在高处就跳。
    mob.vy = -11.4;
  }
}

function blockedAhead(view: WorldView, mob: Mob) {
  const ahead = mob.x + mob.facing * (mob.def.w / 2 + 0.35);
  const feet = Math.floor(mob.y + mob.def.h / 2 - 0.25);
  return solid(view, Math.floor(ahead), feet);
}

function touch(mob: Mob, player: Player) {
  return Math.abs(mob.x - player.x) < (mob.def.w + PLAYER_W) / 2
    && Math.abs(mob.y - player.y) < (mob.def.h + PLAYER_H) / 2;
}

// 一刀只砍中最近的一只：既符合挥砍手感，也让服务端的击杀上报一次只有一个。
export function swing(mobs: Mob[], player: Player, weapon: WeaponDef, events: MobEvents): Mob | null {
  let best: Mob | null = null;
  let bestDistance = Infinity;
  for (const mob of mobs) {
    if (mob.dying !== 0) continue;
    const dx = mob.x - player.x;
    const dy = mob.y - player.y;
    if (Math.abs(dx) > weapon.range || Math.abs(dy) > 1.6) continue;
    if (Math.abs(dx) > 0.7 && Math.sign(dx) !== player.facing) continue;
    const distance = Math.hypot(dx, dy);
    if (distance < bestDistance) {
      bestDistance = distance;
      best = mob;
    }
  }
  if (!best) return null;
  best.hp -= weapon.damage;
  best.hurt = 0.26;
  best.vx = player.facing * 6.4;
  if (!best.def.flying) best.vy = -3.2;
  if (best.hp <= 0) {
    best.dying = 0.4;
    events.onKill(best.def);
  }
  return best;
}

export function prune(mobs: Mob[], player: Player) {
  for (let i = mobs.length - 1; i >= 0; i--) {
    const mob = mobs[i];
    if (mob.dying === -1 || Math.abs(mob.x - player.x) > DESPAWN_AT) mobs.splice(i, 1);
  }
}
