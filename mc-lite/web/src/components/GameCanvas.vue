<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useGame } from '../stores/game';
import { controls, releaseAll } from '../game/controls';
import { buildTextures, type Textures } from '../game/textures';
import { createPlayer, step, type Player } from '../game/physics';
import { drawScene, type Camera, type Target } from '../game/render';

const game = useGame();
const canvasEl = ref<HTMLCanvasElement | null>(null);
const hud = ref({ x: 0, y: 0, depth: 0 });

let player: Player = createPlayer(80, 30);
let tex: Textures | null = null;
let cam: Camera = { x: 0, y: 0, tile: 32, w: 800, h: 600 };
let raf = 0;
let last = 0;
let hudAt = 0;
let syncAt = 0;
let sent = { x: 0, y: 0 };
let target: Target | null = null;
let progress = 0;
let mining = false;
let lastPlaced = '';
const pointer = { down: false, place: false, cx: 0, cy: 0 };

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

function tileAt(cx: number, cy: number) {
  const rect = canvasEl.value!.getBoundingClientRect();
  return {
    x: Math.floor(cam.x + (cx - rect.left) / cam.tile),
    y: Math.floor(cam.y + (cy - rect.top) / cam.tile),
  };
}

// 客户端先算一遍能不能挖，是为了立刻给出红框/灰框反馈；真正的判定仍在服务端。
function updateTarget(dt: number) {
  const view = game.world;
  if (!view || !pointer.down) {
    target = null;
    progress = 0;
    return;
  }
  const { x, y } = tileAt(pointer.cx, pointer.cy);
  if (x < 0 || y < 0 || x >= view.width || y >= view.height) {
    target = null;
    progress = 0;
    return;
  }
  const id = view.blocks[y * view.width + x];
  const far = Math.hypot(x + 0.5 - player.x, y + 0.5 - player.y) > view.reach;
  if (pointer.place) {
    target = { x, y, progress: 0, state: far ? 'far' : id === 0 ? 'place' : 'blocked' };
    const key = `${x},${y}`;
    if (!far && id === 0 && key !== lastPlaced) {
      lastPlaced = key;
      void game.place(x, y);
    }
    return;
  }
  const def = game.blockDefs.get(id);
  if (id === 0 || !def) {
    target = null;
    progress = 0;
    return;
  }
  const state: Target['state'] = far ? 'far'
    : game.toolTier < def.tier ? 'weak'
    : def.hardness <= 0 ? 'blocked' : 'ok';
  if (!target || target.x !== x || target.y !== y || state !== 'ok') progress = 0;
  target = { x, y, progress, state };
  if (state !== 'ok' || mining) return;
  progress += (dt * 1000) / (def.hardness / game.toolSpeed);
  target.progress = Math.min(1, progress);
  if (progress >= 1) {
    mining = true;
    progress = 0;
    void game.mine(x, y).finally(() => {
      mining = false;
    });
  }
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
  step(player, controls, view, dt);
  updateTarget(dt);
  updateCamera();
  drawScene(el.getContext('2d')!, view, player, cam, tex, target);
  if (now - hudAt > 150) {
    hudAt = now;
    const gx = Math.max(0, Math.min(view.width - 1, Math.floor(player.x)));
    hud.value = { x: gx, y: Math.floor(player.y), depth: Math.max(0, Math.floor(player.y) - view.surface[gx]) };
  }
  if (now - syncAt > 700) {
    syncAt = now;
    if (Math.hypot(player.x - sent.x, player.y - sent.y) > 0.05) {
      sent = { x: player.x, y: player.y };
      game.syncPosition(player.x, player.y);
    }
  }
}

function onPointerDown(e: PointerEvent) {
  // 捕获只是增强：合成指针事件（自动化测试）没有活动指针，这里会抛 NotFoundError，不该挡住挖掘。
  try {
    canvasEl.value?.setPointerCapture(e.pointerId);
  } catch {
    // 忽略：没有捕获也照样能挖，只是指针移出画布后不再跟踪。
  }
  pointer.down = true;
  pointer.place = e.button === 2 || game.mode === 'place';
  pointer.cx = e.clientX;
  pointer.cy = e.clientY;
  lastPlaced = '';
}

function onPointerMove(e: PointerEvent) {
  pointer.cx = e.clientX;
  pointer.cy = e.clientY;
}

function onPointerUp() {
  pointer.down = false;
  target = null;
  progress = 0;
}

function onKey(e: KeyboardEvent, down: boolean) {
  if ((e.target as HTMLElement | null)?.tagName === 'INPUT') return;
  const k = e.key.toLowerCase();
  if (k === 'arrowleft' || k === 'a') controls.left = down;
  else if (k === 'arrowright' || k === 'd') controls.right = down;
  else if (k === 'arrowup' || k === 'w' || k === ' ') controls.jump = down;
  else if (down && k === 'e') game.craftOpen = !game.craftOpen;
  else if (down && k === 'q') game.mode = game.mode === 'mine' ? 'place' : 'mine';
  else if (down && k === 'escape') game.craftOpen = false;
  else if (down && k >= '1' && k <= '9') {
    const item = game.hotbar[Number(k) - 1];
    if (item) game.selected = item.key;
  } else return;
  e.preventDefault();
}

const keyDown = (e: KeyboardEvent) => onKey(e, true);
const keyUp = (e: KeyboardEvent) => onKey(e, false);

function reset() {
  const view = game.world;
  if (!view || !game.content) return;
  player = createPlayer(view.player.x, view.player.y);
  sent = { x: player.x, y: player.y };
  tex = buildTextures(game.content);
  target = null;
  progress = 0;
  resize();
}

let observer: ResizeObserver | null = null;

watch(() => game.world?.id, reset, { immediate: true });

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
  game.syncPosition(player.x, player.y);
});
</script>

<template>
  <div class="stage">
    <canvas
      ref="canvasEl"
      class="stage-canvas"
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointercancel="onPointerUp"
      @contextmenu.prevent
    />
    <div class="stage-hud">
      <span>深度 {{ hud.depth }}</span>
      <span>x{{ hud.x }} · y{{ hud.y }}</span>
      <span class="stage-mode">{{ game.mode === 'mine' ? '挖掘模式' : '放置模式' }}</span>
    </div>
    <div class="stage-tip">
      {{ game.mode === 'mine' ? '按住方块挖 · 右键或 Q 切到放置' : '点空气格放下选中的方块 · Q 切回挖掘' }}
    </div>
  </div>
</template>
