package com.mclite.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "worlds")
public class WorldEntity {

  @Id
  private String id;
  private String name;
  private long seed;
  private int width;
  private int height;

  @Lob
  @Column(nullable = false)
  private String blocks;

  @Column(nullable = false, length = 512)
  private String surfaceLine;

  @Lob
  private String inventoryJson;

  // 列名沿用旧存档的 orders_json，现在存的是悬赏板。
  @Lob
  private String ordersJson;

  @Lob
  private String killsJson;

  // 可空：旧存档没有这一列，Hibernate 加列时不能带 NOT NULL，读的时候按 0 兜底。
  private Integer orderSeq;

  // 可空：旧存档没有血量列，null 按满血兜底。
  private Integer playerHp;

  // 可空：旧存档没有通关标记，null 按未通关兜底（同样不能加 NOT NULL）。
  private Boolean cleared;

  private double playerX;
  private double playerY;
  private Instant createdAt;
  private Instant updatedAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public long getSeed() { return seed; }
  public void setSeed(long seed) { this.seed = seed; }
  public int getWidth() { return width; }
  public void setWidth(int width) { this.width = width; }
  public int getHeight() { return height; }
  public void setHeight(int height) { this.height = height; }
  public String getBlocks() { return blocks; }
  public void setBlocks(String blocks) { this.blocks = blocks; }
  public String getSurfaceLine() { return surfaceLine; }
  public void setSurfaceLine(String surfaceLine) { this.surfaceLine = surfaceLine; }
  public String getInventoryJson() { return inventoryJson; }
  public void setInventoryJson(String inventoryJson) { this.inventoryJson = inventoryJson; }
  public String getOrdersJson() { return ordersJson; }
  public void setOrdersJson(String ordersJson) { this.ordersJson = ordersJson; }
  public String getKillsJson() { return killsJson; }
  public void setKillsJson(String killsJson) { this.killsJson = killsJson; }
  public Integer getOrderSeq() { return orderSeq; }
  public void setOrderSeq(Integer orderSeq) { this.orderSeq = orderSeq; }
  public Integer getPlayerHp() { return playerHp; }
  public void setPlayerHp(Integer playerHp) { this.playerHp = playerHp; }
  public Boolean getCleared() { return cleared; }
  public void setCleared(Boolean cleared) { this.cleared = cleared; }
  public double getPlayerX() { return playerX; }
  public void setPlayerX(double playerX) { this.playerX = playerX; }
  public double getPlayerY() { return playerY; }
  public void setPlayerY(double playerY) { this.playerY = playerY; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
