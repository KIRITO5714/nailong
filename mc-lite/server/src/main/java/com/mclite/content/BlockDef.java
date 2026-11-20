package com.mclite.content;

public record BlockDef(int id, String key, String name, int hardness, int tier, String drop,
                       String color, String accent, Boolean top, Boolean vertical, Boolean ore,
                       Boolean falls, Integer light) {
  public boolean breakable() {
    return hardness > 0;
  }

  public boolean loose() {
    return Boolean.TRUE.equals(falls);
  }

  public int glow() {
    return light == null ? 0 : light;
  }
}
