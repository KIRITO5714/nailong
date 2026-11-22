import { reactive } from 'vue';

// 键盘和触屏按钮写同一份输入状态，游戏循环每帧读它，避免两套控制逻辑打架。
// attack 是边沿触发：置 true 表示「这一帧要挥一刀」，循环消费后自己清掉。
export const controls = reactive({ left: false, right: false, jump: false, attack: false });

export function releaseAll() {
  controls.left = false;
  controls.right = false;
  controls.jump = false;
  controls.attack = false;
}
