package com.mclite.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mclite.api.Dtos;
import com.mclite.world.World;
import java.util.LinkedHashMap;
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
