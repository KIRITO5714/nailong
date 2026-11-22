package com.mclite.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.mclite.api.Dtos;
import com.mclite.content.ContentRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

// 悬赏板惰性结算：不开后台线程，每次读档/请求时清过期单、清旧版残留标题、去重，再补满 slots 张。
public final class Bounties {

  private Bounties() {}

  public record Board(List<Dtos.Bounty> bounties, int seq, boolean changed) {}

  public static int seqOf(Integer seq) {
    return seq == null ? 0 : seq;
  }

  public static Board initial(ContentRegistry content, long seed, long now) {
    return refresh(List.of(), 0, seed, now, content);
  }

  public static Board refresh(List<Dtos.Bounty> current, int seq, long seed, long now, ContentRegistry content) {
    Set<String> titles = knownTitles(content);
    List<Dtos.Bounty> kept = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    boolean changed = false;
    for (Dtos.Bounty bounty : current) {
      if (bounty.expiresAt() <= now || !titles.contains(bounty.title()) || !seen.add(bounty.title())) {
        changed = true;
        continue;
      }
      kept.add(bounty);
    }
    int next = seq;
    String lastTitle = kept.isEmpty() ? null : kept.get(kept.size() - 1).title();
    while (kept.size() < content.bountySlots()) {
      Dtos.Bounty offered = offer(content, seed, next, now, lastTitle);
      kept.add(offered);
      lastTitle = offered.title();
      next++;
      changed = true;
    }
    return new Board(kept, next, changed);
  }

  private static Dtos.Bounty offer(ContentRegistry content, long seed, int seq, long now, String avoidTitle) {
    JsonNode templates = content.bounties().get("templates");
    int index = new Random(seed * 31 + seq).nextInt(templates.size());
    JsonNode picked = templates.get(index);
    if (picked.get("title").asText().equals(avoidTitle)) {
      picked = templates.get((index + 1) % templates.size());
    }
    long expiresAt = now + picked.get("minutes").asLong() * 60_000;
    return new Dtos.Bounty("b" + seq, picked.get("title").asText(),
        content.counts(picked.get("wants")), content.counts(picked.get("reward")), expiresAt);
  }

  private static Set<String> knownTitles(ContentRegistry content) {
    Set<String> titles = new HashSet<>();
    for (JsonNode template : content.bounties().get("templates")) {
      titles.add(template.get("title").asText());
    }
    return titles;
  }
}
