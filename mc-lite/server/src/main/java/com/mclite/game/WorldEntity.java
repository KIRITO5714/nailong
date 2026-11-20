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
  public double getPlayerX() { return playerX; }
  public void setPlayerX(double playerX) { this.playerX = playerX; }
  public double getPlayerY() { return playerY; }
  public void setPlayerY(double playerY) { this.playerY = playerY; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
