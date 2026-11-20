package com.mclite.game;

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

  private final ContentRegistry content;
  private final WorldRepository repo;
  private final WorldViews views;
  private final WorldGenerator generator = new WorldGenerator();
  private final Map<String, Object> locks = new ConcurrentHashMap<>();
  private final int width;
  private final int height;
  private final double reach;

  public GameService(ContentRegistry content, WorldRepository repo, WorldViews views,
                     @Value("${mclite.world.width}") int width,
                     @Value("${mclite.world.height}") int height,
                     @Value("${mclite.world.reach}") double reach) {
    this.content = content;
    this.repo = repo;
    this.views = views;
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
    OrderBoard.Board board = OrderBoard.initial(content, actualSeed, System.currentTimeMillis());
    views.persistBoard(entity, board);
    entity.setPlayerX(spawn[0]);
    entity.setPlayerY(spawn[1]);
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());
    repo.save(entity);
    return views.view(entity, world, new LinkedHashMap<>());
  }

  public List<Dtos.WorldSummary> list() {
    return repo.findAllByOrderByUpdatedAtDesc().stream()
        .map(e -> new Dtos.WorldSummary(e.getId(), e.getName(), e.getSeed(), e.getWidth(), e.getHeight(), e.getUpdatedAt()))
        .toList();
  }

  public Dtos.WorldView load(String id) {
    Session s = open(id);
    return views.view(s.entity(), s.world(), s.inventory());
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
      int tier = Inventories.tier(content, s.inventory());
      if (tier < def.tier()) {
        throw new GameException("TOOL_TOO_WEAK",
            "挖" + def.name() + "需要" + content.tool(def.tier()).name() + "，你现在只有" + content.tool(tier).name());
      }
      s.world().set(x, y, 0);
      String dropName = null;
      if (def.drop() != null) {
        Inventories.add(s.inventory(), def.drop(), 1);
        dropName = content.item(def.drop()).name();
      }
      List<Dtos.BlockChange> changes = new ArrayList<>();
      changes.add(new Dtos.BlockChange(x, y, 0));
      changes.addAll(Collapse.settle(s.world(), content));
      Dtos.Player pushed = pushOut(s, changes);
      save(s);
      String message = dropName == null
          ? def.name() + "碎了一地，什么也没留下"
          : "挖掉" + def.name() + "，背包 +1 " + dropName;
      if (pushed != null) message += "；塌方把你顶到了堆顶";
      return views.mutation(s.inventory(), changes, dropName, message, pushed, null);
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
      if (PlayerBox.overlaps(s.entity().getPlayerX(), s.entity().getPlayerY(), x, y)) {
        throw new GameException("BLOCKED_BY_PLAYER", "不能把方块放在自己身上");
      }
      Inventories.take(s.inventory(), itemKey, 1);
      s.world().set(x, y, item.place());
      List<Dtos.BlockChange> changes = new ArrayList<>();
      changes.add(new Dtos.BlockChange(x, y, item.place()));
      changes.addAll(Collapse.settle(s.world(), content));
      Dtos.Player pushed = pushOut(s, changes);
      save(s);
      String message = "放下了" + item.name();
      if (pushed != null) message += "；塌方把你顶到了堆顶";
      return views.mutation(s.inventory(), changes, null, message, pushed, null);
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
      List<String> missing = Inventories.missing(content, s.inventory(), recipe.in());
      if (!missing.isEmpty()) throw new GameException("NO_MATERIAL", "材料不够，还差 " + String.join("、", missing));
      int before = Inventories.tier(content, s.inventory());
      recipe.in().forEach((key, count) -> Inventories.take(s.inventory(), key, count));
      recipe.out().forEach((key, count) -> Inventories.add(s.inventory(), key, count));
      int after = Inventories.tier(content, s.inventory());
      save(s);
      String message = "合成了" + recipe.name();
      if (after > before) message += "，工具升级成" + content.tool(after).name();
      return views.mutation(s.inventory(), List.of(), null, message, null, null);
    }
  }

  public Dtos.OrdersView orders(String id) {
    synchronized (lock(id)) {
      Session s = open(id);
      long now = System.currentTimeMillis();
      OrderBoard.Board board = OrderBoard.refresh(views.readOrders(s.entity().getOrdersJson()),
          OrderBoard.seqOf(s.entity().getOrderSeq()), s.entity().getSeed(), now, content);
      views.persistBoard(s.entity(), board);
      save(s);
      return new Dtos.OrdersView(board.orders(), now);
    }
  }

  public Dtos.Mutation deliver(String id, String orderId) {
    synchronized (lock(id)) {
      Session s = open(id);
      long now = System.currentTimeMillis();
      List<Dtos.Order> orders = views.readOrders(s.entity().getOrdersJson());
      Dtos.Order order = orders.stream().filter(o -> o.id().equals(orderId)).findFirst()
          .orElseThrow(() -> new GameException("UNKNOWN_ORDER", "没有这张委托单"));
      if (order.expiresAt() <= now) throw new GameException("ORDER_EXPIRED", "这单已经过期，邮差换了新委托");
      List<String> missing = Inventories.missing(content, s.inventory(), order.wants());
      if (!missing.isEmpty()) throw new GameException("NO_MATERIAL", "货不够，还差 " + String.join("、", missing));
      order.wants().forEach((key, count) -> Inventories.take(s.inventory(), key, count));
      order.reward().forEach((key, count) -> Inventories.add(s.inventory(), key, count));
      List<Dtos.Order> left = new ArrayList<>(orders);
      left.remove(order);
      OrderBoard.Board board = OrderBoard.refresh(left, OrderBoard.seqOf(s.entity().getOrderSeq()),
          s.entity().getSeed(), now, content);
      views.persistBoard(s.entity(), board);
      save(s);
      String message = "委托「" + order.title() + "」完成！邮差留下 " + Inventories.describe(content, order.reward());
      return views.mutation(s.inventory(), List.of(), null, message, null, board.orders());
    }
  }

  // 物理在客户端跑，服务端只记录位置：一是存档，二是挖掘/放置的距离判定要有依据。
  public Dtos.Ack move(String id, double x, double y) {
    synchronized (lock(id)) {
      Session s = open(id);
      double px = Math.max(PlayerBox.HALF_W, Math.min(width - PlayerBox.HALF_W, x));
      double py = Math.max(PlayerBox.HALF_H, Math.min(height - PlayerBox.HALF_H, y));
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
    return new Session(entity, world, views.readInventory(entity.getInventoryJson()));
  }

  private void save(Session s) {
    s.entity().setBlocks(s.world().encode());
    s.entity().setInventoryJson(views.writeInventory(s.inventory()));
    s.entity().setUpdatedAt(Instant.now());
    repo.save(s.entity());
  }

  private Dtos.Player pushOut(Session s, List<Dtos.BlockChange> changes) {
    double[] pos = PlayerBox.unbury(s.world(), s.entity().getPlayerX(), s.entity().getPlayerY(), changes);
    if (pos == null) return null;
    s.entity().setPlayerX(pos[0]);
    s.entity().setPlayerY(pos[1]);
    return new Dtos.Player(pos[0], pos[1]);
  }

  private void checkReach(Session s, int x, int y) {
    double dx = x + 0.5 - s.entity().getPlayerX();
    double dy = y + 0.5 - s.entity().getPlayerY();
    if (Math.hypot(dx, dy) > reach) throw new GameException("OUT_OF_RANGE", "太远了，走近一点再动手");
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
}