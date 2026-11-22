package com.mclite.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mclite.api.Dtos;
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
  private Saves saves;

  @BeforeEach
  void freshWorld() {
    id = game.create("测试世界", 42L).id();
    saves = new Saves(game, repo, mapper, id);
  }

  @Test
  void newWorldSpansTheLongMapWithEmptyHands() {
    Dtos.WorldView view = game.load(id);
    assertThat(view.width()).isEqualTo(480);
    assertThat(view.height()).isEqualTo(96);
    assertThat(view.inventory()).isEmpty();
    assertThat(view.kills()).isEmpty();
    assertThat(view.hp()).isEqualTo(WorldViews.MAX_HP);
    assertThat(view.weaponTier()).isZero();
    assertThat(view.weaponName()).isEqualTo("徒手");
    assertThat(view.player().x()).isEqualTo(view.width() / 2 + 0.5);
  }

  @Test
  void killingSlimeDropsGelAndCountsTheKill() {
    Dtos.Mutation result = saves.kill("slime");
    assertThat(result.ok()).isTrue();
    assertThat(result.inventory().get("gel")).isBetween(1, 2);
    assertThat(result.kills()).containsEntry("slime", 1);
    assertThat(result.message()).contains("史莱姆");
    Dtos.WorldView reloaded = game.load(id);
    assertThat(reloaded.inventory()).containsEntry("gel", result.inventory().get("gel"));
    assertThat(reloaded.kills()).containsEntry("slime", 1);
  }

  @Test
  void dropsFollowTheDropTable() {
    for (int i = 0; i < 3; i++) saves.kill("zombie");
    Map<String, Integer> inventory = game.load(id).inventory();
    // 兽皮 chance=1.0 min1 max2 必掉 3~6 个，骨棒 chance=0.4 只要求不越界。
    assertThat(inventory.get("hide")).isBetween(3, 6);
    assertThat(inventory.getOrDefault("bone", 0)).isBetween(0, 3);
    assertThat(game.load(id).kills()).containsEntry("zombie", 3);
  }

  @Test
  void unknownMobAndSpammingSwingsAreRejected() {
    saves.expectCode(() -> game.loot(id, "dragon"), "UNKNOWN_MOB");
    game.loot(id, "slime");
    saves.expectCode(() -> game.loot(id, "slime"), "LOOT_TOO_FAST");
  }

  @Test
  void craftingSwordsUpgradesTheWeapon() {
    saves.expectCode(() -> game.craft(id, "wood_sword"), "NO_MATERIAL");
    saves.expectCode(() -> game.craft(id, "nope"), "UNKNOWN_RECIPE");
    saves.give("bone", 2);
    saves.give("gel", 2);
    Dtos.Mutation wood = game.craft(id, "wood_sword");
    assertThat(wood.weaponTier()).isEqualTo(1);
    assertThat(wood.weaponName()).isEqualTo("木剑");
    assertThat(wood.message()).contains("木剑");
    assertThat(wood.inventory()).containsEntry("wood_sword", 1)
        .doesNotContainKey("bone").doesNotContainKey("gel");
  }

  @Test
  void weaponTierFollowsTheBestSwordInBag() {
    saves.give("wood_sword", 1);
    saves.give("diamond_sword", 1);
    Dtos.WorldView view = game.load(id);
    assertThat(view.weaponTier()).isEqualTo(4);
    assertThat(view.weaponName()).isEqualTo("钻石剑");
  }

  @Test
  void moveIsClampedAndStoresHp() {
    Dtos.Ack ack = game.move(id, -50, 999, 40);
    assertThat(ack.player().x()).isGreaterThan(0);
    assertThat(ack.player().y()).isLessThan(96);
    assertThat(ack.hp()).isEqualTo(40);
    assertThat(game.load(id).hp()).isEqualTo(40);
    assertThat(game.move(id, 10, 10, 999).hp()).isEqualTo(WorldViews.MAX_HP);
    assertThat(game.move(id, 12, 10, null).hp()).isEqualTo(WorldViews.MAX_HP);
    assertThat(game.load(id).player().x()).isEqualTo(12);
  }

  @Test
  void worldSurvivesReloadAndDelete() {
    saves.give("core", 2);
    saves.giveKills("bat", 3);
    Dtos.WorldView reloaded = game.load(id);
    assertThat(reloaded.inventory()).containsEntry("core", 2);
    assertThat(reloaded.kills()).containsEntry("bat", 3);
    assertThat(game.list()).extracting(Dtos.WorldSummary::id).contains(id);
    game.delete(id);
    saves.expectCode(() -> game.load(id), "NOT_FOUND");
  }

  @Test
  void sameSeedCreatesIdenticalWorlds() {
    assertThat(game.create("另一份", 42L).blocks()).isEqualTo(game.load(id).blocks());
  }
}
