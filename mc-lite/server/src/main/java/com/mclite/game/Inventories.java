package com.mclite.game;

import com.mclite.content.ContentRegistry;
import com.mclite.content.ItemDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 背包公共操作：掉落、合成、交付都走这里。扣到 0 就删键，免得背包里留一堆 0。
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

  // 工具等级 = 背包里最高级的镐，不做手动装备，少一层 UI 也少一类 bug。
  public static int tier(ContentRegistry content, Map<String, Integer> inventory) {
    int tier = 0;
    for (Map.Entry<String, Integer> entry : inventory.entrySet()) {
      ItemDef item = content.item(entry.getKey());
      if (item != null && item.tool() != null && entry.getValue() > 0) tier = Math.max(tier, item.tool());
    }
    return tier;
  }

  public static List<String> missing(ContentRegistry content, Map<String, Integer> inventory, Map<String, Integer> wants) {
    List<String> missing = new ArrayList<>();
    for (Map.Entry<String, Integer> need : wants.entrySet()) {
      int have = inventory.getOrDefault(need.getKey(), 0);
      if (have < need.getValue()) missing.add(content.item(need.getKey()).name() + "×" + (need.getValue() - have));
    }
    return missing;
  }

  public static String describe(ContentRegistry content, Map<String, Integer> bundle) {
    List<String> parts = new ArrayList<>();
    bundle.forEach((key, count) -> parts.add(content.item(key).name() + "×" + count));
    return String.join("、", parts);
  }
}