<script setup lang="ts">
import { computed } from 'vue';
import { useGame } from '../stores/game';
import { itemIcons } from '../game/icons';
import type { ItemDef } from '../types';

const game = useGame();
const icons = computed(() => (game.content ? itemIcons(game.content) : {}));

function pick(key: string, def: ItemDef) {
  game.selected = key;
  // 镐这类工具不能放置，点它只是看看，自动装备的等级在顶栏显示。
  game.mode = def.place !== undefined ? 'place' : 'mine';
}
</script>

<template>
  <div class="hotbar">
    <button
      v-for="(slot, i) in game.hotbar"
      :key="slot.key"
      class="slot"
      :class="{ active: game.selected === slot.key }"
      :title="slot.def.name"
      @click="pick(slot.key, slot.def)"
    >
      <img :src="icons[slot.key]" :alt="slot.def.name" draggable="false" />
      <span class="slot-count">{{ slot.count }}</span>
      <span v-if="i < 9" class="slot-key">{{ i + 1 }}</span>
      <span v-if="slot.def.tool" class="slot-tool">T{{ slot.def.tool }}</span>
    </button>
    <p v-if="!game.hotbar.length" class="hotbar-empty">背包还是空的 —— 按住地上的草方块挖一下试试</p>
  </div>
</template>
