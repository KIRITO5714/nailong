package com.mclite.world;

// 世界就是一维数组，row-major：index = y * width + x。y 向下增大，和 Canvas 一致。
public class World {

  public final int width;
  public final int height;
  public final int[] blocks;
  // 地形线在生成时定下并存档：树冠不算地表，挖掉草方块也不改它，
  // 否则树冠下的空气会被当成地下洞穴涂黑。
  private final int[] surface;

  public World(int width, int height, int[] blocks, int[] surface) {
    if (blocks.length != width * height) throw new IllegalArgumentException("方块数组长度与世界尺寸不符");
    if (surface.length != width) throw new IllegalArgumentException("地形线长度与世界宽度不符");
    this.width = width;
    this.height = height;
    this.blocks = blocks;
    this.surface = surface;
  }

  public boolean inBounds(int x, int y) {
    return x >= 0 && y >= 0 && x < width && y < height;
  }

  public int get(int x, int y) {
    return inBounds(x, y) ? blocks[y * width + x] : 10;
  }

  public void set(int x, int y, int id) {
    if (inBounds(x, y)) blocks[y * width + x] = id;
  }

  public boolean solid(int x, int y) {
    return get(x, y) != 0;
  }

  // 每列的地表 y，前端拿它画天空/地下的分界。
  public int[] surface() {
    return surface;
  }

  // 一个方块一个字符（'A' + id），15360 格的世界存成 15KB 文本，比 JSON 数组小一个量级。
  public String encode() {
    char[] chars = new char[blocks.length];
    for (int i = 0; i < blocks.length; i++) chars[i] = (char) ('A' + blocks[i]);
    return new String(chars);
  }

  public static int[] decode(String encoded, int width, int height) {
    int[] blocks = new int[width * height];
    for (int i = 0; i < blocks.length; i++) blocks[i] = encoded.charAt(i) - 'A';
    return blocks;
  }

  public String encodeSurface() {
    char[] chars = new char[width];
    for (int x = 0; x < width; x++) chars[x] = (char) ('A' + surface[x]);
    return new String(chars);
  }

  public static int[] decodeSurface(String encoded, int width) {
    int[] surface = new int[width];
    for (int x = 0; x < width; x++) surface[x] = encoded.charAt(x) - 'A';
    return surface;
  }
}
