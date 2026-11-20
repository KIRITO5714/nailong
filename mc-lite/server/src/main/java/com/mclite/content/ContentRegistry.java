package com.mclite.content;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

// 全部数值来自 content.json，代码里不写死任何方块/配方，改内容不用重新编译逻辑。
@Component
public class ContentRegistry {

  private final JsonNode raw;
  private final Map<Integer, BlockDef> blocksById;
  private final Map<String, ItemDef> items;
  private final Map<Integer, ToolDef> toolsByTier;
  private final Map<String, RecipeDef> recipes;

  public ContentRegistry(ObjectMapper mapper) throws IOException {
    try (InputStream in = new ClassPathResource("content.json").getInputStream()) {
      raw = mapper.readTree(in);
    }
    blocksById = new LinkedHashMap<>();
    for (BlockDef block : mapper.treeToValue(raw.get("blocks"), BlockDef[].class)) {
      blocksById.put(block.id(), block);
    }
    items = mapper.convertValue(raw.get("items"), new TypeReference<LinkedHashMap<String, ItemDef>>() {});
    toolsByTier = new LinkedHashMap<>();
    for (ToolDef tool : mapper.treeToValue(raw.get("tools"), ToolDef[].class)) {
      toolsByTier.put(tool.tier(), tool);
    }
    recipes = new LinkedHashMap<>();
    for (RecipeDef recipe : mapper.treeToValue(raw.get("recipes"), RecipeDef[].class)) {
      recipes.put(recipe.id(), recipe);
    }
  }

  public JsonNode raw() {
    return raw;
  }

  public BlockDef block(int id) {
    BlockDef def = blocksById.get(id);
    if (def == null) throw new IllegalArgumentException("未知方块 id: " + id);
    return def;
  }

  public ItemDef item(String key) {
    return items.get(key);
  }

  public ToolDef tool(int tier) {
    return toolsByTier.getOrDefault(tier, toolsByTier.get(0));
  }

  public RecipeDef recipe(String id) {
    return recipes.get(id);
  }

  public int workbenchBlockId() {
    return blocksById.values().stream().filter(b -> b.key().equals("workbench")).findFirst().orElseThrow().id();
  }
}
