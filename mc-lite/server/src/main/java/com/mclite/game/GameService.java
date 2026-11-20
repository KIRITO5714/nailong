package com.mclite.game;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mclite.api.Dtos;
import com.mclite.content.BlockDef;
import com.mclite.content.ContentRegistry;
import com.mclite.content.ItemDef;
import com.mclite.content.RecipeDef;
import com.mclite.world.World;
import com.mclite.world.WorldGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// 规则全部在服务端判定：客户端只发意图，工具等级够不够、材料够不够、距离远不远都由这里说了算。
@Service
public class GameService {

  private static final double HALF_W = 0.3;
  private static final double HALF_H = 0.9;

  private final ContentRegistry content;
  private final WorldRepository repo;
  private final ObjectMapper mapper;
  private final WorldGenerator generator = new WorldGenerator();
  private final Map<String, Object> locks = new ConcurrentHashMap<>();
  private final int width;
  private final int height;
  private final double reach;

  public GameService(ContentRegistry content, WorldRepository repo, ObjectMapper mapper,
                     @Value("${mclite.world.width}") int width,
                     @Value("${mclite.world.height}") int height,
                     @Value("${mclite.world.reach}") double reach) {
    this.content = content;
    this.repo = repo;
    this.mapper = mapper;
    this.width = width;
    this.height = height;
    this.reach = reach;
  }

