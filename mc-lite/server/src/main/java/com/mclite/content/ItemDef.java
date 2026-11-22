package com.mclite.content;

// weapon 非空表示这是一把剑，值即武器等级；材料类物品为 null。
public record ItemDef(String name, String color, String accent, Integer weapon) {}
