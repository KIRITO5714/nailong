package com.mclite.game;

import com.mclite.api.Dtos;
import com.mclite.world.World;
import java.util.List;

// 玩家 AABB（宽 0.6 高 1.8，坐标是中心）。放置不能压到自己，塌方埋到自己时要顶出来。
public final class PlayerBox {

  public static final double HALF_W = 0.3;
  public static final double HALF_H = 0.9;

  private PlayerBox() {}

  public static boolean overlaps(double px, double py, int x, int y) {
    return x < px + HALF_W && x + 1 > px - HALF_W && y < py + HALF_H && y + 1 > py - HALF_H;
  }

  // 塌方落点压到玩家时，把他放到所跨列最高实心堆的头顶，而不是埋在里面。
  public static double[] unbury(World world, double px, double py, List<Dtos.BlockChange> changes) {
    boolean buried = changes.stream().anyMatch(c -> c.id() != 0 && overlaps(px, py, c.x(), c.y()));
    if (!buried) return null;
    int top = world.height;
    for (int x = (int) Math.floor(px - HALF_W); x <= (int) Math.floor(px + HALF_W); x++) {
      for (int y = 0; y < world.height; y++) {
        if (world.solid(x, y)) {
          top = Math.min(top, y);
          break;
        }
      }
    }
    return new double[] {px, Math.max(HALF_H, top - HALF_H - 0.02)};
  }
}