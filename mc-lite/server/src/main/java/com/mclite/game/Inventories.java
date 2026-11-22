package com.mclite.game;

import com.mclite.content.ContentRegistry;
import com.mclite.content.ItemDef;
import com.mclite.content.MobDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 背包公共操作：掉落、合成、悬赏奖励都走这里。扣到 0 就删键，免得背包里留一堆 0。
public final class Inventories {

  private Inventories() {}

  public static void add(Map<String, Integer> inventory, String key, int count) {
    inventory.merge(key, count, Integer::sum);
  }

  public static void take(Map<String, Integer> inventory, String key, int count) {
    int left = inventory.getOrDefault(key, 0) - count;
    if (left > 0) inventory.put(key, left);
    else inventory.remove(key);
  }

  // 武器等级 = 背包里最高级的剑，不做手动装备，少一层 UI 也少一类 bug。
  public static int weaponTier(ContentRegistry content, Map<String, Integer> inventory) {
    int tier = 0;
    for (Map.Entry<String, Integer> entry : inventory.entrySet()) {
      ItemDef item = content.item(entry.getKey());
      if (item != null && item.weapon() != null && entry.getValue() > 0) tier = Math.max(tier, item.weapon());
    }
    return tier;
  }

  public static List<String> missing(ContentRegistry content, Map<String, Integer> inventory, Map<String, Integer> wants) {
    List<String> missing = new ArrayList<>();
    for (Map.Entry<String, Integer> need : wants.entrySet()) {
      int have = inventory.getOrDefault(need.getKey(), 0);
      ItemDef item = content.item(need.getKey());
      if (have < need.getValue() && item != null) missing.add(item.name() + "×" + (need.getValue() - have));
    }
    return missing;
  }

  public static List<String> missingKills(ContentRegistry content, Map<String, Integer> kills, Map<String, Integer> wants) {
    List<String> missing = new ArrayList<>();
    for (Map.Entry<String, Integer> need : wants.entrySet()) {
      int have = kills.getOrDefault(need.getKey(), 0);
      MobDef mob = content.mob(need.getKey());
      if (have < need.getValue() && mob != null) missing.add(mob.name() + "×" + (need.getValue() - have));
    }
    return missing;
  }

  public static String describe(ContentRegistry content, Map<String, Integer> bundle) {
    List<String> parts = new ArrayList<>();
    bundle.forEach((key, count) -> {
      ItemDef item = content.item(key);
      if (item != null) parts.add(item.name() + "×" + count);
    });
    return String.join("、", parts);
  }

  public static String describeMobs(ContentRegistry content, Map<String, Integer> bundle) {
    List<String> parts = new ArrayList<>();
    bundle.forEach((key, count) -> {
      MobDef mob = content.mob(key);
      if (mob != null) parts.add(mob.name() + "×" + count);
    });
    return String.join("、", parts);
  }
}
