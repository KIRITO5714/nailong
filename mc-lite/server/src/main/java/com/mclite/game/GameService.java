package com.mclite.game;

import com.mclite.api.Dtos;
import com.mclite.content.ContentRegistry;
import com.mclite.content.MobDef;
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

// 规则在服务端结算：掉落表、击杀计数、合成材料、悬赏交付都由这里说了算。
// 怪物怎么走、挥砍有没有命中在客户端模拟（与物理同层），服务端只信「杀了哪只怪」并做频率限制。
@Service
public class GameService {

  private static final double HALF_W = 0.3;
  private static final double HALF_H = 0.9;
  private static final long LOOT_GAP_MS = 250;

  private final ContentRegistry content;
  private final WorldRepository repo;
  private final WorldViews views;
  private final WorldGenerator generator = new WorldGenerator();
  private final Map<String, Object> locks = new ConcurrentHashMap<>();
  private final Map<String, Long> lastLoot = new ConcurrentHashMap<>();
  private final int width;
  private final int height;

  public GameService(ContentRegistry content, WorldRepository repo, WorldViews views,
                     @Value("${mclite.world.width}") int width,
                     @Value("${mclite.world.height}") int height) {
    this.content = content;
    this.repo = repo;
    this.views = views;
    this.width = width;
    this.height = height;
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
    entity.setKillsJson("{}");
    entity.setPlayerHp(WorldViews.MAX_HP);
    entity.setCleared(false);
    views.persistBoard(entity, Bounties.initial(content, actualSeed, System.currentTimeMillis()));
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

  public Dtos.Mutation loot(String id, String mobKey) {
    synchronized (lock(id)) {
      MobDef mob = content.mob(mobKey);
      if (mob == null) throw new GameException("UNKNOWN_MOB", "没有这种怪物：" + mobKey);
      long now = System.currentTimeMillis();
      long last = lastLoot.getOrDefault(id, 0L);
      if (now - last < LOOT_GAP_MS) throw new GameException("LOOT_TOO_FAST", "挥砍还没收招");
      lastLoot.put(id, now);
      Session s = open(id);
      Map<String, Integer> kills = views.readKills(s.entity().getKillsJson());
      kills.merge(mobKey, 1, Integer::sum);
      Map<String, Integer> drops = Loot.roll(content, mob, new Random());
      drops.forEach((key, count) -> Inventories.add(s.inventory(), key, count));
      s.entity().setKillsJson(views.writeKills(kills));
      boolean cleared = views.clearedOf(s.entity());
      String message = "击杀" + mob.name() + (drops.isEmpty() ? "，什么也没掉"
          : "，拾获 " + Inventories.describe(content, drops));
      // Boss 只在地图最东端的巢穴里出现（客户端判定），服务端只认这一句「它死了」并写下通关标记。
      if (mob.boss() && !cleared) {
        s.entity().setCleared(true);
        cleared = true;
        message = "讨伐了" + mob.name() + "，世界通关！";
      }
      save(s);
      return views.mutation(s.inventory(), kills, message, cleared, null);
    }
  }

  public Dtos.Mutation craft(String id, String recipeId) {
    synchronized (lock(id)) {
      Session s = open(id);
      RecipeDef recipe = content.recipe(recipeId);
      if (recipe == null) throw new GameException("UNKNOWN_RECIPE", "没有这个配方：" + recipeId);
      List<String> missing = Inventories.missing(content, s.inventory(), recipe.in());
      if (!missing.isEmpty()) throw new GameException("NO_MATERIAL", "材料不够，还差 " + String.join("、", missing));
      int before = Inventories.weaponTier(content, s.inventory());
      recipe.in().forEach((key, count) -> Inventories.take(s.inventory(), key, count));
      recipe.out().forEach((key, count) -> Inventories.add(s.inventory(), key, count));
      int after = Inventories.weaponTier(content, s.inventory());
      save(s);
      String message = "合成了" + recipe.name();
      if (after > before) message += "，武器升级成" + content.weapon(after).name();
      return views.mutation(s.inventory(), views.readKills(s.entity().getKillsJson()), message,
          views.clearedOf(s.entity()), null);
    }
  }

  public Dtos.BountiesView bounties(String id) {
    synchronized (lock(id)) {
      Session s = open(id);
      long now = System.currentTimeMillis();
      Bounties.Board board = Bounties.refresh(views.readBounties(s.entity().getOrdersJson()),
          Bounties.seqOf(s.entity().getOrderSeq()), s.entity().getSeed(), now,
          views.readKills(s.entity().getKillsJson()), content);
      views.persistBoard(s.entity(), board);
      save(s);
      return new Dtos.BountiesView(board.bounties(), now);
    }
  }

  public Dtos.Mutation deliver(String id, String bountyId) {
    synchronized (lock(id)) {
      Session s = open(id);
      long now = System.currentTimeMillis();
      List<Dtos.Bounty> all = views.readBounties(s.entity().getOrdersJson());
      Dtos.Bounty bounty = all.stream().filter(o -> o.id().equals(bountyId)).findFirst()
          .orElseThrow(() -> new GameException("UNKNOWN_BOUNTY", "没有这张悬赏单"));
      if (bounty.expiresAt() <= now) throw new GameException("BOUNTY_EXPIRED", "这单已经过期，悬赏板换了新目标");
      Map<String, Integer> kills = views.readKills(s.entity().getKillsJson());
      List<String> missing = Inventories.missingKills(content, kills, bounty.wants());
      if (!missing.isEmpty()) throw new GameException("NOT_ENOUGH_KILLS", "猎物不够，还差 " + String.join("、", missing));
      bounty.wants().forEach((key, count) -> kills.merge(key, -count, Integer::sum));
      kills.entrySet().removeIf(entry -> entry.getValue() <= 0);
      bounty.reward().forEach((key, count) -> Inventories.add(s.inventory(), key, count));
      List<Dtos.Bounty> left = new ArrayList<>(all);
      left.remove(bounty);
      Bounties.Board board = Bounties.refresh(left, Bounties.seqOf(s.entity().getOrderSeq()),
          s.entity().getSeed(), now, kills, content);
      views.persistBoard(s.entity(), board);
      s.entity().setKillsJson(views.writeKills(kills));
      save(s);
      String message = "悬赏「" + bounty.title() + "」完成！赏金 " + Inventories.describe(content, bounty.reward());
      return views.mutation(s.inventory(), kills, message, views.clearedOf(s.entity()), board.bounties());
    }
  }

  // 物理与战斗在客户端跑，服务端只记录位置与血量：一是存档，二是重生/展示要有依据。
  public Dtos.Ack move(String id, double x, double y, Integer hp) {
    synchronized (lock(id)) {
      Session s = open(id);
      double px = Math.max(HALF_W, Math.min(width - HALF_W, x));
      double py = Math.max(HALF_H, Math.min(height - HALF_H, y));
      s.entity().setPlayerX(px);
      s.entity().setPlayerY(py);
      if (hp != null) s.entity().setPlayerHp(Math.max(0, Math.min(WorldViews.MAX_HP, hp)));
      save(s);
      return new Dtos.Ack(true, new Dtos.Player(px, py), views.hpOf(s.entity()));
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
    s.entity().setInventoryJson(views.writeInventory(s.inventory()));
    s.entity().setUpdatedAt(Instant.now());
    repo.save(s.entity());
  }
}
