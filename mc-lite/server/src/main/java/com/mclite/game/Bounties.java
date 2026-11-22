package com.mclite.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.mclite.api.Dtos;
import com.mclite.content.ContentRegistry;
import com.mclite.content.MobDef;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

// 悬赏板惰性结算：不开后台线程，每次读档/请求时清过期单、清旧档残留标题、按目标去重，再补满 slots 张。
// 优先派玩家已经杀过的怪，杀过的种类不够一板时用 fallbackZones 的入门怪补满；标题拼成「猎杀 N 只 X」，
// 数量/时限/酬劳取该怪 zone 对应的 tiers 档；Boss 永不进池。
public final class Bounties {

  private Bounties() {}

  public record Board(List<Dtos.Bounty> bounties, int seq, boolean changed) {}

  public static int seqOf(Integer seq) {
    return seq == null ? 0 : seq;
  }

  public static Board initial(ContentRegistry content, long seed, long now) {
    return refresh(List.of(), 0, seed, now, Map.of(), content);
  }

  public static Board refresh(List<Dtos.Bounty> current, int seq, long seed, long now,
                              Map<String, Integer> kills, ContentRegistry content) {
    List<Dtos.Bounty> kept = new ArrayList<>();
    Set<String> targets = new HashSet<>();
    boolean changed = false;
    for (Dtos.Bounty bounty : current) {
      String target = targetOf(content, bounty);
      if (bounty.expiresAt() <= now || target == null || !targets.add(target)) {
        changed = true;
        continue;
      }
      kept.add(bounty);
    }
    List<MobDef> pool = pool(content, kills, targets);
    int next = seq;
    while (kept.size() < content.bountySlots() && !pool.isEmpty()) {
      MobDef picked = pool.remove(new Random(seed * 31 + next).nextInt(pool.size()));
      kept.add(offer(content, picked, "b" + next, now));
      next++;
      changed = true;
    }
    return new Board(kept, next, changed);
  }

  // 校验旧单：目标必须是怪物表里的非 Boss 怪，且数量与标题都跟当前 tiers 档一致。
  // 于是上一版留下的「送 N 个材料」标题会被当成未知单清掉换新单。
  private static String targetOf(ContentRegistry content, Dtos.Bounty bounty) {
    if (bounty.wants().size() != 1) return null;
    Map.Entry<String, Integer> want = bounty.wants().entrySet().iterator().next();
    MobDef mob = content.mob(want.getKey());
    if (!huntable(content, mob)) return null;
    int count = tier(content, mob.zone()).get("count").asInt();
    if (count != want.getValue() || !title(mob, count).equals(bounty.title())) return null;
    return mob.key();
  }

  private static List<MobDef> pool(ContentRegistry content, Map<String, Integer> kills, Set<String> exclude) {
    List<MobDef> pool = new ArrayList<>();
    for (MobDef mob : content.mobs()) {
      if (huntable(content, mob) && !exclude.contains(mob.key()) && kills.getOrDefault(mob.key(), 0) > 0) {
        pool.add(mob);
      }
    }
    Set<Integer> zones = fallbackZones(content);
    // 只在杀过的种类凑不满一板时才拿入门怪垫底，老玩家的板子上不该再出现史莱姆。
    if (pool.size() >= content.bountySlots()) return pool;
    for (MobDef mob : content.mobs()) {
      if (huntable(content, mob) && !exclude.contains(mob.key()) && zones.contains(mob.zone()) && !pool.contains(mob)) {
        pool.add(mob);
      }
    }
    return pool;
  }

  private static boolean huntable(ContentRegistry content, MobDef mob) {
    return mob != null && !mob.boss() && tier(content, mob.zone()) != null;
  }

  private static Dtos.Bounty offer(ContentRegistry content, MobDef mob, String id, long now) {
    JsonNode tier = tier(content, mob.zone());
    int count = tier.get("count").asInt();
    Map<String, Integer> wants = new LinkedHashMap<>();
    wants.put(mob.key(), count);
    long expiresAt = now + tier.get("minutes").asLong() * 60_000;
    return new Dtos.Bounty(id, title(mob, count), wants, content.counts(tier.get("reward")), expiresAt);
  }

  private static String title(MobDef mob, int count) {
    return "猎杀 " + count + " 只 " + mob.name();
  }

  private static JsonNode tier(ContentRegistry content, int zone) {
    for (JsonNode tier : content.bounties().get("tiers")) {
      if (tier.get("zone").asInt() == zone) return tier;
    }
    return null;
  }

  private static Set<Integer> fallbackZones(ContentRegistry content) {
    Set<Integer> zones = new LinkedHashSet<>();
    for (JsonNode zone : content.bounties().get("fallbackZones")) {
      zones.add(zone.asInt());
    }
    return zones;
  }
}
