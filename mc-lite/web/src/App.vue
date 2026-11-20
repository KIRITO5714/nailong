<script setup lang="ts">
import { onMounted } from 'vue';
import { useGame } from './stores/game';
import GameCanvas from './components/GameCanvas.vue';
import Hotbar from './components/Hotbar.vue';
import CraftPanel from './components/CraftPanel.vue';
import TouchControls from './components/TouchControls.vue';
import WorldPicker from './components/WorldPicker.vue';

const game = useGame();
onMounted(() => game.boot());
</script>

<template>
  <div class="app">
    <header class="topbar">
      <div class="brand">方块世界<span>mc-lite</span></div>
      <template v-if="game.world">
        <div class="world-name">{{ game.world.name }}</div>
        <div class="tool-badge" :title="'工具等级 T' + game.toolTier">
          {{ game.toolName }} · T{{ game.toolTier }}
        </div>
        <div class="topbar-actions">
          <button class="btn btn-ghost" @click="game.craftOpen = !game.craftOpen">合成 (E)</button>
          <button class="btn btn-ghost" @click="game.leave()">换个世界</button>
        </div>
      </template>
    </header>

    <p v-if="game.booting" class="center-note">正在连接后端…</p>
    <p v-else-if="game.fatal" class="center-note warn">{{ game.fatal }}</p>
    <WorldPicker v-else-if="!game.world" />

    <main v-else class="play">
      <GameCanvas />
      <Hotbar />
      <TouchControls />
      <CraftPanel v-if="game.craftOpen" />
    </main>

    <transition name="fade">
      <div v-if="game.notice" class="toast" :class="game.notice.tone">{{ game.notice.text }}</div>
    </transition>
  </div>
</template>
