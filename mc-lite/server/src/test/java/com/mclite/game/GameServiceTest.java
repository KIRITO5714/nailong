package com.mclite.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mclite.api.Dtos;
import com.mclite.world.World;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// 只覆盖数据源，主配置里的 mclite.world.* 仍然生效（另写一份同名 yml 会把它整个盖掉）。
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:mclite-test;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class GameServiceTest {

  @Autowired GameService game;
  @Autowired WorldRepository repo;
  @Autowired ObjectMapper mapper;

  private String id;
  private int spawnX;
  private int groundY;

  @BeforeEach
  void freshWorld() {
    Dtos.WorldView view = game.create("测试世界", 42L);
    id = view.id();
    spawnX = view.width() / 2;
    groundY = view.surface()[spawnX];
  }

  @Test
  void handCanMineGrassAndGetsDirt() {
    Dtos.Mutation result = game.mine(id, spawnX, groundY);
    assertThat(result.ok()).isTrue();
    assertThat(result.changes()).containsExactly(new Dtos.BlockChange(spawnX, groundY, 0));
    assertThat(result.inventory()).containsEntry("dirt", 1);
    assertThat(result.toolTier()).isZero();
    assertThat(game.load(id).blocks()[groundY * 160 + spawnX]).isZero();
  }

  @Test
  void handCannotMineStone() {
    paint(spawnX + 1, groundY, 3);
    assertCode(() -> game.mine(id, spawnX + 1, groundY), "TOOL_TOO_WEAK");
    assertThat(game.load(id).blocks()[groundY * 160 + spawnX + 1]).isEqualTo(3);
  }

  @Test
  void leavesDropNothingAndBedrockIsUnbreakable() {
    paint(spawnX + 1, groundY - 1, 5);
    assertThat(game.mine(id, spawnX + 1, groundY - 1).inventory()).isEmpty();
    assertCode(() -> game.mine(id, spawnX, 95), "UNBREAKABLE");
  }

  @Test
  void outOfRangeAndAirAreRejected() {
    assertCode(() -> game.mine(id, spawnX + 30, groundY), "OUT_OF_RANGE");
    assertCode(() -> game.mine(id, spawnX, groundY - 3), "NOT_SOLID");
    assertCode(() -> game.mine(id, 500, 5), "OUT_OF_BOUNDS");
  }

  @Test
  void craftingWithoutWorkbenchIsRejected() {
    give("planks", 3);
    assertCode(() -> game.craft(id, "wood_pickaxe"), "NEEDS_WORKBENCH");
    assertCode(() -> game.craft(id, "planks"), "NO_MATERIAL");
    assertCode(() -> game.craft(id, "nope"), "UNKNOWN_RECIPE");
  }

  @Test
  void planksAndWorkbenchCraftAnywhere() {
    give("log", 2);
    Dtos.Mutation planks = game.craft(id, "planks");
    assertThat(planks.inventory()).containsEntry("planks", 4).containsEntry("log", 1);
    assertThat(game.craft(id, "workbench").inventory()).containsEntry("workbench", 1).doesNotContainKey("planks");
  }

  @Test
  void fullToolProgressionUnlocksDeeperOres() {
    give("log", 4);
    game.craft(id, "planks");
    game.craft(id, "planks");
    game.craft(id, "workbench");
    game.place(id, spawnX + 1, groundY - 1, "workbench");

    Dtos.Mutation wood = game.craft(id, "wood_pickaxe");
    assertThat(wood.toolTier()).isEqualTo(1);
    assertThat(wood.toolName()).isEqualTo("木镐");
    paint(spawnX + 2, groundY, 3);
    assertThat(game.mine(id, spawnX + 2, groundY).inventory()).containsEntry("stone", 1);
    assertCode(() -> mineOre(7), "TOOL_TOO_WEAK");

    give("stone", 3);
    give("planks", 2);
    assertThat(game.craft(id, "stone_pickaxe").toolTier()).isEqualTo(2);
    assertThat(mineOre(7).inventory()).containsEntry("iron", 1);
    assertCode(() -> mineOre(9), "TOOL_TOO_WEAK");

    give("iron", 3);
    give("planks", 2);
    assertThat(game.craft(id, "iron_pickaxe").toolTier()).isEqualTo(3);
    assertThat(mineOre(8).inventory()).containsEntry("gold", 1);
    assertThat(mineOre(9).inventory()).containsEntry("diamond", 1);

    give("diamond", 3);
    give("planks", 2);
    assertThat(game.craft(id, "diamond_pickaxe").toolTier()).isEqualTo(4);
  }

  @Test
  void placingRulesAreEnforced() {
    assertCode(() -> game.place(id, spawnX + 1, groundY - 1, "dirt"), "NO_ITEM");
    give("iron", 1);
    assertCode(() -> game.place(id, spawnX + 1, groundY - 1, "iron"), "NOT_PLACEABLE");
    give("dirt", 2);
    assertCode(() -> game.place(id, spawnX, groundY, "dirt"), "NOT_EMPTY");
    assertCode(() -> game.place(id, spawnX, groundY - 1, "dirt"), "BLOCKED_BY_PLAYER");
    Dtos.Mutation placed = game.place(id, spawnX + 1, groundY - 1, "dirt");
    assertThat(placed.inventory()).containsEntry("dirt", 1);
    assertThat(placed.changes()).containsExactly(new Dtos.BlockChange(spawnX + 1, groundY - 1, 2));
  }

  @Test
  void moveIsClampedAndPersisted() {
    Dtos.Ack ack = game.move(id, -50, 999);
    assertThat(ack.player().x()).isGreaterThan(0);
    assertThat(ack.player().y()).isLessThan(96);
    Dtos.Player stored = game.load(id).player();
    assertThat(stored.x()).isEqualTo(ack.player().x());
    assertThat(stored.y()).isEqualTo(ack.player().y());
  }

  @Test
  void worldSurvivesReloadAndDelete() {
    game.mine(id, spawnX, groundY);
    give("log", 3);
    Dtos.WorldView reloaded = game.load(id);
    assertThat(reloaded.inventory()).containsEntry("dirt", 1).containsEntry("log", 3);
    assertThat(reloaded.blocks()[groundY * 160 + spawnX]).isZero();
    assertThat(game.list()).extracting(Dtos.WorldSummary::id).contains(id);
    game.delete(id);
    assertCode(() -> game.load(id), "NOT_FOUND");
  }

  @Test
  void sameSeedCreatesIdenticalWorlds() {
    assertThat(game.create("另一份", 42L).blocks()).isEqualTo(game.load(id).blocks());
  }

  @Test
  void sandColumnFallsIntoMinedGap() {
    int x = spawnX + 4;
    game.move(id, x + 0.5, groundY + 1.5);
    paint(x, groundY + 1, 13);
    paint(x, groundY + 2, 13);
    paint(x, groundY + 3, 0);
    paint(x, groundY + 4, 3);
    Dtos.Mutation result = game.mine(id, x, groundY + 2);
    assertThat(result.changes()).contains(new Dtos.BlockChange(x, groundY + 1, 0), new Dtos.BlockChange(x, groundY + 3, 13));
    assertThat(world().get(x, groundY + 3)).isEqualTo(13);
    assertThat(world().get(x, groundY + 1)).isZero();
    assertThat(world().get(x, groundY + 4)).isEqualTo(3);
  }

  @Test
  void pillarHoldsLooseColumnUntilItIsMined() {
    int x = spawnX + 4;
    game.move(id, x + 0.5, groundY + 1.5);
    paint(x, groundY + 1, 13);
    paint(x, groundY + 2, 0);
    paint(x, groundY + 3, 3);
    paint(x, groundY + 4, 3);
    paint(x - 1, groundY, 1);
    game.mine(id, x - 1, groundY);
    // 沙先落到柱顶停住：柱子还在，就不会继续往下塌。
    assertThat(world().get(x, groundY + 2)).isEqualTo(13);
    assertThat(world().get(x, groundY + 1)).isZero();
    give("wood_pickaxe", 1);
    game.mine(id, x, groundY + 3);
    assertThat(world().get(x, groundY + 3)).isEqualTo(13);
    assertThat(world().get(x, groundY + 2)).isZero();
  }

  @Test
  void cohesiveDirtDoesNotFall() {
    int x = spawnX + 4;
    game.move(id, x + 0.5, groundY + 1.5);
    paint(x, groundY + 1, 2);
    paint(x, groundY + 2, 0);
    paint(x - 1, groundY, 1);
    game.mine(id, x - 1, groundY);
    assertThat(world().get(x, groundY + 1)).isEqualTo(2);
  }

  @Test
  void buriedPlayerIsPushedToTopOfTheFallenStack() {
    paint(spawnX, groundY - 3, 13);
    paint(spawnX, groundY - 2, 13);
    paint(spawnX + 1, groundY, 1);
    Dtos.Mutation result = game.mine(id, spawnX + 1, groundY);
    assertThat(result.player()).isNotNull();
    assertThat(result.player().y()).isLessThan(groundY - 2);
    assertThat(result.message()).contains("塌方");
    assertThat(game.load(id).player().y()).isEqualTo(result.player().y());
  }

  @Test
  void placedSandFallsUntilItRests() {
    int x = spawnX + 2;
    game.move(id, x + 0.5, groundY - 1.5);
    for (int y = groundY - 4; y < groundY; y++) paint(x, y, 0);
    paint(x, groundY, 1);
    give("sand", 1);
    game.place(id, x, groundY - 4, "sand");
    assertThat(world().get(x, groundY - 1)).isEqualTo(13);
    assertThat(world().get(x, groundY - 4)).isZero();
  }

  @Test
  void ordersStartAtThreeAndDeliverPaysReward() {
    Dtos.WorldView view = game.load(id);
    assertThat(view.orders()).hasSize(3);
    Dtos.Order first = view.orders().get(0);
    first.wants().forEach((key, count) -> give(key, count));
    Dtos.Mutation result = game.deliver(id, first.id());
    assertThat(result.message()).contains("委托");
    assertThat(result.orders()).hasSize(3);
    assertThat(result.orders()).extracting(Dtos.Order::id).doesNotContain(first.id());
    first.reward().forEach((key, count) -> assertThat(result.inventory()).containsEntry(key, count));
    first.wants().keySet().forEach(key -> assertThat(result.inventory()).doesNotContainKey(key));
  }

  @Test
  void deliverWithoutGoodsOrUnknownOrderIsRejected() {
    Dtos.Order first = game.load(id).orders().get(0);
    assertCode(() -> game.deliver(id, first.id()), "NO_MATERIAL");
    assertCode(() -> game.deliver(id, "nope"), "UNKNOWN_ORDER");
  }

  @Test
  void expiredOrdersAreReplacedOnLoad() throws Exception {
    WorldEntity entity = repo.findById(id).orElseThrow();
    List<Dtos.Order> old = mapper.readValue(entity.getOrdersJson(), new TypeReference<List<Dtos.Order>>() {});
    List<Dtos.Order> expired = old.stream()
        .map(o -> new Dtos.Order(o.id(), o.title(), o.wants(), o.reward(), 1L)).toList();
    entity.setOrdersJson(mapper.writeValueAsString(expired));
    repo.save(entity);
    Dtos.WorldView view = game.load(id);
    assertThat(view.orders()).hasSize(3);
    List<String> ids = view.orders().stream().map(Dtos.Order::id).toList();
    assertThat(old.stream().map(Dtos.Order::id).noneMatch(ids::contains)).isTrue();
  }

  @Test
  void deliveringAnExpiredOrderIsRejected() throws Exception {
    WorldEntity entity = repo.findById(id).orElseThrow();
    List<Dtos.Order> old = mapper.readValue(entity.getOrdersJson(), new TypeReference<List<Dtos.Order>>() {});
    Dtos.Order stale = new Dtos.Order(old.get(0).id(), old.get(0).title(), old.get(0).wants(), old.get(0).reward(), 1L);
    List<Dtos.Order> mixed = new java.util.ArrayList<>(old);
    mixed.set(0, stale);
    entity.setOrdersJson(mapper.writeValueAsString(mixed));
    repo.save(entity);
    assertCode(() -> game.deliver(id, stale.id()), "ORDER_EXPIRED");
  }

  @Test
  void orderBoardNeverShowsDuplicateTitles() {
    for (long seed : new long[] {1L, 42L, 7L, 99L, 2026L}) {
      Dtos.WorldView view = game.create("单号测试", seed);
      assertThat(view.orders()).hasSize(3);
      assertThat(view.orders().stream().map(Dtos.Order::title).toList()).doesNotHaveDuplicates();
      game.delete(view.id());
    }
  }

  private World world() {
    WorldEntity entity = repo.findById(id).orElseThrow();
    return new World(entity.getWidth(), entity.getHeight(),
        World.decode(entity.getBlocks(), entity.getWidth(), entity.getHeight()),
        World.decodeSurface(entity.getSurfaceLine(), entity.getWidth()));
  }

  private Dtos.Mutation mineOre(int blockId) {
    paint(spawnX + 2, groundY, blockId);
    return game.mine(id, spawnX + 2, groundY);
  }

  private void assertCode(Runnable action, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(GameException.class, e -> assertThat(e.getCode()).isEqualTo(code));
  }

  private void paint(int x, int y, int blockId) {
    WorldEntity entity = repo.findById(id).orElseThrow();
    World world = new World(entity.getWidth(), entity.getHeight(),
        World.decode(entity.getBlocks(), entity.getWidth(), entity.getHeight()),
        World.decodeSurface(entity.getSurfaceLine(), entity.getWidth()));
    world.set(x, y, blockId);
    entity.setBlocks(world.encode());
    repo.save(entity);
  }

  private void give(String item, int count) {
    WorldEntity entity = repo.findById(id).orElseThrow();
    Map<String, Integer> inventory = read(entity.getInventoryJson());
    inventory.merge(item, count, Integer::sum);
    entity.setInventoryJson(write(inventory));
    repo.save(entity);
  }

  private Map<String, Integer> read(String json) {
    try {
      return json == null || json.isBlank() ? new LinkedHashMap<>()
          : mapper.readValue(json, new TypeReference<LinkedHashMap<String, Integer>>() {});
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String write(Map<String, Integer> inventory) {
    try {
      return mapper.writeValueAsString(inventory);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
