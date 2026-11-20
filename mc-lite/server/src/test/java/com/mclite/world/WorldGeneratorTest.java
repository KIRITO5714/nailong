package com.mclite.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorldGeneratorTest {

  private final WorldGenerator generator = new WorldGenerator();

  @Test
  void sameSeedProducesIdenticalWorld() {
    World a = generator.generate(42L, 160, 96);
    World b = generator.generate(42L, 160, 96);
    assertThat(a.encode()).isEqualTo(b.encode());
    assertThat(generator.generate(7L, 160, 96).encode()).isNotEqualTo(a.encode());
  }

  @Test
  void bottomRowsAreBedrockAndSurfaceLineIsTheGrassLayer() {
    World world = generator.generate(42L, 160, 96);
    int[] surface = world.surface();
    for (int x = 0; x < world.width; x++) {
      assertThat(world.get(x, world.height - 1)).isEqualTo(10);
      assertThat(world.get(x, world.height - 3)).isEqualTo(10);
      // 地形线只认土层：树冠再高也不影响它，树冠下的空气仍然是天空。
      assertThat(world.get(x, surface[x])).isEqualTo(1);
      assertThat(world.get(x, surface[x] + 1)).isEqualTo(2);
      assertThat(surface[x]).isBetween(6, world.height - 24);
    }
  }

  @Test
  void everyOreTypeExistsAndOnlyInsideStone() {
    World world = generator.generate(42L, 160, 96);
    int[] counts = new int[13];
    for (int id : world.blocks) counts[id]++;
    assertThat(counts[6]).isPositive();
    assertThat(counts[7]).isPositive();
    assertThat(counts[8]).isPositive();
    assertThat(counts[9]).isPositive();
    assertThat(counts[4]).isPositive();
    assertThat(counts[5]).isPositive();
    assertThat(counts[1] + counts[2] + counts[3] + counts[6] + counts[7] + counts[8] + counts[9] + counts[10])
        .isGreaterThan(world.width * world.height / 2);
  }

  @Test
  void deeperOresAreRarerAndLower() {
    World world = generator.generate(42L, 160, 96);
    assertThat(avgDepth(world, 6)).isLessThan(avgDepth(world, 7));
    assertThat(avgDepth(world, 7)).isLessThan(avgDepth(world, 9));
  }

  @Test
  void spawnColumnIsClearAboveGround() {
    World world = generator.generate(42L, 160, 96);
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
    World world = generator.generate(99L, 160, 96);
    World copy = new World(world.width, world.height,
        World.decode(world.encode(), world.width, world.height),
        World.decodeSurface(world.encodeSurface(), world.width));
    assertThat(copy.blocks).isEqualTo(world.blocks);
    assertThat(copy.surface()).isEqualTo(world.surface());
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
