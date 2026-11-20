<script setup lang="ts">
import { ref } from 'vue';
import { useGame } from '../stores/game';

const game = useGame();
const name = ref('我的世界');
const seed = ref('');
const busy = ref(false);
const confirming = ref('');

async function create() {
  busy.value = true;
  const raw = seed.value.trim();
  const parsed = raw === '' ? undefined : Number(raw);
  await game.createWorld(name.value.trim() || '新世界', parsed !== undefined && Number.isFinite(parsed) ? parsed : undefined);
  busy.value = false;
}

async function remove(id: string) {
  if (confirming.value !== id) {
    confirming.value = id;
    return;
  }
  confirming.value = '';
  await game.deleteWorld(id);
}

function when(iso: string) {
  return new Date(iso).toLocaleString('zh-CN', { hour12: false });
}
</script>

<template>
  <div class="picker">
    <section class="card">
      <h1>方块世界</h1>
      <p class="lead">
        2D 侧视方块世界：挖泥土、砍树、下矿洞，用木板和工作台一级级做出更好的镐，
        直到挖到钻石。世界存在后端 H2 里，关掉浏览器再回来还在。
      </p>
      <div class="form">
        <label>
          世界名字
          <input v-model="name" maxlength="24" placeholder="我的世界" />
        </label>
        <label>
          种子（可选，同一个种子生成同一个世界）
          <input v-model="seed" inputmode="numeric" placeholder="留空就随机" />
        </label>
        <button class="btn btn-primary btn-big" :disabled="busy" @click="create">
          {{ busy ? '生成中…' : '生成新世界' }}
        </button>
      </div>
    </section>

    <section class="card">
      <h2>已有的世界</h2>
      <p v-if="!game.worlds.length" class="muted">还没有世界，先生成一个。</p>
      <ul v-else class="world-list">
        <li v-for="w in game.worlds" :key="w.id">
          <div class="world-meta">
            <strong>{{ w.name }}</strong>
            <span class="muted">{{ w.width }}×{{ w.height }} · 种子 {{ w.seed }} · {{ when(w.updatedAt) }}</span>
          </div>
          <div class="world-actions">
            <button class="btn btn-primary" @click="game.openWorld(w.id)">进入</button>
            <button class="btn btn-ghost" @click="remove(w.id)">
              {{ confirming === w.id ? '再点一次确认删除' : '删除' }}
            </button>
          </div>
        </li>
      </ul>
    </section>

    <section class="card">
      <h2>操作</h2>
      <ul class="help">
        <li><b>A / D</b> 或 <b>← →</b> 移动，<b>W / 空格</b> 跳</li>
        <li><b>按住鼠标左键</b>挖方块，<b>右键</b>放置；<b>Q</b> 切换挖/放</li>
        <li><b>1–9</b> 选背包里的方块，<b>E</b> 打开合成面板，<b>F</b> 打开地下邮局委托</li>
        <li>沙和碎石挖空下方会整列塌下来，矿洞留柱子才挡得住；埋到玩家会被顶到堆顶</li>
        <li>限时委托交付换荧光晶等稀有酬劳，荧光晶放在洞里会照亮周围</li>
        <li>手机上用屏幕下方的方向键和「挖/放」按钮，手指按住方块即可挖</li>
      </ul>
      <h2>这一版省掉了什么</h2>
      <ul class="help">
        <li v-for="(line, i) in game.content?.$simplifications ?? []" :key="i">{{ line }}</li>
      </ul>
    </section>
  </div>
</template>
