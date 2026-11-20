package com.mclite.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class Dtos {

  private Dtos() {}

  public record CreateWorld(String name, Long seed) {}

  public record Mine(@Min(0) int x, @Min(0) int y) {}

  public record Place(@Min(0) int x, @Min(0) int y, @NotBlank String item) {}

  public record Craft(@NotBlank String recipeId) {}

  public record Deliver(@NotBlank String orderId) {}

  public record Move(double x, double y) {}

  public record Player(double x, double y) {}

  public record BlockChange(int x, int y, int id) {}

  public record Order(String id, String title, Map<String, Integer> wants, Map<String, Integer> reward, long expiresAt) {}

  public record OrdersView(List<Order> orders, long now) {}

  public record WorldView(String id, String name, long seed, int width, int height, int[] blocks,
                          int[] surface, Player player, Map<String, Integer> inventory,
                          int toolTier, String toolName, double reach, List<Order> orders, long now) {}

  public record WorldSummary(String id, String name, long seed, int width, int height, Instant updatedAt) {}

  public record Mutation(boolean ok, List<BlockChange> changes, Map<String, Integer> inventory,
                         int toolTier, String toolName, String drop, String message,
                         Player player, List<Order> orders) {}

  public record Ack(boolean ok, Player player) {}

  public record ApiError(String code, String message) {}
}
