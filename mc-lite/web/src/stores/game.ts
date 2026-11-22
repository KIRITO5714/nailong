import { defineStore } from 'pinia';
import { computed, ref, shallowRef } from 'vue';
import { api, ApiError } from '../api';
import { setPassableBlocks } from '../game/physics';
import type { Bounty, Content, Mutation, Progression, WorldSummary, WorldView } from '../types';

// 方块数组有四万六千格，不做深度响应式：原地改值，再用 revision 通知需要重算的组件。
// 渲染循环每帧直接读 blocks 和怪物数组，不经过 Vue 的依赖追踪。
export const useGame = defineStore('game', () => {
  const content = ref<Content | null>(null);
  const worlds = ref<WorldSummary[]>([]);
  const world = shallowRef<WorldView | null>(null);
  const revision = ref(0);
  const inventory = ref<Record<string, number>>({});
  const kills = ref<Record<string, number>>({});
  const hp = ref(100);
  const weaponTier = ref(0);
  const weaponName = ref('徒手');
  const craftOpen = ref(false);
  const bountyOpen = ref(false);
  const bounties = ref<Bounty[]>([]);
  // 服务端时钟与本地时钟的差：悬赏倒计时用服务端时间算，改本机时间也骗不过限时。
  const nowOffset = ref(0);
  // 死亡由客户端判定（血量归零），重生点用出生列，服务端只在 move 时收到新血量。
  const dead = ref(false);
  // 通关：cleared 是存档里的持久状态（顶栏徽章用），victory 只在「刚打死 Boss」那一次为 true。
  const cleared = ref(false);
  const victory = ref(false);
  const notice = ref<{ text: string; tone: 'ok' | 'warn' } | null>(null);
  const booting = ref(true);
  const fatal = ref('');
  const pending = ref(0);

  const blockDefs = computed(() => {
    const map = new Map<number, Content['blocks'][number]>();
    for (const def of content.value?.blocks ?? []) map.set(def.id, def);
    return map;
  });

  const weapon = computed(() => {
    const list = content.value?.weapons ?? [];
    return list.find((w) => w.tier === weaponTier.value) ?? list[0];
  });

  const mobDefs = computed(() => content.value?.mobs ?? []);

  // 分区规则全部来自内容表，客户端只按玩家离出生点的距离算当前区。
  const progression = computed<Progression>(() => content.value?.progression
    ?? { zoneWidth: 15, zoneCount: 14, lairWidth: 36, signatureChance: 0.6 });

  const spawnColumn = computed(() => Math.floor((world.value?.width ?? 0) / 2));

  const bossDef = computed(() => mobDefs.value.find((m) => m.boss) ?? null);

  // 背包按「武器在前、材料在后」排，热键 1-9 才有稳定含义（服务端返回的是插入序）。
  const hotbar = computed(() => {
    void revision.value;
    const items = content.value?.items ?? {};
    return Object.keys(items)
      .filter((key) => (inventory.value[key] ?? 0) > 0)
      .sort((a, b) => (items[b].weapon ?? 0) - (items[a].weapon ?? 0))
      .map((key) => ({ key, count: inventory.value[key], def: items[key] }));
  });

  const totalKills = computed(() => Object.values(kills.value).reduce((sum, n) => sum + n, 0));
  const kindsKilled = computed(() => Object.keys(kills.value).length);

  function toast(text: string, tone: 'ok' | 'warn' = 'ok') {
    notice.value = { text, tone };
    window.setTimeout(() => {
      if (notice.value?.text === text) notice.value = null;
    }, 2600);
  }

  function fail(e: unknown) {
    // 挥砍太快是客户端冷却没对齐，静默丢掉即可，不该刷屏。
    if (e instanceof ApiError && e.code === 'LOOT_TOO_FAST') return false;
    toast(e instanceof ApiError ? e.message : '网络不通，这一步没做成', 'warn');
    return false;
  }

  function apply(mutation: Mutation) {
    inventory.value = mutation.inventory;
    kills.value = mutation.kills;
    weaponTier.value = mutation.weaponTier;
    weaponName.value = mutation.weaponName;
    if (mutation.bounties) bounties.value = mutation.bounties;
    // 通关界面只在「从没通关变成通关」那一次弹，之后读档只显示顶栏徽章。
    if (mutation.cleared && !cleared.value) victory.value = true;
    cleared.value = mutation.cleared;
    revision.value++;
    if (mutation.message) toast(mutation.message);
  }

  async function boot() {
    try {
      content.value = await api.content();
      setPassableBlocks(content.value.blocks.filter((b) => b.passable).map((b) => b.id));
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
    kills.value = view.kills;
    hp.value = view.hp;
    weaponTier.value = view.weaponTier;
    weaponName.value = view.weaponName;
    craftOpen.value = false;
    bountyOpen.value = false;
    bounties.value = view.bounties;
    nowOffset.value = view.now - Date.now();
    dead.value = view.hp <= 0;
    cleared.value = view.cleared;
    victory.value = false;
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
    kills.value = {};
    bounties.value = [];
    hp.value = 100;
    dead.value = false;
    cleared.value = false;
    victory.value = false;
    craftOpen.value = false;
    bountyOpen.value = false;
    revision.value++;
  }

  // 客户端已经算出这只怪死了，服务端补掉落与击杀数；并发上限用来挡住连点。
  async function reportKill(mobKey: string) {
    const view = world.value;
    if (!view || pending.value >= 3) return;
    pending.value++;
    try {
      apply(await api.loot(view.id, mobKey));
    } catch (e) {
      fail(e);
    } finally {
      pending.value--;
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

  async function syncBounties() {
    if (!world.value) return;
    try {
      const view = await api.bounties(world.value.id);
      bounties.value = view.bounties;
      nowOffset.value = view.now - Date.now();
    } catch {
      // 面板轮询失败不打断战斗，下一轮再试。
    }
  }

  async function deliver(bountyId: string) {
    if (!world.value) return false;
    try {
      apply(await api.deliver(world.value.id, bountyId));
      return true;
    } catch (e) {
      return fail(e);
    }
  }

  // 位置与血量由客户端物理算出来，这里定期报给服务端存档。
  function syncPlayer(x: number, y: number, currentHp: number) {
    const view = world.value;
    if (!view) return;
    hp.value = currentHp;
    void api.move(view.id, x, y, currentHp).catch(() => {});
  }

  function respawn() {
    dead.value = false;
    hp.value = 100;
    revision.value++;
  }

  function blockAt(x: number, y: number) {
    const view = world.value;
    if (!view || x < 0 || y < 0 || x >= view.width || y >= view.height) return 10;
    return view.blocks[y * view.width + x];
  }

  return {
    content, worlds, world, revision, inventory, kills, hp, weaponTier, weaponName, craftOpen, bountyOpen,
    bounties, nowOffset, dead, cleared, victory, notice, booting, fatal, blockDefs, weapon, mobDefs, hotbar,
    progression, spawnColumn, bossDef, totalKills, kindsKilled,
    boot, createWorld, openWorld, deleteWorld, leave, reportKill, craft, syncBounties, deliver,
    syncPlayer, respawn, blockAt, toast,
  };
});
