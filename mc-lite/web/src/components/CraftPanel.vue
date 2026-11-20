<script setup lang="ts">
import { computed } from 'vue';
import { useGame } from '../stores/game';
import { itemIcons } from '../game/icons';
import type { RecipeDef } from '../types';

const game = useGame();
const icons = computed(() => (game.content ? itemIcons(game.content) : {}));

const benchId = computed(() => game.content?.blocks.find((b) => b.key === 'workbench')?.id ?? 12);

// 和后端同一套判定：以玩家为中心、reach 为半径扫一圈有没有工作台。
const nearBench = computed(() => {
  void game.revision;
  const view = game.world;
  if (!view) return false;
  const r = Math.ceil(view.reach);
  const px = Math.floor(game.playerPos.x);
  const py = Math.floor(game.playerPos.y);
  for (let y = py - r; y <= py + r; y++) {
    for (let x = px - r; x <= px + r; x++) {
      if (x < 0 || y < 0 || x >= view.width || y >= view.height) continue;
      if (view.blocks[y * view.width + x] === benchId.value) return true;
    }
  }
  return false;
});

function missing(recipe: RecipeDef) {
  return Object.entries(recipe.in)
    .filter(([key, need]) => (game.inventory[key] ?? 0) < need)
    .map(([key, need]) => ({ key, need, have: game.inventory[key] ?? 0 }));
}

function blocked(recipe: RecipeDef) {
  if (missing(recipe).length) return '材料不够';
  if (recipe.needsWorkbench && !nearBench.value) return '要站在工作台旁边';
  return '';
}

function nameOf(key: string) {
  return game.content?.items[key]?.name ?? key;
}
</script>

<template>
  <aside class="craft">
    <header class="craft-head">
      <h2>合成</h2>
      <span class="craft-bench" :class="{ on: nearBench }">{{ nearBench ? '工作台就在旁边' : '附近没有工作台' }}</span>
      <button class="btn btn-ghost" @click="game.craftOpen = false">关闭 (E)</button>
    </header>
    <ul class="craft-list">
      <li v-for="recipe in game.content?.recipes ?? []" :key="recipe.id" class="craft-item">
        <img class="craft-icon" :src="icons[Object.keys(recipe.out)[0]]" :alt="recipe.name" draggable="false" />
        <div class="craft-body">
          <div class="craft-name">
            {{ recipe.name }}
            <em v-if="recipe.needsWorkbench">需要工作台</em>
          </div>
          <div class="craft-cost">
            <span v-for="(need, key) in recipe.in" :key="key" :class="{ short: (game.inventory[key] ?? 0) < need }">
              {{ nameOf(key) }} {{ game.inventory[key] ?? 0 }}/{{ need }}
            </span>
          </div>
          <div class="craft-out">
            产出：
            <span v-for="(count, key) in recipe.out" :key="key">{{ nameOf(key) }} ×{{ count }}</span>
          </div>
        </div>
        <button class="btn btn-primary" :disabled="!!blocked(recipe)" @click="game.craft(recipe.id)">
          {{ blocked(recipe) || '合成' }}
        </button>
      </li>
    </ul>
    <p class="craft-note">
      工具等级自动取背包里最高级的镐：徒手 → 木镐(T1) → 石镐(T2) → 铁镐(T3) → 钻石镐(T4)。
      等级不够的矿挖不动，框会变红。
    </p>
  </aside>
</template>
