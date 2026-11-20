import { reactive } from 'vue';

// 键盘和触屏按钮写同一份输入状态，游戏循环每帧读它，避免两套控制逻辑打架。
export const controls = reactive({ left: false, right: false, jump: false });

export type ControlKey = keyof typeof controls;

export function releaseAll() {
  controls.left = false;
  controls.right = false;
  controls.jump = false;
}
