<script setup lang="ts">
import { onMounted } from 'vue';
import { useGame } from './stores/game';
import GameCanvas from './components/GameCanvas.vue';
import Hotbar from './components/Hotbar.vue';
import CraftPanel from './components/CraftPanel.vue';
import BountyPanel from './components/BountyPanel.vue';
import TouchControls from './components/TouchControls.vue';
import WorldPicker from './components/WorldPicker.vue';
import VictoryPanel from './components/VictoryPanel.vue';

const game = useGame();
onMounted(() => game.boot());
</script>

<template>
  <div class="app">
    <header class="topbar">
      <div class="brand">方块猎场<span>mc-lite</span></div>
      <template v-if="game.world">
        <div class="world-name">{{ game.world.name }} · {{ game.world.width }} 格长</div>
        <div class="weapon-badge" :title="'武器等级 T' + game.weaponTier">
          {{ game.weaponName }} · T{{ game.weaponTier }}
        </div>
        <div v-if="game.cleared" class="clear-badge" title="已经打死巢穴里的 Boss">已通关</div>
        <div class="topbar-actions">
          <button class="btn btn-ghost" @click="game.bountyOpen = !game.bountyOpen; game.craftOpen = false">悬赏 (F)</button>
          <button class="btn btn-ghost" @click="game.craftOpen = !game.craftOpen; game.bountyOpen = false">锻造 (E)</button>
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
      <BountyPanel v-if="game.bountyOpen" />
      <VictoryPanel v-if="game.victory" />
    </main>

    <transition name="fade">
      <div v-if="game.notice" class="toast" :class="game.notice.tone">{{ game.notice.text }}</div>
    </transition>
  </div>
</template>
