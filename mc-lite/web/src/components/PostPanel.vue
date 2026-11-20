<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useGame } from '../stores/game';
import { itemIcons } from '../game/icons';
import type { Order } from '../types';

const game = useGame();
const icons = computed(() => (game.content ? itemIcons(game.content) : {}));
const tick = ref(Date.now());
let timer = 0;
let poll = 0;

onMounted(() => {
  void game.syncOrders();
  timer = window.setInterval(() => {
    tick.value = Date.now();
  }, 1000);
  poll = window.setInterval(() => void game.syncOrders(), 5000);
});

onBeforeUnmount(() => {
  window.clearInterval(timer);
  window.clearInterval(poll);
});

// 倒计时用服务端时钟算，改本机时间骗不过限时。
const now = computed(() => tick.value + game.nowOffset);

function left(order: Order) {
  const ms = Math.max(0, order.expiresAt - now.value);
  const m = Math.floor(ms / 60000);
  const s = Math.floor((ms % 60000) / 1000);
  return `${m}:${String(s).padStart(2, '0')}`;
}

function urgent(order: Order) {
  return order.expiresAt - now.value < 60000;
}

function missing(order: Order) {
  return Object.entries(order.wants).filter(([key, need]) => (game.inventory[key] ?? 0) < need);
}

function nameOf(key: string) {
  return game.content?.items[key]?.name ?? key;
}
</script>

<template>
  <aside class="craft post">
    <header class="craft-head">
      <h2>地下邮局</h2>
      <span class="craft-bench on">鼹鼠邮差等着收货</span>
      <button class="btn btn-ghost" @click="game.postOpen = false">关闭 (F)</button>
    </header>
    <ul class="craft-list">
      <li v-for="order in game.orders" :key="order.id" class="craft-item">
        <img class="craft-icon" :src="icons[Object.keys(order.reward)[0]]" :alt="order.title" draggable="false" />
        <div class="craft-body">
          <div class="craft-name">
            {{ order.title }}
            <em class="post-timer" :class="{ urgent: urgent(order) }">剩 {{ left(order) }}</em>
          </div>
          <div class="craft-cost">
            要：
            <span v-for="(need, key) in order.wants" :key="key" :class="{ short: (game.inventory[key] ?? 0) < need }">
              {{ nameOf(key) }} {{ game.inventory[key] ?? 0 }}/{{ need }}
            </span>
          </div>
          <div class="craft-out">
            酬：
            <span v-for="(count, key) in order.reward" :key="key">{{ nameOf(key) }} ×{{ count }}</span>
          </div>
        </div>
        <button class="btn btn-primary" :disabled="missing(order).length > 0" @click="game.deliver(order.id)">
          {{ missing(order).length ? '货不够' : '交付' }}
        </button>
      </li>
    </ul>
    <p class="craft-note">
      委托限时，过期邮差就换一张新单。荧光晶只有邮局给：放在矿洞里会照亮周围，
      深处不再只靠身边那一圈光。沙和碎石挖空下方会整列塌下来，留柱子才挡得住。
    </p>
  </aside>
</template>