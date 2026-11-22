package com.mclite.content;

import java.util.List;

// 怪物数值表：hp/damage/speed 供客户端模拟，drops 是服务端结算掉落用的表。
public record MobDef(String key, String name, int hp, int damage, double speed,
                     boolean flying, double aggro, List<Drop> drops) {

  public record Drop(String item, int min, int max, double chance) {}
}
