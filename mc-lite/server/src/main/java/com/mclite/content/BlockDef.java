package com.mclite.content;

public record BlockDef(int id, String key, String name, int hardness, int tier, String drop,
                       String color, String accent, Boolean top, Boolean vertical, Boolean ore) {
  public boolean breakable() {
    return hardness > 0;
  }
}
