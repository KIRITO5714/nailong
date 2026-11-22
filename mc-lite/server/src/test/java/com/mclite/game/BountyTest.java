package com.mclite.game;

import static org.assertj.core.api.Assertions.assertThat;

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

// 悬赏板：只覆盖数据源，主配置里的 mclite.bounty.* 仍然生效。
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:mclite-test;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class BountyTest {

  @Autowired GameService game;
  @Autowired WorldRepository repo;
  @Autowired ObjectMapper mapper;

  private String id;
  private Saves saves;

  @BeforeEach
  void freshWorld() {
    id = game.create("悬赏世界", 42L).id();
    saves = new Saves(game, repo, mapper, id);
  }

  @Test
  void bountyDeliveryConsumesKillsAndPaysReward() {
    Dtos.Bounty first = game.load(id).bounties().get(0);
    first.wants().forEach(saves::giveKills);
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
    saves.expectCode(() -> game.deliver(id, first.id()), "NOT_ENOUGH_KILLS");
    saves.expectCode(() -> game.deliver(id, "nope"), "UNKNOWN_BOUNTY");
  }

  @Test
  void bountiesEndpointKeepsThreeFreshOffers() {
    Dtos.BountiesView view = game.bounties(id);
    assertThat(view.bounties()).hasSize(3);
    assertThat(view.now()).isPositive();
    assertThat(view.bounties()).allSatisfy(b -> assertThat(b.expiresAt()).isGreaterThan(view.now()));
  }

  @Test
  void expiredBountiesAreReplacedOnLoad() {
    List<Dtos.Bounty> old = saves.storedBounties();
    saves.rewriteBounties(old.stream()
        .map(b -> new Dtos.Bounty(b.id(), b.title(), b.wants(), b.reward(), 1L)).toList());
    Dtos.WorldView view = game.load(id);
    assertThat(view.bounties()).hasSize(3);
    List<String> ids = view.bounties().stream().map(Dtos.Bounty::id).toList();
    assertThat(old.stream().map(Dtos.Bounty::id).noneMatch(ids::contains)).isTrue();
  }

  @Test
  void deliveringAnExpiredBountyIsRejected() {
    List<Dtos.Bounty> old = saves.storedBounties();
    Dtos.Bounty stale = new Dtos.Bounty(old.get(0).id(), old.get(0).title(),
        old.get(0).wants(), old.get(0).reward(), 1L);
    List<Dtos.Bounty> mixed = new ArrayList<>(old);
    mixed.set(0, stale);
    saves.rewriteBounties(mixed);
    stale.wants().forEach(saves::giveKills);
    saves.expectCode(() -> game.deliver(id, stale.id()), "BOUNTY_EXPIRED");
  }

  // 旧存档里是「送信委托」，标题已不在新内容表里，读档时必须整批换掉而不是崩掉。
  @Test
  void legacyBountyTitlesAreCleanedOnLoad() {
    List<Dtos.Bounty> legacy = List.of(
        new Dtos.Bounty("b0", "送货：木板", Map.of("planks", 6), Map.of("core", 1), Long.MAX_VALUE),
        new Dtos.Bounty("b1", "送货：荧光晶", Map.of("glow_crystal", 2), Map.of("core", 1), Long.MAX_VALUE));
    saves.rewriteBounties(legacy);
    Dtos.WorldView view = game.load(id);
    assertThat(view.bounties()).hasSize(3);
    assertThat(view.bounties()).extracting(Dtos.Bounty::title).doesNotContain("送货：木板", "送货：荧光晶");
  }

  @Test
  void bountyBoardNeverShowsDuplicateTitles() {
    for (long seed : new long[] {1L, 42L, 7L, 99L, 2026L}) {
      Dtos.WorldView world = game.create("悬赏测试", seed);
      assertThat(world.bounties()).hasSize(3);
      assertThat(world.bounties().stream().map(Dtos.Bounty::title).toList()).doesNotHaveDuplicates();
      game.delete(world.id());
    }
  }

  // 悬赏优先派玩家杀过的怪：清空板子后重新补单，目标只能落在杀过的那几种里。
  @Test
  void bountiesOnlyTargetMobsThePlayerHasKilled() {
    saves.giveKills("skeleton", 5);
    saves.giveKills("spider", 5);
    saves.giveKills("wolf", 5);
    saves.giveKills("imp", 5);
    saves.rewriteBounties(List.of());
    Dtos.BountiesView board = game.bounties(id);
    assertThat(board.bounties()).hasSize(3);
    assertThat(board.bounties()).allSatisfy(b ->
        assertThat(b.wants().keySet()).isSubsetOf("skeleton", "spider", "wolf", "imp"));
    assertThat(board.bounties()).extracting(Dtos.Bounty::title).allMatch(t -> t.startsWith("猎杀 "));
  }

  // 高档位的悬赏只收少量猎物但给更多晶核/翼膜，把「打怪 → 升剑」接成循环。
  @Test
  void bountyRewardsScaleWithTheTargetZone() {
    saves.giveKills("slime", 9);
    saves.giveKills("drake", 9);
    Map<String, Dtos.Bounty> byTarget = new java.util.LinkedHashMap<>();
    for (int i = 0; i < 8; i++) {
      for (Dtos.Bounty bounty : game.bounties(id).bounties()) {
        byTarget.putIfAbsent(bounty.wants().keySet().iterator().next(), bounty);
      }
      saves.rewriteBounties(List.of());
    }
    Dtos.Bounty easy = byTarget.get("slime");
    Dtos.Bounty hard = byTarget.get("drake");
    assertThat(easy).isNotNull();
    assertThat(hard).isNotNull();
    assertThat(hard.wants().get("drake")).isLessThan(easy.wants().get("slime"));
    assertThat(hard.reward().getOrDefault("core", 0)).isGreaterThan(easy.reward().getOrDefault("core", 0));
  }
}
