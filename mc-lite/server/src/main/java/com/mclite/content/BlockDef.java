package com.mclite.content;

// 方块只是静态舞台与光源：世界不可挖掘，所以没有 hardness/tier/drop。
public record BlockDef(int id, String key, String name, String color, String accent,
                       Boolean top, Boolean vertical, Boolean ore, Integer light) {

  public int glow() {
    return light == null ? 0 : light;
  }
}
