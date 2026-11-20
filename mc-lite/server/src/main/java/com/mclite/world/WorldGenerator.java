package com.mclite.world;

import java.util.Random;

// 同一个 seed 必须生成完全一样的世界，所以只用 java.util.Random(seed)，不碰 Math.random。
public class WorldGenerator {

  private static final int GRASS = 1, DIRT = 2, STONE = 3, LOG = 4, LEAVES = 5;
  private static final int COAL = 6, IRON = 7, GOLD = 8, DIAMOND = 9, BEDROCK = 10;
  private static final int SAND = 13, GRAVEL = 14;

  public World generate(long seed, int width, int height) {
    Random rnd = new Random(seed);
    int[] surface = new int[width];
    World world = new World(width, height, new int[width * height], surface);
    terrain(world, rnd, surface, width, height);
    ores(world, rnd, surface, width, height);
    pockets(world, rnd, surface, width, height);
    trees(world, rnd, surface, width);
    clearSpawn(world, surface, width / 2);
    return world;
  }

  public double[] spawn(World world) {
    int x = world.width / 2;
    return new double[] {x + 0.5, world.surface()[x] - 0.9};
  }

  private void terrain(World world, Random rnd, int[] surface, int width, int height) {
    int base = Math.max(10, height / 3);
    double p1 = rnd.nextDouble() * 6.28, p2 = rnd.nextDouble() * 6.28, p3 = rnd.nextDouble() * 6.28;
    for (int x = 0; x < width; x++) {
      double wave = Math.sin(x * 0.055 + p1) * 5 + Math.sin(x * 0.17 + p2) * 2.2 + Math.sin(x * 0.43 + p3) * 0.9;
      int top = clamp((int) Math.round(base + wave), 6, height - 24);
      surface[x] = top;
      for (int y = top; y < height; y++) {
        int id = y == top ? GRASS : y <= top + 4 ? DIRT : STONE;
        if (y >= height - 3) id = BEDROCK;
        world.set(x, y, id);
      }
    }
  }

  private void ores(World world, Random rnd, int[] surface, int width, int height) {
    int scale = Math.max(1, width * height / 15360);
    veinType(world, rnd, surface, width, height, COAL, 60 * scale, 0.34, 0.66);
    veinType(world, rnd, surface, width, height, IRON, 45 * scale, 0.50, 0.82);
    veinType(world, rnd, surface, width, height, GOLD, 25 * scale, 0.66, 0.92);
    veinType(world, rnd, surface, width, height, DIAMOND, 14 * scale, 0.80, 0.96);
  }

  private void veinType(World world, Random rnd, int[] surface, int width, int height,
                        int id, int veins, double from, double to) {
    for (int i = 0; i < veins; i++) {
      int x = rnd.nextInt(width);
      int min = Math.max(surface[x] + 5, (int) (height * from));
      int max = Math.min(height - 4, (int) (height * to));
      if (max <= min) continue;
      int y = min + rnd.nextInt(max - min);
      int size = 3 + rnd.nextInt(4);
      for (int step = 0; step < size; step++) {
        if (world.get(x, y) == STONE) world.set(x, y, id);
        x = clamp(x + rnd.nextInt(3) - 1, 0, width - 1);
        y = clamp(y + rnd.nextInt(3) - 1, min, max);
      }
    }
  }

  // 松散层：土 band 里的沙囊、石 band 里的碎石囊。挖空它们下方就会整列塌下来。
  private void pockets(World world, Random rnd, int[] surface, int width, int height) {
    int scale = Math.max(1, width * height / 15360);
    sandPockets(world, rnd, surface, width, 22 * scale);
    veinType(world, rnd, surface, width, height, GRAVEL, 46 * scale, 0.30, 0.90);
  }

  private void sandPockets(World world, Random rnd, int[] surface, int width, int count) {
    for (int i = 0; i < count; i++) {
      int x = rnd.nextInt(width);
      int top = surface[x] + 1;
      int span = Math.min(4, world.height - 4 - top);
      if (span <= 0) continue;
      int y = top + rnd.nextInt(span);
      int size = 4 + rnd.nextInt(5);
      for (int step = 0; step < size; step++) {
        if (world.get(x, y) == DIRT) world.set(x, y, SAND);
        x = clamp(x + rnd.nextInt(3) - 1, 0, width - 1);
        y = clamp(y + rnd.nextInt(3) - 1, top, top + span - 1);
      }
    }
  }

  private void trees(World world, Random rnd, int[] surface, int width) {
    int last = -10;
    for (int x = 4; x < width - 4; x++) {
      if (x - last < 6 || rnd.nextDouble() > 0.34) continue;
      last = x;
      int ground = surface[x];
      int trunk = 4 + rnd.nextInt(3);
      int top = ground - trunk;
      if (top < 4) continue;
      for (int dy = -2; dy <= 1; dy++) {
        for (int dx = -2; dx <= 2; dx++) {
          if (Math.abs(dx) == 2 && (dy == -2 || dy == 1)) continue;
          if (world.get(x + dx, top + dy) == 0) world.set(x + dx, top + dy, LEAVES);
        }
      }
      for (int y = top; y < ground; y++) world.set(x, y, LOG);
    }
  }

  // 出生点上方清空，免得玩家一开局卡在树干里。
  private void clearSpawn(World world, int[] surface, int spawnX) {
    for (int x = spawnX - 1; x <= spawnX + 1; x++) {
      for (int y = 0; y < surface[x]; y++) world.set(x, y, 0);
    }
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }
}