  public Dtos.WorldView create(String name, Long seed) {
    long actualSeed = seed != null ? seed : new Random().nextLong();
    World world = generator.generate(actualSeed, width, height);
    double[] spawn = generator.spawn(world);
    WorldEntity entity = new WorldEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setName(name == null || name.isBlank() ? "新世界" : name.trim());
    entity.setSeed(actualSeed);
    entity.setWidth(width);
    entity.setHeight(height);
    entity.setBlocks(world.encode());
    entity.setSurfaceLine(world.encodeSurface());
    entity.setInventoryJson("{}");
    entity.setPlayerX(spawn[0]);
    entity.setPlayerY(spawn[1]);
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());
    repo.save(entity);
    return view(entity, world, new LinkedHashMap<>());
  }

  public List<Dtos.WorldSummary> list() {
    return repo.findAllByOrderByUpdatedAtDesc().stream()
        .map(e -> new Dtos.WorldSummary(e.getId(), e.getName(), e.getSeed(), e.getWidth(), e.getHeight(), e.getUpdatedAt()))
        .toList();
  }

  public Dtos.WorldView load(String id) {
    Session s = open(id);
    return view(s.entity(), s.world(), s.inventory());
  }

  public void delete(String id) {
    repo.deleteById(id);
  }

  public Dtos.Mutation mine(String id, int x, int y) {
    synchronized (lock(id)) {
      Session s = open(id);
      if (!s.world().inBounds(x, y)) throw new GameException("OUT_OF_BOUNDS", "那里在世界之外");
      int current = s.world().get(x, y);
      if (current == 0) throw new GameException("NOT_SOLID", "那里是空气，没什么可挖的");
      BlockDef def = content.block(current);
      if (!def.breakable()) throw new GameException("UNBREAKABLE", def.name() + "挖不动");
      checkReach(s, x, y);
      int tier = toolTier(s.inventory());
      if (tier < def.tier()) {
        throw new GameException("TOOL_TOO_WEAK",
            "挖" + def.name() + "需要" + content.tool(def.tier()).name() + "，你现在只有" + content.tool(tier).name());
      }
      s.world().set(x, y, 0);
      String dropName = null;
      if (def.drop() != null) {
        add(s.inventory(), def.drop(), 1);
        dropName = content.item(def.drop()).name();
      }
      save(s);
      String message = dropName == null
          ? def.name() + "碎了一地，什么也没留下"
          : "挖掉" + def.name() + "，背包 +1 " + dropName;
      return mutation(s, List.of(new Dtos.BlockChange(x, y, 0)), dropName, message);
    }
  }

  public Dtos.Mutation place(String id, int x, int y, String itemKey) {
    synchronized (lock(id)) {
      Session s = open(id);
      ItemDef item = content.item(itemKey);
      if (item == null) throw new GameException("UNKNOWN_ITEM", "没有这种物品：" + itemKey);
      if (item.place() == null) throw new GameException("NOT_PLACEABLE", item.name() + "不能放成方块");
      if (s.inventory().getOrDefault(itemKey, 0) <= 0) throw new GameException("NO_ITEM", "背包里没有" + item.name());
      if (!s.world().inBounds(x, y)) throw new GameException("OUT_OF_BOUNDS", "那里在世界之外");
      if (s.world().get(x, y) != 0) throw new GameException("NOT_EMPTY", "那一格已经有方块了");
      checkReach(s, x, y);
      if (overlapsPlayer(s, x, y)) throw new GameException("BLOCKED_BY_PLAYER", "不能把方块放在自己身上");
      take(s.inventory(), itemKey, 1);
      s.world().set(x, y, item.place());
      save(s);
      return mutation(s, List.of(new Dtos.BlockChange(x, y, item.place())), null, "放下了" + item.name());
    }
  }

  public Dtos.Mutation craft(String id, String recipeId) {
    synchronized (lock(id)) {
      Session s = open(id);
      RecipeDef recipe = content.recipe(recipeId);
      if (recipe == null) throw new GameException("UNKNOWN_RECIPE", "没有这个配方：" + recipeId);
      if (recipe.needsWorkbench() && !nearWorkbench(s)) {
        throw new GameException("NEEDS_WORKBENCH", "这个配方要站在工作台旁边才能合成");
      }
      List<String> missing = new ArrayList<>();
      for (Map.Entry<String, Integer> need : recipe.in().entrySet()) {
        int have = s.inventory().getOrDefault(need.getKey(), 0);
        if (have < need.getValue()) {
          missing.add(content.item(need.getKey()).name() + "×" + (need.getValue() - have));
        }
      }
      if (!missing.isEmpty()) throw new GameException("NO_MATERIAL", "材料不够，还差 " + String.join("、", missing));
      int before = toolTier(s.inventory());
      recipe.in().forEach((key, count) -> take(s.inventory(), key, count));
      recipe.out().forEach((key, count) -> add(s.inventory(), key, count));
      int after = toolTier(s.inventory());
      save(s);
      String message = "合成了" + recipe.name();
      if (after > before) message += "，工具升级成" + content.tool(after).name();
      return mutation(s, List.of(), null, message);
    }
  }

  // 物理在客户端跑，服务端只记录位置：一是存档，二是挖掘/放置的距离判定要有依据。
  public Dtos.Ack move(String id, double x, double y) {
    synchronized (lock(id)) {
      Session s = open(id);
      double px = Math.max(HALF_W, Math.min(width - HALF_W, x));
      double py = Math.max(HALF_H, Math.min(height - HALF_H, y));
      s.entity().setPlayerX(px);
      s.entity().setPlayerY(py);
      save(s);
      return new Dtos.Ack(true, new Dtos.Player(px, py));
    }
  }

  private record Session(WorldEntity entity, World world, Map<String, Integer> inventory) {}

  private Object lock(String id) {
    return locks.computeIfAbsent(id, key -> new Object());
  }

  private Session open(String id) {
    WorldEntity entity = repo.findById(id)
        .orElseThrow(() -> new GameException("NOT_FOUND", "世界不存在，可能已经被删掉了"));
    World world = new World(entity.getWidth(), entity.getHeight(),
        World.decode(entity.getBlocks(), entity.getWidth(), entity.getHeight()),
        World.decodeSurface(entity.getSurfaceLine(), entity.getWidth()));
    return new Session(entity, world, readInventory(entity.getInventoryJson()));
  }

  private void save(Session s) {
    s.entity().setBlocks(s.world().encode());
    s.entity().setInventoryJson(writeInventory(s.inventory()));
    s.entity().setUpdatedAt(Instant.now());
    repo.save(s.entity());
  }

  private Map<String, Integer> readInventory(String json) {
    if (json == null || json.isBlank()) return new LinkedHashMap<>();
    try {
      return mapper.readValue(json, new TypeReference<LinkedHashMap<String, Integer>>() {});
    } catch (Exception e) {
      throw new IllegalStateException("背包存档读不出来", e);
    }
  }

  private String writeInventory(Map<String, Integer> inventory) {
    try {
      return mapper.writeValueAsString(inventory);
    } catch (Exception e) {
      throw new IllegalStateException("背包存档写不进去", e);
    }
  }

  private Dtos.WorldView view(WorldEntity e, World world, Map<String, Integer> inventory) {
    int tier = toolTier(inventory);
    return new Dtos.WorldView(e.getId(), e.getName(), e.getSeed(), world.width, world.height,
        world.blocks, world.surface(), new Dtos.Player(e.getPlayerX(), e.getPlayerY()),
        inventory, tier, content.tool(tier).name(), reach);
  }

  private Dtos.Mutation mutation(Session s, List<Dtos.BlockChange> changes, String drop, String message) {
    int tier = toolTier(s.inventory());
    return new Dtos.Mutation(true, changes, s.inventory(), tier, content.tool(tier).name(), drop, message);
  }

  // 工具等级 = 背包里最高级的镐，不做手动装备，少一层 UI 也少一类 bug。
  private int toolTier(Map<String, Integer> inventory) {
    int tier = 0;
    for (Map.Entry<String, Integer> entry : inventory.entrySet()) {
      ItemDef item = content.item(entry.getKey());
      if (item != null && item.tool() != null && entry.getValue() > 0) tier = Math.max(tier, item.tool());
    }
    return tier;
  }

  private void checkReach(Session s, int x, int y) {
    double dx = x + 0.5 - s.entity().getPlayerX();
    double dy = y + 0.5 - s.entity().getPlayerY();
    if (Math.hypot(dx, dy) > reach) throw new GameException("OUT_OF_RANGE", "太远了，走近一点再动手");
  }

  private boolean overlapsPlayer(Session s, int x, int y) {
    double px = s.entity().getPlayerX();
    double py = s.entity().getPlayerY();
    return x < px + HALF_W && x + 1 > px - HALF_W && y < py + HALF_H && y + 1 > py - HALF_H;
  }

  private boolean nearWorkbench(Session s) {
    int bench = content.workbenchBlockId();
    int px = (int) Math.floor(s.entity().getPlayerX());
    int py = (int) Math.floor(s.entity().getPlayerY());
    int r = (int) Math.ceil(reach);
    for (int y = py - r; y <= py + r; y++) {
      for (int x = px - r; x <= px + r; x++) {
        if (s.world().get(x, y) == bench) return true;
      }
    }
    return false;
  }

  private void add(Map<String, Integer> inventory, String key, int count) {
    inventory.merge(key, count, Integer::sum);
  }

  private void take(Map<String, Integer> inventory, String key, int count) {
    int left = inventory.getOrDefault(key, 0) - count;
    if (left > 0) inventory.put(key, left);
    else inventory.remove(key);
  }
}
