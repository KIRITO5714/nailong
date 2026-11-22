package com.mclite.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorldGeneratorTest {

  private static final int WIDTH = 480;
  private static final int HEIGHT = 96;

  private final WorldGenerator generator = new WorldGenerator();

  @Test
  void sameSeedProducesIdenticalWorld() {
    World a = generator.generate(42L, WIDTH, HEIGHT);
    World b = generator.generate(42L, WIDTH, HEIGHT);
    assertThat(a.encode()).isEqualTo(b.encode());
    assertThat(generator.generate(7L, WIDTH, HEIGHT).encode()).isNotEqualTo(a.encode());
  }

  @Test
  void bottomRowsAreBedrockAndSurfaceLineIsTheGrassLayer() {
    World world = generator.generate(42L, WIDTH, HEIGHT);
    int[] surface = world.surface();
    for (int x = 0; x < world.width; x++) {
      assertThat(world.get(x, world.height - 1)).isEqualTo(10);
      assertThat(world.get(x, world.height - 3)).isEqualTo(10);
      // 地形线只认土层：树冠和地表荧光晶再高也不影响它。
      assertThat(world.get(x, surface[x])).isEqualTo(1);
      // 地表下一格是泥土或沙囊，两者都算土层。
      assertThat(world.get(x, surface[x] + 1)).isIn(2, 13);
      assertThat(surface[x]).isBetween(6, world.height - 24);
    }
  }

  @Test
  void everyOreTypeExistsAndOnlyInsideStone() {
    World world = generator.generate(42L, WIDTH, HEIGHT);
    int[] counts = new int[16];
    for (int id : world.blocks) counts[id]++;
    assertThat(counts[6]).isPositive();
    assertThat(counts[7]).isPositive();
    assertThat(counts[8]).isPositive();
    assertThat(counts[9]).isPositive();
    assertThat(counts[13]).isPositive();
    assertThat(counts[14]).isPositive();
    assertThat(counts[4]).isPositive();
    assertThat(counts[5]).isPositive();
    assertThat(counts[1] + counts[2] + counts[3] + counts[6] + counts[7] + counts[8] + counts[9] + counts[10])
        .isGreaterThan(world.width * world.height / 2);
  }

  @Test
  void deeperOresAreRarerAndLower() {
    World world = generator.generate(42L, WIDTH, HEIGHT);
    assertThat(avgDepth(world, 6)).isLessThan(avgDepth(world, 7));
    assertThat(avgDepth(world, 7)).isLessThan(avgDepth(world, 9));
  }

  // 地图拉长到 480 列后，地形起伏和树量都要跟着变多，否则「走得远」等于「走得空」。
  @Test
  void longMapHasMoreTerrainAndTreesThanTheOldOne() {
    World longWorld = generator.generate(42L, WIDTH, HEIGHT);
    World shortWorld = generator.generate(42L, 160, HEIGHT);
    assertThat(longWorld.width).isEqualTo(480);
    assertThat(spread(longWorld.surface())).isGreaterThan(8);
    assertThat(count(longWorld, 4)).isGreaterThan(count(shortWorld, 4));
    assertThat(count(longWorld, 5)).isGreaterThan(count(shortWorld, 5));
  }

  // 世界不能挖，地表就是唯一通路：相邻列最多差 1 格（一级跳就能翻），
  // 地表以上只允许树和荧光晶这两种「可穿过」的风景方块，否则长地图会被截断。
  @Test
  void surfaceIsWalkableFromEndToEnd() {
    World world = generator.generate(42L, WIDTH, HEIGHT);
    int[] surface = world.surface();
    for (int x = 1; x < world.width; x++) {
      assertThat(Math.abs(surface[x] - surface[x - 1])).isLessThanOrEqualTo(1);
    }
    for (int x = 0; x < world.width; x++) {
      for (int y = 0; y < surface[x]; y++) assertThat(world.get(x, y)).isIn(0, 4, 5, 15);
    }
  }

  // 荧光晶只长在地表空气里，且离出生点足够远，玩家不会一开局被它挡住。
  @Test
  void glowCrystalsStandOnTheSurfaceAwayFromSpawn() {
    World world = generator.generate(42L, WIDTH, HEIGHT);
    int spawnX = world.width / 2;
    int found = 0;
    for (int x = 0; x < world.width; x++) {
      for (int y = 0; y < world.height; y++) {
        if (world.get(x, y) != 15) continue;
        found++;
        assertThat(Math.abs(x - spawnX)).isGreaterThanOrEqualTo(5);
        assertThat(world.get(x, y + 1)).isIn(1, 15);
      }
    }
    assertThat(found).isPositive();
  }

  @Test
  void spawnColumnIsClearAboveGround() {
    World world = generator.generate(42L, WIDTH, HEIGHT);
    int[] surface = world.surface();
    int spawnX = world.width / 2;
    for (int x = spawnX - 1; x <= spawnX + 1; x++) {
      for (int y = 0; y < surface[x]; y++) assertThat(world.get(x, y)).isZero();
    }
    double[] spawn = generator.spawn(world);
    assertThat(spawn[0]).isEqualTo(spawnX + 0.5);
    assertThat(spawn[1]).isLessThan(surface[spawnX]);
  }

  @Test
  void encodeDecodeRoundTrip() {
    World world = generator.generate(99L, WIDTH, HEIGHT);
    World copy = new World(world.width, world.height,
        World.decode(world.encode(), world.width, world.height),
        World.decodeSurface(world.encodeSurface(), world.width));
    assertThat(copy.blocks).isEqualTo(world.blocks);
    assertThat(copy.surface()).isEqualTo(world.surface());
    // 地形线一列一个字符，480 列仍在 surface_line 的 512 长度限制内。
    assertThat(world.encodeSurface()).hasSize(WIDTH);
  }

  @Test
  void loosePocketsRestOnSolidGround() {
    World world = generator.generate(42L, WIDTH, HEIGHT);
    int loose = 0;
    for (int y = 0; y < world.height - 1; y++) {
      for (int x = 0; x < world.width; x++) {
        int id = world.get(x, y);
        if (id == 13 || id == 14) {
          loose++;
          assertThat(world.get(x, y + 1)).isNotZero();
        }
      }
    }
    assertThat(loose).isPositive();
  }

  private int count(World world, int id) {
    int total = 0;
    for (int block : world.blocks) if (block == id) total++;
    return total;
  }

  private int spread(int[] surface) {
    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    for (int y : surface) {
      min = Math.min(min, y);
      max = Math.max(max, y);
    }
    return max - min;
  }

  private double avgDepth(World world, int id) {
    long sum = 0;
    int count = 0;
    for (int y = 0; y < world.height; y++) {
      for (int x = 0; x < world.width; x++) {
        if (world.get(x, y) == id) {
          sum += y;
          count++;
        }
      }
    }
    return count == 0 ? -1 : (double) sum / count;
  }
}
