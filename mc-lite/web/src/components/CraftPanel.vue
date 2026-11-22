<script setup lang="ts">
import { computed } from 'vue';
import { useGame } from '../stores/game';
import { itemIcons } from '../game/icons';
import type { RecipeDef } from '../types';

const game = useGame();
const icons = computed(() => (game.content ? itemIcons(game.content) : {}));

function missing(recipe: RecipeDef) {
  return Object.entries(recipe.in).filter(([key, need]) => (game.inventory[key] ?? 0) < need);
}

function outKey(recipe: RecipeDef) {
  return Object.keys(recipe.out)[0];
}

// 这把剑的属性直接从内容表读，面板不写死任何数值。
function statsOf(recipe: RecipeDef) {
  const tier = game.content?.items[outKey(recipe)]?.weapon ?? 0;
  const weapon = game.content?.weapons.find((w) => w.tier === tier);
  if (!weapon) return '';
  return `伤害 ${weapon.damage} · 距离 ${weapon.range.toFixed(1)} · 冷却 ${weapon.cooldown.toFixed(2)}s`;
}

function nameOf(key: string) {
  return game.content?.items[key]?.name ?? key;
}

function owned(recipe: RecipeDef) {
  return (game.inventory[outKey(recipe)] ?? 0) > 0;
}
</script>

<template>
  <aside class="craft">
    <header class="craft-head">
      <h2>锻造</h2>
      <span class="craft-bench on">现在用的是 {{ game.weaponName }}（T{{ game.weaponTier }}）</span>
      <button class="btn btn-ghost" @click="game.craftOpen = false">关闭 (E)</button>
    </header>
    <ul class="craft-list">
      <li v-for="recipe in game.content?.recipes ?? []" :key="recipe.id" class="craft-item">
        <img class="craft-icon" :src="icons[outKey(recipe)]" :alt="recipe.name" draggable="false" />
        <div class="craft-body">
          <div class="craft-name">
            {{ recipe.name }}
            <em v-if="owned(recipe)">已在背包</em>
          </div>
          <div class="craft-cost">
            <span v-for="(need, key) in recipe.in" :key="key" :class="{ short: (game.inventory[key] ?? 0) < need }">
              {{ nameOf(key) }} {{ game.inventory[key] ?? 0 }}/{{ need }}
            </span>
          </div>
          <div class="craft-out">{{ statsOf(recipe) }}</div>
        </div>
        <button class="btn btn-primary" :disabled="missing(recipe).length > 0" @click="game.craft(recipe.id)">
          {{ missing(recipe).length ? '材料不够' : '打造' }}
        </button>
      </li>
    </ul>
    <p class="craft-note">
      材料全部来自怪物掉落：史莱姆掉黏液、僵尸掉兽皮、骷髅掉骨棒、蝙蝠掉翼膜、岩魔掉晶核。
      武器等级自动取背包里最好的一把剑，不用手动装备，也没有耐久。
    </p>
  </aside>
</template>
