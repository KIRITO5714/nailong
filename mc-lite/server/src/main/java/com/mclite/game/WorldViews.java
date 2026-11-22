package com.mclite.game;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mclite.api.Dtos;
import com.mclite.content.ContentRegistry;
import com.mclite.world.World;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

// 存档 JSON 读写 + 视图组装。悬赏在这里惰性结算：读档/返回视图时清过期单、补新单并落库。
@Component
public class WorldViews {

  public static final int MAX_HP = 100;

  private final ContentRegistry content;
  private final WorldRepository repo;
  private final ObjectMapper mapper;

  public WorldViews(ContentRegistry content, WorldRepository repo, ObjectMapper mapper) {
    this.content = content;
    this.repo = repo;
    this.mapper = mapper;
  }

  public Map<String, Integer> readInventory(String json) {
    if (json == null || json.isBlank()) return new LinkedHashMap<>();
    return read(json, new TypeReference<LinkedHashMap<String, Integer>>() {});
  }

  public String writeInventory(Map<String, Integer> inventory) {
    return write(inventory);
  }

  public Map<String, Integer> readKills(String json) {
    if (json == null || json.isBlank()) return new LinkedHashMap<>();
    return read(json, new TypeReference<LinkedHashMap<String, Integer>>() {});
  }

  public String writeKills(Map<String, Integer> kills) {
    return write(kills);
  }

  public List<Dtos.Bounty> readBounties(String json) {
    if (json == null || json.isBlank()) return List.of();
    return read(json, new TypeReference<List<Dtos.Bounty>>() {});
  }

  public void persistBoard(WorldEntity entity, Bounties.Board board) {
    if (!board.changed()) return;
    entity.setOrdersJson(write(board.bounties()));
    entity.setOrderSeq(board.seq());
  }

  public int hpOf(WorldEntity entity) {
    Integer hp = entity.getPlayerHp();
    if (hp == null) return MAX_HP;
    return Math.max(0, Math.min(MAX_HP, hp));
  }

  public Dtos.WorldView view(WorldEntity entity, World world, Map<String, Integer> inventory) {
    long now = System.currentTimeMillis();
    Bounties.Board board = Bounties.refresh(readBounties(entity.getOrdersJson()), Bounties.seqOf(entity.getOrderSeq()),
        entity.getSeed(), now, content);
    if (board.changed()) {
      persistBoard(entity, board);
      entity.setUpdatedAt(Instant.now());
      repo.save(entity);
    }
    int tier = Inventories.weaponTier(content, inventory);
    return new Dtos.WorldView(entity.getId(), entity.getName(), entity.getSeed(), world.width, world.height,
        world.blocks, world.surface(), new Dtos.Player(entity.getPlayerX(), entity.getPlayerY()),
        inventory, readKills(entity.getKillsJson()), hpOf(entity),
        tier, content.weapon(tier).name(), board.bounties(), now);
  }

  public Dtos.Mutation mutation(Map<String, Integer> inventory, Map<String, Integer> kills,
                                String message, List<Dtos.Bounty> bounties) {
    int tier = Inventories.weaponTier(content, inventory);
    return new Dtos.Mutation(true, inventory, kills, tier, content.weapon(tier).name(), message, bounties);
  }

  private <T> T read(String json, TypeReference<T> type) {
    try {
      return mapper.readValue(json, type);
    } catch (Exception e) {
      throw new IllegalStateException("存档读不出来", e);
    }
  }

  private String write(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("存档写不进去", e);
    }
  }
}
