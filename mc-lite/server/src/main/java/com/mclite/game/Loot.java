package com.mclite.game;

import com.mclite.content.ContentRegistry;
import com.mclite.content.MobDef;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

// 掉落结算在服务端：客户端只报「我杀了某怪」，掉什么、掉几个由这张表掷出来。
public final class Loot {

  private Loot() {}

  public static Map<String, Integer> roll(ContentRegistry content, MobDef mob, Random rnd) {
    Map<String, Integer> drops = new LinkedHashMap<>();
    for (MobDef.Drop drop : mob.drops()) {
      if (content.item(drop.item()) == null) continue;
      if (rnd.nextDouble() > drop.chance()) continue;
      int count = drop.min() + (drop.max() > drop.min() ? rnd.nextInt(drop.max() - drop.min() + 1) : 0);
      if (count > 0) drops.merge(drop.item(), count, Integer::sum);
    }
    return drops;
  }
}
