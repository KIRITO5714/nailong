package com.mclite.game;

import com.mclite.api.Dtos;
import com.mclite.content.ContentRegistry;
import com.mclite.world.World;
import java.util.ArrayList;
import java.util.List;

// 塌方：松散方块（沙/碎石）失去支撑后整列下坠压实。每列自底向上扫一遍，
// 遇到实心非松散块就把落点重置到它头顶——所以矿洞里留一根柱子就能挡住整列塌方。
public final class Collapse {

  private Collapse() {}

  public static List<Dtos.BlockChange> settle(World world, ContentRegistry content) {
    List<Dtos.BlockChange> changes = new ArrayList<>();
    for (int x = 0; x < world.width; x++) {
      int target = world.height - 1;
      for (int y = world.height - 1; y >= 0; y--) {
        int id = world.get(x, y);
        if (id == 0) continue;
        if (!content.block(id).loose()) {
          target = y - 1;
          continue;
        }
        if (y != target) {
          world.set(x, target, id);
          world.set(x, y, 0);
          changes.add(new Dtos.BlockChange(x, y, 0));
          changes.add(new Dtos.BlockChange(x, target, id));
        }
        target--;
      }
    }
    return changes;
  }
}