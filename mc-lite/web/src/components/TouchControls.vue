<script setup lang="ts">
import { controls } from '../game/controls';

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
      <button class="pad pad-jump" @pointerdown.prevent="hold('jump', true)"
        @pointerup="hold('jump', false)" @pointerleave="hold('jump', false)"
        @pointercancel="hold('jump', false)"
      >跳</button>
      <!-- 挥砍是边沿触发：按一次砍一刀，冷却由当前武器决定 -->
      <button class="pad pad-attack" @pointerdown.prevent="controls.attack = true">砍</button>
    </div>
  </div>
</template>
