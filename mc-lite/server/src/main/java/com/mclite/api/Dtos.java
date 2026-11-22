package com.mclite.api;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class Dtos {

  private Dtos() {}

  public record CreateWorld(String name, Long seed) {}

  public record Loot(@NotBlank String mob) {}

  public record Craft(@NotBlank String recipeId) {}

  public record Deliver(@NotBlank String bountyId) {}

  public record Move(double x, double y, Integer hp) {}

  public record Player(double x, double y) {}

  public record Bounty(String id, String title, Map<String, Integer> wants, Map<String, Integer> reward, long expiresAt) {}

  public record BountiesView(List<Bounty> bounties, long now) {}

  public record WorldView(String id, String name, long seed, int width, int height, int[] blocks,
                          int[] surface, Player player, Map<String, Integer> inventory,
                          Map<String, Integer> kills, int hp, boolean cleared,
                          int weaponTier, String weaponName, List<Bounty> bounties, long now) {}

  public record WorldSummary(String id, String name, long seed, int width, int height, Instant updatedAt) {}

  public record Mutation(boolean ok, Map<String, Integer> inventory, Map<String, Integer> kills,
                         int weaponTier, String weaponName, String message, boolean cleared,
                         List<Bounty> bounties) {}

  public record Ack(boolean ok, Player player, Integer hp) {}

  public record ApiError(String code, String message) {}
}
