<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useGame } from '../stores/game';
import { itemIcons } from '../game/icons';
import type { Bounty } from '../types';

const game = useGame();
const icons = computed(() => (game.content ? itemIcons(game.content) : {}));
const tick = ref(Date.now());
let timer = 0;
let poll = 0;

onMounted(() => {
  void game.syncBounties();
  timer = window.setInterval(() => {
    tick.value = Date.now();
  }, 1000);
  poll = window.setInterval(() => void game.syncBounties(), 5000);
});

onBeforeUnmount(() => {
  window.clearInterval(timer);
  window.clearInterval(poll);
});

// 倒计时用服务端时钟算，改本机时间骗不过限时。
const now = computed(() => tick.value + game.nowOffset);

function left(bounty: Bounty) {
  const ms = Math.max(0, bounty.expiresAt - now.value);
  const m = Math.floor(ms / 60000);
  const s = Math.floor((ms % 60000) / 1000);
  return `${m}:${String(s).padStart(2, '0')}`;
}

function urgent(bounty: Bounty) {
  return bounty.expiresAt - now.value < 60000;
}

function short(bounty: Bounty) {
  return Object.entries(bounty.wants).filter(([key, need]) => (game.kills[key] ?? 0) < need);
}

function mobName(key: string) {
  return game.content?.mobs.find((m) => m.key === key)?.name ?? key;
}

function nameOf(key: string) {
  return game.content?.items[key]?.name ?? key;
}
</script>

<template>
  <aside class="craft post">
    <header class="craft-head">
      <h2>悬赏板</h2>
      <span class="craft-bench on">按击杀数交货，不用交材料</span>
      <button class="btn btn-ghost" @click="game.bountyOpen = false">关闭 (F)</button>
    </header>
    <ul class="craft-list">
      <li v-for="bounty in game.bounties" :key="bounty.id" class="craft-item">
        <img class="craft-icon" :src="icons[Object.keys(bounty.reward)[0]]" :alt="bounty.title" draggable="false" />
        <div class="craft-body">
          <div class="craft-name">
            {{ bounty.title }}
            <em class="post-timer" :class="{ urgent: urgent(bounty) }">剩 {{ left(bounty) }}</em>
          </div>
          <div class="craft-cost">
            猎：
            <span v-for="(need, key) in bounty.wants" :key="key" :class="{ short: (game.kills[key] ?? 0) < need }">
              {{ mobName(String(key)) }} {{ game.kills[String(key)] ?? 0 }}/{{ need }}
            </span>
          </div>
          <div class="craft-out">
            赏：
            <span v-for="(count, key) in bounty.reward" :key="key">{{ nameOf(String(key)) }} ×{{ count }}</span>
          </div>
        </div>
        <button class="btn btn-primary" :disabled="short(bounty).length > 0" @click="game.deliver(bounty.id)">
          {{ short(bounty).length ? '猎物不够' : '交付' }}
        </button>
      </li>
    </ul>
    <p class="craft-note">
      悬赏限时，过期板子就换一张新单。交付时按单子里的数量扣击杀计数，多出来的猎物留着交下一单。
      板子最多同时挂三张，只派你已经杀过的怪（种类不够时拿出生点附近的入门怪垫底），Boss 永远不上板。
    </p>
  </aside>
</template>
