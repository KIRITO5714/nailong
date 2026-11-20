package com.mclite.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.mclite.api.Dtos;
import com.mclite.content.ContentRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// 地下邮局：同时挂 slots 张限时委托。惰性结算——不开后台线程，
// 每次读档/请求时清掉过期单再补新单；单号递增 + 世界种子决定抽哪张模板，可复现。
public final class OrderBoard {

  public record Board(List<Dtos.Order> orders, int seq, boolean changed) {}

  private OrderBoard() {}

  // 旧存档没有单号列，读出来是 null，按 0 起算。
  public static int seqOf(Integer seq) {
    return seq == null ? 0 : seq;
  }

  public static Board initial(ContentRegistry content, long seed, long now) {
    return refresh(List.of(), 0, seed, now, content);
  }

  public static Board refresh(List<Dtos.Order> current, int seq, long seed, long now, ContentRegistry content) {
    // 同标题只留一张：随机抽模板可能撞车，挂两张一样的单看起来很坏。
    List<Dtos.Order> orders = new ArrayList<>();
    for (Dtos.Order order : current) {
      if (order.expiresAt() > now && orders.stream().noneMatch(k -> k.title().equals(order.title()))) {
        orders.add(order);
      }
    }
    boolean changed = orders.size() != current.size();
    int next = seq;
    while (orders.size() < content.orderSlots()) {
      String avoid = orders.isEmpty() ? null : orders.get(orders.size() - 1).title();
      orders.add(offer(content, seed, next, now, avoid));
      next++;
      changed = true;
    }
    return new Board(List.copyOf(orders), next, changed);
  }

  public static Dtos.Order offer(ContentRegistry content, long seed, int seq, long now, String avoidTitle) {
    JsonNode templates = content.orders().get("templates");
    int index = new Random(seed * 31 + seq).nextInt(templates.size());
    if (avoidTitle != null && templates.get(index).get("title").asText().equals(avoidTitle)) {
      index = (index + 1) % templates.size();
    }
    JsonNode t = templates.get(index);
    return new Dtos.Order("o" + seq, t.get("title").asText(),
        content.counts(t.get("wants")), content.counts(t.get("reward")),
        now + t.get("minutes").asLong() * 60_000);
  }
}