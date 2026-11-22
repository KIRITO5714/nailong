package com.mclite.content;

// 武器档位：damage 是单次挥砍伤害，range 是判定半径（格），cooldown 是挥砍间隔（秒）。
public record WeaponDef(int tier, String key, String name, int damage, double range, double cooldown) {}
