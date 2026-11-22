import type { MobDef } from '../types';
import { canvas, rng, shade } from './art';

// 15 只怪共用 7 种造型：按内容表的 shape 画 16×16 像素图，配色取自每只怪自己的 color/accent。
// 贴图只画朝右的样子，朝左时由 render.ts 水平翻转。
type Painter = (g: CanvasRenderingContext2D, def: MobDef) => void;

const PAINTERS: Record<string, Painter> = {
  slime: paintSlime, bat: paintBat, beast: paintBeast, golem: paintGolem,
  wisp: paintWisp, boss: paintBoss, humanoid: paintHumanoid,
};

export function paintMob(def: MobDef): HTMLCanvasElement {
  const c = canvas();
  (PAINTERS[def.shape] ?? paintHumanoid)(c.getContext('2d')!, def);
  return c;
}

function eyes(g: CanvasRenderingContext2D, x: number, y: number, color = '#20242e') {
  g.fillStyle = color;
  g.fillRect(x, y, 2, 2);
  g.fillRect(x + 4, y, 2, 2);
}

// 史莱姆：一坨半透明果冻，顶上高光。
function paintSlime(g: CanvasRenderingContext2D, def: MobDef) {
  g.fillStyle = def.color;
  g.fillRect(2, 6, 12, 8);
  g.fillRect(3, 4, 10, 10);
  g.fillStyle = def.accent;
  g.fillRect(4, 5, 4, 2);
  eyes(g, 5, 9);
}

// 蝙蝠与鹰：小身子配一对张开的翅膀。
function paintBat(g: CanvasRenderingContext2D, def: MobDef) {
  g.fillStyle = def.color;
  g.fillRect(6, 6, 4, 5);
  g.fillRect(1, 4, 5, 4);
  g.fillRect(10, 4, 5, 4);
  g.fillStyle = def.accent;
  g.fillRect(2, 5, 3, 1);
  g.fillRect(11, 5, 3, 1);
  g.fillRect(6, 4, 4, 1);
  eyes(g, 7, 7, '#f0d060');
}

// 四足兽：狼、蛛、蛇都用这套，靠配色和体型区分。
function paintBeast(g: CanvasRenderingContext2D, def: MobDef) {
  const rnd = rng(def.key.length * 31337 + def.hp);
  g.fillStyle = def.color;
  g.fillRect(2, 6, 11, 5);
  g.fillRect(11, 4, 4, 5);
  g.fillRect(1, 5, 2, 3);
  g.fillStyle = shade(def.color, -34);
  g.fillRect(3, 11, 2, 4);
  g.fillRect(6, 11, 2, 4);
  g.fillRect(10, 11, 2, 4);
  g.fillStyle = def.accent;
  g.fillRect(3, 6, 8, 1);
  for (let i = 0; i < 6; i++) g.fillRect(3 + Math.floor(rnd() * 8), 7 + Math.floor(rnd() * 3), 1, 1);
  g.fillRect(13, 7, 2, 1);
  eyes(g, 12, 5, '#f06a5a');
}

// 岩魔与巨人：一整块带裂纹的石头，眼睛和裂缝发光。
function paintGolem(g: CanvasRenderingContext2D, def: MobDef) {
  const rnd = rng(def.key.length * 31337 + def.hp);
  g.fillStyle = def.color;
  g.fillRect(2, 4, 12, 10);
  g.fillRect(4, 2, 8, 12);
  for (let i = 0; i < 18; i++) {
    g.fillStyle = rnd() < 0.5 ? shade(def.color, -26) : shade(def.color, 20);
    g.fillRect(3 + Math.floor(rnd() * 10), 3 + Math.floor(rnd() * 10), 2, 2);
  }
  g.fillStyle = def.accent;
  g.fillRect(5, 6, 2, 2);
  g.fillRect(9, 6, 2, 2);
  g.fillRect(6, 10, 4, 1);
  g.fillRect(3, 8, 1, 3);
  g.fillRect(12, 7, 1, 4);
}

// 夜魇与龙裔：没有腿的飘浮灵体，下摆散成雾。
function paintWisp(g: CanvasRenderingContext2D, def: MobDef) {
  const rnd = rng(def.key.length * 31337 + def.hp);
  g.fillStyle = def.color;
  g.fillRect(4, 2, 8, 8);
  g.fillRect(3, 4, 10, 5);
  g.fillRect(5, 10, 6, 2);
  g.fillRect(6, 12, 4, 2);
  g.fillRect(7, 14, 2, 1);
  for (let i = 0; i < 8; i++) {
    g.fillStyle = rnd() < 0.5 ? shade(def.color, -22) : def.color;
    g.fillRect(4 + Math.floor(rnd() * 8), 9 + Math.floor(rnd() * 5), 1, 2);
  }
  g.fillStyle = def.accent;
  g.fillRect(5, 3, 6, 1);
  g.fillRect(4, 6, 2, 3);
  g.fillRect(10, 6, 2, 3);
  eyes(g, 6, 5, def.accent);
}

// 直立人形：僵尸、骷髅、小鬼、武士共用，靠配色和护甲条纹区分身份。
function paintHumanoid(g: CanvasRenderingContext2D, def: MobDef) {
  g.fillStyle = def.color;
  g.fillRect(5, 1, 6, 5);
  g.fillRect(4, 6, 8, 6);
  g.fillRect(5, 12, 2, 3);
  g.fillRect(9, 12, 2, 3);
  g.fillRect(2, 7, 2, 5);
  g.fillRect(12, 7, 2, 5);
  g.fillStyle = def.accent;
  g.fillRect(5, 7, 6, 1);
  g.fillRect(7, 9, 2, 2);
  g.fillRect(4, 11, 8, 1);
  g.fillStyle = shade(def.color, -46);
  g.fillRect(5, 3, 6, 1);
  eyes(g, 6, 3, '#f0d060');
}

// Boss：一团黑甲，头顶双角、两侧骨刺、胸口一颗发亮的核心。
function paintBoss(g: CanvasRenderingContext2D, def: MobDef) {
  const rnd = rng(def.key.length * 31337 + def.hp);
  g.fillStyle = def.color;
  g.fillRect(3, 4, 10, 11);
  g.fillRect(2, 6, 12, 7);
  g.fillRect(5, 2, 6, 4);
  for (let i = 0; i < 22; i++) {
    g.fillStyle = rnd() < 0.5 ? shade(def.color, -18) : shade(def.color, 26);
    g.fillRect(2 + Math.floor(rnd() * 12), 3 + Math.floor(rnd() * 12), 2, 2);
  }
  g.fillStyle = def.accent;
  g.fillRect(3, 1, 2, 3);
  g.fillRect(11, 1, 2, 3);
  g.fillRect(4, 0, 1, 2);
  g.fillRect(12, 0, 1, 2);
  g.fillRect(1, 7, 2, 4);
  g.fillRect(13, 7, 2, 4);
  g.fillRect(6, 8, 4, 4);
  g.fillStyle = shade(def.accent, 70);
  g.fillRect(7, 9, 2, 2);
  eyes(g, 6, 4, def.accent);
}
