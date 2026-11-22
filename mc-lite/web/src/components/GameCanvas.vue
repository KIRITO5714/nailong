<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useGame } from '../stores/game';
import { controls, releaseAll } from '../game/controls';
import { buildTextures, type Textures } from '../game/textures';
import { createPlayer, knockback, step, type Player } from '../game/physics';
import { bossOf, clearBoss, lairStart, MAX_MOBS, prune, spawnBoss, spawnOne, stepMobs, swing, zoneOf,
         type Mob, type MobEvents } from '../game/mobs';
import { drawScene, type Camera, type Swing } from '../game/render';

const game = useGame();
const canvasEl = ref<HTMLCanvasElement | null>(null);
const hud = ref({ x: 0, y: 0, hp: 100, mobs: 0, zone: 1, boss: null as { name: string; hp: number; max: number } | null });

let player: Player = createPlayer(240, 30);
let mobs: Mob[] = [];
let tex: Textures | null = null;
let cam: Camera = { x: 0, y: 0, tile: 32, w: 800, h: 600 };
let raf = 0;
let last = 0;
let hudAt = 0;
let syncAt = 0;
let spawnAt = 0;
let cooldown = 0;
let swingAnim: Swing | null = null;
let sent = { x: 0, y: 0, hp: 100 };
let hp = 100;

// 血量在客户端结算（和物理同层），归零就进死亡态；服务端只在 move 时收到最新血量。
const events: MobEvents = {
  onPlayerHurt(damage, fromX) {
    if (game.dead) return;
    hp = Math.max(0, hp - damage);
    knockback(player, fromX, 6.5);
    if (hp <= 0) {
      game.dead = true;
      game.syncPlayer(player.x, player.y, 0);
    }
  },
  onKill(def) {
    void game.reportKill(def.key);
  },
};

function resize() {
  const el = canvasEl.value;
  if (!el || !el.parentElement) return;
  const dpr = Math.min(2, window.devicePixelRatio || 1);
  const w = el.parentElement.clientWidth;
  const h = el.parentElement.clientHeight;
  el.width = Math.round(w * dpr);
  el.height = Math.round(h * dpr);
  el.style.width = `${w}px`;
  el.style.height = `${h}px`;
  el.getContext('2d')!.setTransform(dpr, 0, 0, dpr, 0, 0);
  cam.w = w;
  cam.h = h;
  cam.tile = Math.max(20, Math.min(48, Math.round(w / (w < 720 ? 12 : 24))));
}

// 挥砍冷却取当前武器的 cooldown，比服务端限频更严，所以上报永远不会被拒。
function trySwing(dt: number) {
  cooldown = Math.max(0, cooldown - dt);
  if (swingAnim) {
    swingAnim.t -= dt * 4;
    if (swingAnim.t <= 0) swingAnim = null;
  }
  if (!controls.attack) return;
  controls.attack = false;
  const weapon = game.weapon;
  if (cooldown > 0 || game.dead || !weapon) return;
  cooldown = weapon.cooldown;
  swingAnim = { t: 1, facing: player.facing, range: weapon.range };
  swing(mobs, player, weapon, events);
}

function updateSpawns(now: number) {
  if (now - spawnAt < 900 || mobs.length >= MAX_MOBS) return;
  spawnAt = now;
  const born = spawnOne(game.mobDefs, game.world!, player, game.progression, game.spawnColumn);
  if (born) mobs.push(born);
}

// Boss 巢穴：走进最东端那几十列才生成，一次一只；走出来就收回（不算击杀），通关之后巢穴是空的。
function updateBoss() {
  const view = game.world!;
  if (player.x < lairStart(view, game.progression)) {
    clearBoss(mobs);
    return;
  }
  if (game.cleared || mobs.some((m) => m.def.boss)) return;
  const boss = spawnBoss(game.mobDefs, view, game.progression);
  if (!boss) return;
  mobs.push(boss);
  game.toast(`${boss.def.name} 从巢穴深处站起来了`, 'warn');
}

function updateCamera() {
  const view = game.world!;
  const vw = cam.w / cam.tile;
  const vh = cam.h / cam.tile;
  const clamp = (v: number, max: number) => Math.max(0, Math.min(max, v));
  cam.x = view.width <= vw ? (view.width - vw) / 2 : clamp(player.x - vw / 2, view.width - vw);
  cam.y = view.height <= vh ? (view.height - vh) / 2 : clamp(player.y - vh / 2, view.height - vh);
}

function loop(now: number) {
  raf = requestAnimationFrame(loop);
  const view = game.world;
  const el = canvasEl.value;
  if (!view || !el || !tex) {
    last = now;
    return;
  }
  const dt = Math.min(0.05, (now - last) / 1000 || 0);
  last = now;
  if (game.dead) {
    cooldown = 0;
    controls.attack = false;
  } else {
    step(player, controls, view, dt);
    trySwing(dt);
    stepMobs(mobs, view, player, dt, events);
    prune(mobs, player);
    updateBoss();
    updateSpawns(now);
  }
  updateCamera();
  drawScene(el.getContext('2d')!, view, player, cam, tex, mobs, swingAnim,
    (id) => game.blockDefs.get(id)?.light ?? 0);
  if (now - hudAt > 150) {
    hudAt = now;
    const gx = Math.max(0, Math.min(view.width - 1, Math.floor(player.x)));
    const boss = bossOf(mobs);
    hud.value = {
      x: gx,
      y: Math.floor(player.y),
      hp,
      mobs: mobs.filter((m) => m.dying === 0).length,
      zone: zoneOf(player.x, game.spawnColumn, game.progression) + 1,
      boss: boss ? { name: boss.def.name, hp: Math.max(0, boss.hp), max: boss.def.hp } : null,
    };
  }
  if (now - syncAt > 700) {
    syncAt = now;
    if (Math.hypot(player.x - sent.x, player.y - sent.y) > 0.05 || sent.hp !== hp) {
      sent = { x: player.x, y: player.y, hp };
      game.syncPlayer(player.x, player.y, hp);
    }
  }
}

