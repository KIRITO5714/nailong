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

// 全部数值来自 content.json，代码里不写死任何怪物/武器/配方，改内容不用重新编译逻辑。
@Component
public class ContentRegistry {

  private final JsonNode raw;
  private final ObjectMapper mapper;
  private final Map<Integer, BlockDef> blocksById;
  private final Map<String, ItemDef> items;
  private final Map<Integer, WeaponDef> weaponsByTier;
  private final Map<String, RecipeDef> recipes;
  private final Map<String, MobDef> mobs;

  public ContentRegistry(ObjectMapper mapper) throws IOException {
    this.mapper = mapper;
    try (InputStream in = new ClassPathResource("content.json").getInputStream()) {
      raw = mapper.readTree(in);
    }
    blocksById = new LinkedHashMap<>();
    for (BlockDef block : mapper.treeToValue(raw.get("blocks"), BlockDef[].class)) {
      blocksById.put(block.id(), block);
    }
    items = mapper.convertValue(raw.get("items"), new TypeReference<LinkedHashMap<String, ItemDef>>() {});
    weaponsByTier = new LinkedHashMap<>();
    for (WeaponDef weapon : mapper.treeToValue(raw.get("weapons"), WeaponDef[].class)) {
      weaponsByTier.put(weapon.tier(), weapon);
    }
    recipes = new LinkedHashMap<>();
    for (RecipeDef recipe : mapper.treeToValue(raw.get("recipes"), RecipeDef[].class)) {
      recipes.put(recipe.id(), recipe);
    }
    mobs = new LinkedHashMap<>();
    for (MobDef mob : mapper.treeToValue(raw.get("mobs"), MobDef[].class)) {
      mobs.put(mob.key(), mob);
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

  public WeaponDef weapon(int tier) {
    return weaponsByTier.getOrDefault(tier, weaponsByTier.get(0));
  }

  public RecipeDef recipe(String id) {
    return recipes.get(id);
  }

  public MobDef mob(String key) {
    return mobs.get(key);
  }

  public List<MobDef> mobs() {
    return List.copyOf(mobs.values());
  }

  public JsonNode bounties() {
    return raw.get("bounties");
  }

  public int bountySlots() {
    return bounties().get("slots").asInt(3);
  }

  public Map<String, Integer> counts(JsonNode node) {
    return mapper.convertValue(node, new TypeReference<LinkedHashMap<String, Integer>>() {});
  }
}
