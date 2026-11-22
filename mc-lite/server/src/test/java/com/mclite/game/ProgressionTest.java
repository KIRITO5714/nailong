package com.mclite.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mclite.api.Dtos;
import com.mclite.content.ContentRegistry;
import com.mclite.content.MobDef;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// 难度分区与 Boss 通关：内容表的递增关系、通关标记的写入与旧存档兜底。
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:mclite-test;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ProgressionTest {

  @Autowired GameService game;
  @Autowired WorldRepository repo;
  @Autowired ObjectMapper mapper;
  @Autowired ContentRegistry content;

  private String id;
  private Saves saves;

  @BeforeEach
  void freshWorld() {
    id = game.create("分区世界", 42L).id();
    saves = new Saves(game, repo, mapper, id);
  }

  // 15 种怪按分区一级级变硬，zone 最大的那只是 Boss，且 Boss 既不进随机刷怪权重也不进悬赏池。
  @Test
  void mobsGetHarderByZoneAndEndWithTheBoss() {
    List<MobDef> all = content.mobs();
    List<MobDef> normal = all.stream().filter(m -> !m.boss()).toList();
    assertThat(all).hasSize(15);
    assertThat(normal).hasSize(14);
    for (int i = 1; i < normal.size(); i++) {
      assertThat(normal.get(i).zone()).isGreaterThan(normal.get(i - 1).zone());
      assertThat(normal.get(i).hp()).isGreaterThanOrEqualTo(normal.get(i - 1).hp());
      assertThat(normal.get(i).damage()).isGreaterThanOrEqualTo(normal.get(i - 1).damage());
    }
    MobDef last = normal.get(normal.size() - 1);
    MobDef boss = all.get(all.size() - 1);
    assertThat(boss.boss()).isTrue();
    assertThat(boss.zone()).isGreaterThan(last.zone());
    assertThat(boss.hp()).isGreaterThan(last.hp());
    assertThat(boss.damage()).isGreaterThanOrEqualTo(last.damage());
    for (long seed : new long[] {1L, 42L, 2026L}) {
      Dtos.WorldView world = game.create("Boss 检查", seed);
      assertThat(world.bounties()).allSatisfy(b -> assertThat(b.wants()).doesNotContainKey(boss.key()));
      game.delete(world.id());
    }
  }

  @Test
  void killingTheBossClearsTheWorldAndTheFlagPersists() {
    assertThat(game.load(id).cleared()).isFalse();
    Dtos.Mutation result = saves.kill("overlord");
    assertThat(result.cleared()).isTrue();
    assertThat(result.message()).contains("墟岩魔王").contains("通关");
    assertThat(result.kills()).containsEntry("overlord", 1);
    assertThat(game.load(id).cleared()).isTrue();
    // 通关之后再杀一次不该重复播报「世界通关」。
    assertThat(saves.kill("overlord").message()).doesNotContain("通关");
  }

  // 旧存档没有 cleared 列，读出来是 null，必须按「未通关」兜底。
  @Test
  void legacySavesWithoutTheClearedColumnAreNotCleared() {
    saves.simulateLegacySave();
    Dtos.WorldView view = game.load(id);
    assertThat(view.cleared()).isFalse();
    assertThat(view.hp()).isEqualTo(WorldViews.MAX_HP);
    assertThat(view.bounties()).hasSize(3);
  }

  @Test
  void clearingTheWorldSurvivesOtherMutations() {
    saves.kill("overlord");
    saves.giveKills("slime", 5);
    saves.give("bone", 2);
    saves.give("gel", 2);
    assertThat(game.craft(id, "wood_sword").cleared()).isTrue();
    assertThat(game.move(id, 100, 10, 80).hp()).isEqualTo(80);
    assertThat(game.load(id).cleared()).isTrue();
  }
}