function onPointerDown(e: PointerEvent) {
  // 捕获只是增强：合成指针事件（自动化测试）没有活动指针，这里会抛 NotFoundError，不该挡住挥砍。
  try {
    canvasEl.value?.setPointerCapture(e.pointerId);
  } catch {
    // 忽略：没有捕获也照样能砍。
  }
  controls.attack = true;
}

function onKey(e: KeyboardEvent, down: boolean) {
  if ((e.target as HTMLElement | null)?.tagName === 'INPUT') return;
  const k = e.key.toLowerCase();
  if (k === 'arrowleft' || k === 'a') controls.left = down;
  else if (k === 'arrowright' || k === 'd') controls.right = down;
  else if (k === 'arrowup' || k === 'w' || k === ' ') controls.jump = down;
  else if (k === 'j' || k === 'k') controls.attack = down;
  else if (down && k === 'e') {
    game.craftOpen = !game.craftOpen;
    if (game.craftOpen) game.bountyOpen = false;
  } else if (down && k === 'f') {
    game.bountyOpen = !game.bountyOpen;
    if (game.bountyOpen) game.craftOpen = false;
  } else if (down && k === 'escape') {
    game.craftOpen = false;
    game.bountyOpen = false;
  } else return;
  e.preventDefault();
}

const keyDown = (e: KeyboardEvent) => onKey(e, true);
const keyUp = (e: KeyboardEvent) => onKey(e, false);

function spawnPoint() {
  const view = game.world!;
  const x = Math.floor(view.width / 2);
  return { x: x + 0.5, y: view.surface[x] - 0.9 };
}

function respawn() {
  const point = spawnPoint();
  player = createPlayer(point.x, point.y);
  hp = 100;
  mobs = [];
  sent = { ...point, hp: 100 };
  game.respawn();
  game.syncPlayer(point.x, point.y, 100);
}

function reset() {
  const view = game.world;
  if (!view || !game.content) return;
  player = createPlayer(view.player.x, view.player.y);
  hp = view.hp;
  mobs = [];
  cooldown = 0;
  swingAnim = null;
  sent = { x: player.x, y: player.y, hp };
  tex = buildTextures(game.content);
  resize();
}

let observer: ResizeObserver | null = null;

watch(() => game.world?.id, reset, { immediate: true });
// 读档回来时血量可能被服务端夹过（比如旧存档 null 按满血），这里跟一次。
watch(() => game.hp, (value) => {
  if (!game.dead) hp = value;
});

onMounted(() => {
  resize();
  observer = new ResizeObserver(resize);
  if (canvasEl.value?.parentElement) observer.observe(canvasEl.value.parentElement);
  window.addEventListener('keydown', keyDown);
  window.addEventListener('keyup', keyUp);
  window.addEventListener('blur', releaseAll);
  last = performance.now();
  raf = requestAnimationFrame(loop);
});

onBeforeUnmount(() => {
  cancelAnimationFrame(raf);
  observer?.disconnect();
  window.removeEventListener('keydown', keyDown);
  window.removeEventListener('keyup', keyUp);
  window.removeEventListener('blur', releaseAll);
  releaseAll();
  game.syncPlayer(player.x, player.y, hp);
});
</script>

<template>
  <div class="stage">
    <canvas ref="canvasEl" class="stage-canvas" @pointerdown="onPointerDown" @contextmenu.prevent />
    <div class="stage-hud">
      <span class="stage-hp">
        <i :style="{ width: `${Math.max(0, Math.min(100, hud.hp))}%` }" />
        <b>{{ Math.round(hud.hp) }} / 100</b>
      </span>
      <span>x{{ hud.x }} · y{{ hud.y }}</span>
      <span class="stage-zone">区域 {{ hud.zone }} / {{ game.progression.zoneCount }}</span>
      <span>附近怪物 {{ hud.mobs }}</span>
    </div>
    <div v-if="hud.boss" class="boss-bar">
      <b>{{ hud.boss.name }}</b>
      <span class="boss-gauge"><i :style="{ width: `${(hud.boss.hp / hud.boss.max) * 100}%` }" /></span>
      <em>{{ Math.ceil(hud.boss.hp) }} / {{ hud.boss.max }}</em>
    </div>
    <div class="stage-tip">点画面或按 J 挥砍 · A/D 移动 · W 跳 · E 锻造 · F 悬赏板 · 地图最东端是 Boss 巢穴</div>
    <div v-if="game.dead" class="dead">
      <p class="dead-title">你被怪物打倒了</p>
      <p class="dead-note">材料和悬赏进度都还在存档里，只丢这一条命。</p>
      <button class="btn btn-primary btn-big" @click="respawn">在出生点重生</button>
    </div>
  </div>
</template>
