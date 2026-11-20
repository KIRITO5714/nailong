<script setup lang="ts">
import { controls } from '../game/controls';
import { useGame } from '../stores/game';

const game = useGame();

function hold(key: 'left' | 'right' | 'jump', value: boolean) {
  controls[key] = value;
}
</script>

<template>
  <div class="touch">
    <div class="touch-group">
      <button
        class="pad"
        @pointerdown.prevent="hold('left', true)"
        @pointerup="hold('left', false)"
        @pointerleave="hold('left', false)"
        @pointercancel="hold('left', false)"
      >←</button>
      <button
        class="pad"
        @pointerdown.prevent="hold('right', true)"
        @pointerup="hold('right', false)"
        @pointerleave="hold('right', false)"
        @pointercancel="hold('right', false)"
      >→</button>
    </div>
    <div class="touch-group">
      <button class="pad pad-mode" @click="game.mode = game.mode === 'mine' ? 'place' : 'mine'">
        {{ game.mode === 'mine' ? '挖' : '放' }}
      </button>
      <button
        class="pad pad-jump"
        @pointerdown.prevent="hold('jump', true)"
        @pointerup="hold('jump', false)"
        @pointerleave="hold('jump', false)"
        @pointercancel="hold('jump', false)"
      >跳</button>
    </div>
  </div>
</template>
