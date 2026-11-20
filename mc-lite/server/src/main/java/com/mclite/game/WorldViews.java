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

// 存档 JSON 读写 + 视图组装。订单在这里惰性结算：读档/返回视图时清过期单、补新单并落库。
@Component
public class WorldViews {

  private final ContentRegistry content;
  private final WorldRepository repo;
  private final ObjectMapper mapper;
  private final double reach;

  public WorldViews(ContentRegistry content, WorldRepository repo, ObjectMapper mapper,
                    @org.springframework.beans.factory.annotation.Value("${mclite.world.reach}") double reach) {
    this.content = content;
    this.repo = repo;
    this.mapper = mapper;
    this.reach = reach;
  }

  public Map<String, Integer> readInventory(String json) {
    if (json == null || json.isBlank()) return new LinkedHashMap<>();
    return read(json, new TypeReference<LinkedHashMap<String, Integer>>() {});
  }

  public String writeInventory(Map<String, Integer> inventory) {
    return write(inventory);
  }

  public List<Dtos.Order> readOrders(String json) {
    if (json == null || json.isBlank()) return List.of();
    return read(json, new TypeReference<List<Dtos.Order>>() {});
  }

  public void persistBoard(WorldEntity entity, OrderBoard.Board board) {
    if (!board.changed()) return;
    entity.setOrdersJson(write(board.orders()));
    entity.setOrderSeq(board.seq());
  }

  public Dtos.WorldView view(WorldEntity entity, World world, Map<String, Integer> inventory) {
    long now = System.currentTimeMillis();
    OrderBoard.Board board = OrderBoard.refresh(readOrders(entity.getOrdersJson()), OrderBoard.seqOf(entity.getOrderSeq()),
        entity.getSeed(), now, content);
    if (board.changed()) {
      persistBoard(entity, board);
      entity.setUpdatedAt(Instant.now());
      repo.save(entity);
    }
    int tier = Inventories.tier(content, inventory);
    return new Dtos.WorldView(entity.getId(), entity.getName(), entity.getSeed(), world.width, world.height,
        world.blocks, world.surface(), new Dtos.Player(entity.getPlayerX(), entity.getPlayerY()),
        inventory, tier, content.tool(tier).name(), reach, board.orders(), now);
  }

  public Dtos.Mutation mutation(Map<String, Integer> inventory, List<Dtos.BlockChange> changes,
                                String drop, String message, Dtos.Player player, List<Dtos.Order> orders) {
    int tier = Inventories.tier(content, inventory);
    return new Dtos.Mutation(true, changes, inventory, tier, content.tool(tier).name(), drop, message, player, orders);
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