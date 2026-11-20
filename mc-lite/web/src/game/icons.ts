import type { Content } from '../types';
import { buildTextures } from './textures';

let cache: { content: Content; icons: Record<string, string> } | null = null;

// 背包和合成面板用 <img>，所以把同一套像素贴图导出成 dataURL，只算一次。
export function itemIcons(content: Content) {
  if (cache && cache.content === content) return cache.icons;
  const icons: Record<string, string> = {};
  buildTextures(content).items.forEach((canvas, key) => {
    icons[key] = canvas.toDataURL();
  });
  cache = { content, icons };
  return icons;
}
