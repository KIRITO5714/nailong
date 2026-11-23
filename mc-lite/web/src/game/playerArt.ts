// 主角造型：按参考图画的黄色圆胖卡通角色（奶油肚皮、绿眼睛、棕手棕脚、头身一体的梨形）。
// 用椭圆 + 胶囊两种笔触在 24×34 的虚拟格子里画，浏览器和校验脚本共用同一份几何描述。
export const PLAYER_GRID_W = 24;
export const PLAYER_GRID_H = 34;

// 主角比碰撞盒（0.6×1.8 格）画得宽一点，圆胖体型才看得出来；高度仍贴着碰撞盒。
export const PLAYER_TILE_W = 1.15;
export const PLAYER_TILE_H = 1.8;

export interface PaintOps {
  ell(cx: number, cy: number, rx: number, ry: number, color: string): void;
  cap(x1: number, y1: number, x2: number, y2: number, r: number, color: string): void;
}

export interface PlayerPose {
  walk: number;
  onGround: boolean;
  moving: boolean;
  swinging: boolean;
  facing: 1 | -1;
}

const C = {
  body: '#f4d158',
  bodyShade: '#e2ba45',
  bodyLight: '#fbe98f',
  belly: '#f5ecd6',
  limb: '#8a7a63',
  toe: '#6f604c',
  eye: '#5fae67',
  pupil: '#141a16',
  mouth: '#8a4a38',
};

// 镜像：贴图只描述朝右的样子，朝左时把 x 翻到格子另一侧，省一套镜像几何。
export function mirrorOps(ops: PaintOps, facing: 1 | -1): PaintOps {
  if (facing === 1) return ops;
  const flip = (x: number) => PLAYER_GRID_W - x;
  return {
    ell: (cx, cy, rx, ry, color) => ops.ell(flip(cx), cy, rx, ry, color),
    cap: (x1, y1, x2, y2, r, color) => ops.cap(flip(x1), y1, flip(x2), y2, r, color),
  };
}

interface Limbs {
  frontHand: [number, number];
  backHand: [number, number];
  frontFoot: [number, number];
  backFoot: [number, number];
}

// 走路由 walk 相位摆四肢，跳起收腿，挥砍时前手平举、嘴张开。
function limbsOf(pose: PlayerPose): Limbs {
  if (!pose.onGround) {
    return { frontHand: [20.5, 9.5], backHand: [5, 12], frontFoot: [14, 30], backFoot: [9.5, 30.8] };
  }
  if (pose.swinging) {
    return { frontHand: [22.5, 14.5], backHand: [6, 19], frontFoot: [17, 31.8], backFoot: [7.5, 31.8] };
  }
  if (!pose.moving) {
    // 站定时前手举起来挥一挥，跟参考图的动作对上。
    return { frontHand: [21, 8.5], backHand: [6, 19], frontFoot: [16.5, 31.8], backFoot: [7.5, 31.8] };
  }
  const s = Math.sin(pose.walk * 3.4);
  const lift = (v: number) => Math.max(0, v) * 1.2;
  return {
    frontHand: [19 + s * 1.5, 18.5 - s * 1.0],
    backHand: [6 - s * 1.5, 19 + s * 1.0],
    frontFoot: [16.5 + s * 2.2, 31.8 - lift(s)],
    backFoot: [7.5 - s * 2.2, 31.8 - lift(-s)],
  };
}

// 四肢先铺一圈暗色再盖主色，不然黄胳膊黄腿融进身体里看不出轮廓。
function drawLeg(ops: PaintOps, hip: [number, number], foot: [number, number]) {
  ops.cap(hip[0], hip[1], foot[0], foot[1] - 0.8, 2.6, C.bodyShade);
  ops.cap(hip[0], hip[1], foot[0], foot[1] - 0.8, 2.1, C.body);
  ops.ell(foot[0] + 0.6, foot[1], 3.2, 1.8, C.limb);
  ops.ell(foot[0] + 3.0, foot[1] - 0.4, 0.75, 0.75, C.toe);
  ops.ell(foot[0] + 2.1, foot[1] + 0.6, 0.75, 0.75, C.toe);
}

function drawArm(ops: PaintOps, shoulder: [number, number], hand: [number, number]) {
  ops.cap(shoulder[0], shoulder[1], hand[0], hand[1], 1.9, C.bodyShade);
  ops.cap(shoulder[0], shoulder[1], hand[0], hand[1], 1.5, C.body);
  ops.ell(hand[0], hand[1], 2.0, 1.9, C.limb);
}

// 头、颈、身三团叠成一只梨形：参考图没有脖子，中间不补一团就会画成雪人。
function drawBody(ops: PaintOps) {
  ops.ell(11.0, 19.6, 9.8, 9.9, C.bodyShade);
  ops.ell(11.6, 12.6, 7.4, 5.4, C.bodyShade);
  ops.ell(11.8, 6.7, 6.3, 6.1, C.bodyShade);
  ops.ell(12.2, 19.3, 9.5, 9.6, C.body);
  ops.ell(12.8, 12.5, 7.0, 5.1, C.body);
  ops.ell(12.6, 6.5, 6.1, 5.9, C.body);
  ops.ell(17.2, 7.6, 2.4, 2.2, C.body);
  ops.ell(15.0, 3.2, 2.2, 1.2, C.bodyLight);
  ops.ell(13.5, 21.0, 6.6, 7.6, C.belly);
}

function drawFace(ops: PaintOps, swinging: boolean) {
  ops.ell(11.6, 5.9, 1.4, 1.4, C.eye);
  ops.ell(11.8, 6.0, 0.65, 0.65, C.pupil);
  ops.ell(15.7, 6.0, 1.6, 1.6, C.eye);
  ops.ell(16.0, 6.1, 0.75, 0.75, C.pupil);
  if (swinging) {
    ops.ell(17.3, 9.6, 1.0, 0.8, C.mouth);
  } else {
    // 两段短胶囊拼一个上翘的微笑。
    ops.cap(16.4, 9.2, 17.5, 9.6, 0.4, C.mouth);
    ops.cap(17.5, 9.6, 18.4, 9.2, 0.4, C.mouth);
  }
}

export function paintPlayer(raw: PaintOps, pose: PlayerPose) {
  const ops = mirrorOps(raw, pose.facing);
  const limbs = limbsOf(pose);
  drawArm(ops, [7.5, 13.5], limbs.backHand);
  drawLeg(ops, [9.5, 28.0], limbs.backFoot);
  drawBody(ops);
  drawLeg(ops, [14.5, 28.0], limbs.frontFoot);
  drawFace(ops, pose.swinging);
  drawArm(ops, [18.0, 13.5], limbs.frontHand);
}

// 浏览器笔触：直接落在主画布上，left/top/scale 把虚拟格子映射到屏幕像素。
export function canvasOps(ctx: CanvasRenderingContext2D, left: number, top: number, sx: number, sy: number): PaintOps {
  return {
    ell(cx, cy, rx, ry, color) {
      ctx.fillStyle = color;
      ctx.beginPath();
      ctx.ellipse(left + cx * sx, top + cy * sy, rx * sx, ry * sy, 0, 0, Math.PI * 2);
      ctx.fill();
    },
    cap(x1, y1, x2, y2, r, color) {
      ctx.strokeStyle = color;
      ctx.lineWidth = r * 2 * Math.min(sx, sy);
      ctx.lineCap = 'round';
      ctx.beginPath();
      ctx.moveTo(left + x1 * sx, top + y1 * sy);
      ctx.lineTo(left + x2 * sx, top + y2 * sy);
      ctx.stroke();
    },
  };
}
