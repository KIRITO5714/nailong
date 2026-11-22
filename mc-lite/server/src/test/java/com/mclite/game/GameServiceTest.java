package com.mclite.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mclite.api.Dtos;
import java.util.ArrayList;
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

  @BeforeEach
  void freshWorld() {
    id = game.create("测试世界", 42L).id();
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
    Dtos.Mutation result = kill("slime");
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
    for (int i = 0; i < 3; i++) kill("zombie");
    Map<String, Integer> inventory = game.load(id).inventory();
    // 兽皮 chance=1.0 必掉 1 个，骨棒 chance=0.35 只要求不越界。
    assertThat(inventory).containsEntry("hide", 3);
    assertThat(inventory.getOrDefault("bone", 0)).isBetween(0, 3);
    assertThat(game.load(id).kills()).containsEntry("zombie", 3);
  }

  @Test
  void unknownMobAndSpammingSwingsAreRejected() {
    assertCode(() -> game.loot(id, "dragon"), "UNKNOWN_MOB");
    game.loot(id, "slime");
    assertCode(() -> game.loot(id, "slime"), "LOOT_TOO_FAST");
  }

  @Test
  void craftingSwordsUpgradesTheWeapon() {
    assertCode(() -> game.craft(id, "wood_sword"), "NO_MATERIAL");
    assertCode(() -> game.craft(id, "nope"), "UNKNOWN_RECIPE");
    give("bone", 2);
    give("gel", 2);
    Dtos.Mutation wood = game.craft(id, "wood_sword");
    assertThat(wood.weaponTier()).isEqualTo(1);
    assertThat(wood.weaponName()).isEqualTo("木剑");
    assertThat(wood.message()).contains("木剑");
    assertThat(wood.inventory()).containsEntry("wood_sword", 1)
        .doesNotContainKey("bone").doesNotContainKey("gel");
  }

  @Test
  void weaponTierFollowsTheBestSwordInBag() {
    give("wood_sword", 1);
    give("diamond_sword", 1);
    Dtos.WorldView view = game.load(id);
    assertThat(view.weaponTier()).isEqualTo(4);
    assertThat(view.weaponName()).isEqualTo("钻石剑");
  }

  @Test
  void bountyDeliveryConsumesKillsAndPaysReward() {
    Dtos.Bounty first = game.load(id).bounties().get(0);
    first.wants().forEach(this::giveKills);
    Dtos.Mutation result = game.deliver(id, first.id());
    assertThat(result.message()).contains(first.title());
    assertThat(result.bounties()).hasSize(3);
    assertThat(result.bounties()).extracting(Dtos.Bounty::id).doesNotContain(first.id());
    first.reward().forEach((key, count) -> assertThat(result.inventory()).containsEntry(key, count));
    first.wants().keySet().forEach(key -> assertThat(result.kills()).doesNotContainKey(key));
  }

  @Test
  void deliverWithoutKillsOrUnknownBountyIsRejected() {
    Dtos.Bounty first = game.load(id).bounties().get(0);
    assertCode(() -> game.deliver(id, first.id()), "NOT_ENOUGH_KILLS");
    assertCode(() -> game.deliver(id, "nope"), "UNKNOWN_BOUNTY");
  }

  @Test
  void bountiesEndpointKeepsThreeFreshOffers() {
    Dtos.BountiesView view = game.bounties(id);
    assertThat(view.bounties()).hasSize(3);
    assertThat(view.now()).isPositive();
    assertThat(view.bounties()).allSatisfy(b -> assertThat(b.expiresAt()).isGreaterThan(view.now()));
  }

  @Test
  void expiredBountiesAreReplacedOnLoad() throws Exception {
    List<Dtos.Bounty> old = storedBounties();
    rewriteBounties(old.stream()
        .map(b -> new Dtos.Bounty(b.id(), b.title(), b.wants(), b.reward(), 1L)).toList());
    Dtos.WorldView view = game.load(id);
    assertThat(view.bounties()).hasSize(3);
    List<String> ids = view.bounties().stream().map(Dtos.Bounty::id).toList();
    assertThat(old.stream().map(Dtos.Bounty::id).noneMatch(ids::contains)).isTrue();
  }

  @Test
  void deliveringAnExpiredBountyIsRejected() throws Exception {
    List<Dtos.Bounty> old = storedBounties();
    Dtos.Bounty stale = new Dtos.Bounty(old.get(0).id(), old.get(0).title(),
        old.get(0).wants(), old.get(0).reward(), 1L);
    List<Dtos.Bounty> mixed = new ArrayList<>(old);
    mixed.set(0, stale);
    rewriteBounties(mixed);
    stale.wants().forEach(this::giveKills);
    assertCode(() -> game.deliver(id, stale.id()), "BOUNTY_EXPIRED");
  }

  // 旧存档里是「送信委托」，标题已不在新内容表里，读档时必须整批换掉而不是崩掉。
  @Test
  void legacyBountyTitlesAreCleanedOnLoad() throws Exception {
    List<Dtos.Bounty> legacy = List.of(
        new Dtos.Bounty("b0", "送货：木板", Map.of("planks", 6), Map.of("core", 1), Long.MAX_VALUE),
        new Dtos.Bounty("b1", "送货：荧光晶", Map.of("glow_crystal", 2), Map.of("core", 1), Long.MAX_VALUE));
    rewriteBounties(legacy);
    Dtos.WorldView view = game.load(id);
    assertThat(view.bounties()).hasSize(3);
    assertThat(view.bounties()).extracting(Dtos.Bounty::title).doesNotContain("送货：木板", "送货：荧光晶");
  }

  @Test
  void bountyBoardNeverShowsDuplicateTitles() {
    for (long seed : new long[] {1L, 42L, 7L, 99L, 2026L}) {
      Dtos.WorldView view = game.create("悬赏测试", seed);
      assertThat(view.bounties()).hasSize(3);
      assertThat(view.bounties().stream().map(Dtos.Bounty::title).toList()).doesNotHaveDuplicates();
      game.delete(view.id());
    }
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
    give("core", 2);
    giveKills("bat", 3);
    Dtos.WorldView reloaded = game.load(id);
    assertThat(reloaded.inventory()).containsEntry("core", 2);
    assertThat(reloaded.kills()).containsEntry("bat", 3);
    assertThat(game.list()).extracting(Dtos.WorldSummary::id).contains(id);
    game.delete(id);
    assertCode(() -> game.load(id), "NOT_FOUND");
  }

  @Test
  void sameSeedCreatesIdenticalWorlds() {
    assertThat(game.create("另一份", 42L).blocks()).isEqualTo(game.load(id).blocks());
  }

  // 服务端限频比客户端最短冷却（0.32s）更松，测试里按限频间隔等一下再挥下一刀。
  private Dtos.Mutation kill(String mob) {
    try {
      Thread.sleep(260);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
    return game.loot(id, mob);
  }

  private void assertCode(Runnable action, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(GameException.class, e -> assertThat(e.getCode()).isEqualTo(code));
  }

  private List<Dtos.Bounty> storedBounties() throws Exception {
    String json = repo.findById(id).orElseThrow().getOrdersJson();
    return json == null || json.isBlank() ? List.of()
        : mapper.readValue(json, new TypeReference<List<Dtos.Bounty>>() {});
  }

  private void rewriteBounties(List<Dtos.Bounty> bounties) throws Exception {
    WorldEntity entity = repo.findById(id).orElseThrow();
    entity.setOrdersJson(mapper.writeValueAsString(bounties));
    repo.save(entity);
  }

  private void give(String item, int count) {
    WorldEntity entity = repo.findById(id).orElseThrow();
    Map<String, Integer> inventory = read(entity.getInventoryJson());
    inventory.merge(item, count, Integer::sum);
    entity.setInventoryJson(write(inventory));
    repo.save(entity);
  }

  private void giveKills(String mob, int count) {
    WorldEntity entity = repo.findById(id).orElseThrow();
    Map<String, Integer> kills = read(entity.getKillsJson());
    kills.merge(mob, count, Integer::sum);
    entity.setKillsJson(write(kills));
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

  private String write(Map<String, Integer> counts) {
    try {
      return mapper.writeValueAsString(counts);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
