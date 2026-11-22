<script setup lang="ts">
import { computed } from 'vue';
import { useGame } from '../stores/game';
import { itemIcons } from '../game/icons';

const game = useGame();
const icons = computed(() => (game.content ? itemIcons(game.content) : {}));
</script>

<template>
  <div class="hotbar">
    <button
      v-for="slot in game.hotbar"
      :key="slot.key"
      class="slot"
      :class="{ armed: slot.def.weapon === game.weaponTier }"
      :title="slot.def.name"
      type="button"
    >
      <img :src="icons[slot.key]" :alt="slot.def.name" draggable="false" />
      <span class="slot-count">{{ slot.count }}</span>
      <span v-if="slot.def.weapon" class="slot-tool">T{{ slot.def.weapon }}</span>
    </button>
    <p v-if="!game.hotbar.length" class="hotbar-empty">
      背包还是空的 —— 走近怪物点一下画面挥砍，材料会自动进包
    </p>
  </div>
</template>
