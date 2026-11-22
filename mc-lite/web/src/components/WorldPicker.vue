<script setup lang="ts">
import { ref } from 'vue';
import { useGame } from '../stores/game';

const game = useGame();
const name = ref('我的猎场');
const seed = ref('');
const busy = ref(false);
const confirming = ref('');

async function create() {
  busy.value = true;
  const raw = seed.value.trim();
  const parsed = raw === '' ? undefined : Number(raw);
  await game.createWorld(name.value.trim() || '新猎场', parsed !== undefined && Number.isFinite(parsed) ? parsed : undefined);
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
      <h1>方块猎场</h1>
      <p class="lead">
        2D 侧视方块世界里的打怪游戏：地形不可挖掘，只是风景和战场。
        沿着 480 格长的地表走，挥砍史莱姆、僵尸、骷髅、蝙蝠和岩魔，
        用掉落的材料打造更强的剑，再到悬赏板按击杀数交货换晶核。
        世界存在后端 H2 里，关掉浏览器再回来还在。
      </p>
      <div class="form">
        <label>
          猎场名字
          <input v-model="name" maxlength="24" placeholder="我的猎场" />
        </label>
        <label>
          种子（可选，同一个种子生成同一片地形）
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
        <li><b>点画面</b>或按 <b>J</b> 朝面向的方向挥砍，一刀只砍中最近的一只怪</li>
        <li><b>E</b> 打开锻造面板，<b>F</b> 打开悬赏板；<b>Esc</b> 关闭</li>
        <li>怪物靠近会追你并造成接触伤害，血量归零就在出生点重生，存档进度不丢</li>
        <li>悬赏按<b>击杀数</b>交货，限时过期板子自动换新单</li>
        <li>手机上用屏幕下方的方向键、「跳」和「砍」按钮</li>
      </ul>
      <h2>这一版省掉了什么</h2>
      <ul class="help">
        <li v-for="(line, i) in game.content?.$simplifications ?? []" :key="i">{{ line }}</li>
      </ul>
    </section>
  </div>
</template>
