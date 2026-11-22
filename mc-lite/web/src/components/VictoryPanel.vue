<script setup lang="ts">
import { computed } from 'vue';
import { useGame } from '../stores/game';

// 通关界面：只在「刚打死 Boss」那一次弹出（store 里 victory 的 false→true 跳变），
// 之后读档只在顶栏显示「已通关」徽章，不再拦着玩家继续游荡。
const game = useGame();

const stats = computed(() => [
  { label: '世界', value: game.world?.name ?? '—' },
  { label: '总击杀', value: `${game.totalKills} 只` },
  { label: '遇到的怪', value: `${game.kindsKilled} / ${game.mobDefs.length} 种` },
  { label: '武器', value: `${game.weaponName} · T${game.weaponTier}` },
]);

function keepPlaying() {
  game.victory = false;
}

function anotherWorld() {
  game.victory = false;
  game.leave();
}
</script>

<template>
  <div class="victory">
    <div class="victory-card">
      <p class="victory-kicker">巢穴安静下来了</p>
      <h1 class="victory-title">世界通关！</h1>
      <p class="victory-note">
        你讨伐了{{ game.bossDef?.name ?? '墟岩魔王' }}。地图上的怪还会继续刷新，悬赏板也照旧接单——
        想再打一轮就留在这里，想换一张地图就重开一个世界。
      </p>
      <ul class="victory-stats">
        <li v-for="stat in stats" :key="stat.label">
          <span>{{ stat.label }}</span>
          <b>{{ stat.value }}</b>
        </li>
      </ul>
      <div class="victory-actions">
        <button class="btn btn-primary btn-big" @click="keepPlaying">继续游荡</button>
        <button class="btn btn-big" @click="anotherWorld">换个世界</button>
      </div>
    </div>
  </div>
</template>
