import { defineStore } from 'pinia';
import { computed, ref, shallowRef } from 'vue';
import { api, ApiError } from '../api';
import type { Content, Mutation, Order, WorldSummary, WorldView } from '../types';

// 方块数组有一万五千格，不做深度响应式：原地改值，再用 revision 通知需要重算的组件。
// 渲染循环每帧直接读 blocks，不经过 Vue 的依赖追踪。
export const useGame = defineStore('game', () => {
  const content = ref<Content | null>(null);
  const worlds = ref<WorldSummary[]>([]);
  const world = shallowRef<WorldView | null>(null);
  const revision = ref(0);
  const inventory = ref<Record<string, number>>({});
  const toolTier = ref(0);
  const toolName = ref('徒手');
  const selected = ref<string | null>(null);
  // 物理位置在 Canvas 组件内部逐帧跑，这里存一份节流后的副本，给合成面板判断工作台距离用。
  const playerPos = ref({ x: 0, y: 0 });
  const mode = ref<'mine' | 'place'>('mine');
  const craftOpen = ref(false);
  const postOpen = ref(false);
  const orders = ref<Order[]>([]);
  // 服务端时钟与本地时钟的差：倒计时用服务端时间算，改本机时间也骗不过限时。
  const nowOffset = ref(0);
  // 塌方把玩家顶出来时服务端会回传新坐标，Canvas 监听序号做瞬移。
  const teleport = ref<{ x: number; y: number; n: number } | null>(null);
  const notice = ref<{ text: string; tone: 'ok' | 'warn' } | null>(null);
  const booting = ref(true);
  const fatal = ref('');

  const blockDefs = computed(() => {
    const map = new Map<number, Content['blocks'][number]>();
    for (const def of content.value?.blocks ?? []) map.set(def.id, def);
    return map;
  });

  const toolSpeed = computed(() => content.value?.tools.find((t) => t.tier === toolTier.value)?.speed ?? 1);

  const hotbar = computed(() => {
    void revision.value;
    const items = content.value?.items ?? {};
    return Object.keys(items)
      .filter((key) => (inventory.value[key] ?? 0) > 0)
      .map((key) => ({ key, count: inventory.value[key], def: items[key] }));
  });

  function toast(text: string, tone: 'ok' | 'warn' = 'ok') {
    notice.value = { text, tone };
    window.setTimeout(() => {
      if (notice.value?.text === text) notice.value = null;
    }, 2600);
  }

  function fail(e: unknown) {
    toast(e instanceof ApiError ? e.message : '网络不通，这一步没做成', 'warn');
    return false;
  }

  function apply(mutation: Mutation) {
    const view = world.value;
    if (!view) return;
    for (const change of mutation.changes) view.blocks[change.y * view.width + change.x] = change.id;
    inventory.value = mutation.inventory;
    toolTier.value = mutation.toolTier;
    toolName.value = mutation.toolName;
    if (selected.value && !(inventory.value[selected.value] > 0)) selected.value = null;
    if (mutation.player) {
      playerPos.value = { x: mutation.player.x, y: mutation.player.y };
      teleport.value = { ...mutation.player, n: (teleport.value?.n ?? 0) + 1 };
    }
    if (mutation.orders) orders.value = mutation.orders;
    revision.value++;
    if (mutation.message) toast(mutation.message);
  }

  async function boot() {
    try {
      content.value = await api.content();
      worlds.value = await api.worlds();
    } catch (e) {
      fatal.value = e instanceof ApiError ? e.message : '连不上后端，先确认 Spring Boot 已经跑在 8080';
    } finally {
      booting.value = false;
    }
  }

  async function createWorld(name: string, seed?: number) {
    try {
      const view = await api.create(name, seed);
      worlds.value = await api.worlds();
      enter(view);
      return true;
    } catch (e) {
      return fail(e);
    }
  }

  async function openWorld(id: string) {
    try {
      enter(await api.load(id));
      return true;
    } catch (e) {
      return fail(e);
    }
  }

  function enter(view: WorldView) {
    world.value = view;
    inventory.value = view.inventory;
    toolTier.value = view.toolTier;
    toolName.value = view.toolName;
    selected.value = null;
    mode.value = 'mine';
    craftOpen.value = false;
    postOpen.value = false;
    orders.value = view.orders;
    nowOffset.value = view.now - Date.now();
    playerPos.value = { x: view.player.x, y: view.player.y };
    revision.value++;
  }

  async function deleteWorld(id: string) {
    try {
      await api.remove(id);
      worlds.value = await api.worlds();
      if (world.value?.id === id) leave();
      return true;
    } catch (e) {
      return fail(e);
    }
  }

  function leave() {
    world.value = null;
    inventory.value = {};
    selected.value = null;
    orders.value = [];
    postOpen.value = false;
    craftOpen.value = false;
    revision.value++;
  }

  async function mine(x: number, y: number) {
    if (!world.value) return false;
    try {
      apply(await api.mine(world.value.id, x, y));
      return true;
    } catch (e) {
      return fail(e);
    }
  }

  async function place(x: number, y: number) {
    const view = world.value;
    if (!view) return false;
    if (!selected.value) {
      toast('先在下面背包里点一个要放的方块', 'warn');
      return false;
    }
    try {
      apply(await api.place(view.id, x, y, selected.value));
      return true;
    } catch (e) {
      return fail(e);
    }
  }

  async function craft(recipeId: string) {
    if (!world.value) return false;
    try {
      apply(await api.craft(world.value.id, recipeId));
      return true;
    } catch (e) {
      return fail(e);
    }
  }

  async function syncOrders() {
    if (!world.value) return;
    try {
      const view = await api.orders(world.value.id);
      orders.value = view.orders;
      nowOffset.value = view.now - Date.now();
    } catch {
      // 面板轮询失败不打断游戏，下一轮再试。
    }
  }

  async function deliver(orderId: string) {
    if (!world.value) return false;
    try {
      apply(await api.deliver(world.value.id, orderId));
      return true;
    } catch (e) {
      return fail(e);
    }
  }

  // 位置由客户端物理算出来，这里只负责定期报给服务端存档 + 作为距离判定依据。
  function syncPosition(x: number, y: number) {
    const view = world.value;
    if (!view) return;
    playerPos.value = { x, y };
    void api.move(view.id, x, y).catch(() => {});
  }

  function blockAt(x: number, y: number) {
    const view = world.value;
    if (!view || x < 0 || y < 0 || x >= view.width || y >= view.height) return 10;
    return view.blocks[y * view.width + x];
  }

  return {
    content, worlds, world, revision, inventory, toolTier, toolName, selected, playerPos, mode, craftOpen, notice,
    postOpen, orders, nowOffset, teleport, booting, fatal, blockDefs, toolSpeed, hotbar,
    boot, createWorld, openWorld, deleteWorld, leave, mine, place, craft, syncOrders, deliver, syncPosition, blockAt, toast,
  };
});
