package com.mclite.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mclite.api.Dtos;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 测试用的存档改写工具：绕过服务层直接动 H2 里的 JSON 列，用来伪造旧存档、塞击杀数和重写悬赏板。
class Saves {

  private final GameService game;
  private final WorldRepository repo;
  private final ObjectMapper mapper;
  private final String id;

  Saves(GameService game, WorldRepository repo, ObjectMapper mapper, String id) {
    this.game = game;
    this.repo = repo;
    this.mapper = mapper;
    this.id = id;
  }

  String id() {
    return id;
  }

  // 服务端限频比客户端最短冷却（0.32s）更松，测试里按限频间隔等一下再挥下一刀。
  Dtos.Mutation kill(String mob) {
    try {
      Thread.sleep(260);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
    return game.loot(id, mob);
  }

  void expectCode(Runnable action, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(GameException.class, e -> assertThat(e.getCode()).isEqualTo(code));
  }

  void give(String item, int count) {
    WorldEntity entity = entity();
    Map<String, Integer> inventory = read(entity.getInventoryJson());
    inventory.merge(item, count, Integer::sum);
    entity.setInventoryJson(write(inventory));
    repo.save(entity);
  }

  void giveKills(String mob, int count) {
    WorldEntity entity = entity();
    Map<String, Integer> kills = read(entity.getKillsJson());
    kills.merge(mob, count, Integer::sum);
    entity.setKillsJson(write(kills));
    repo.save(entity);
  }

  List<Dtos.Bounty> storedBounties() {
    String json = entity().getOrdersJson();
    try {
      return json == null || json.isBlank() ? List.of()
          : mapper.readValue(json, new TypeReference<List<Dtos.Bounty>>() {});
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  void rewriteBounties(List<Dtos.Bounty> bounties) {
    WorldEntity entity = entity();
    entity.setOrdersJson(write(bounties));
    repo.save(entity);
  }

  // 旧存档没有 cleared / playerHp / orderSeq 这几列，读出来是 null。
  void simulateLegacySave() {
    WorldEntity entity = entity();
    entity.setCleared(null);
    entity.setPlayerHp(null);
    entity.setOrderSeq(null);
    repo.save(entity);
  }

  private WorldEntity entity() {
    return repo.findById(id).orElseThrow();
  }

  private Map<String, Integer> read(String json) {
    try {
      return json == null || json.isBlank() ? new LinkedHashMap<>()
          : mapper.readValue(json, new TypeReference<LinkedHashMap<String, Integer>>() {});
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String write(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
