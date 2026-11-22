package com.mclite.content;

import java.util.List;

// 怪物数值表：hp/damage/speed 供客户端模拟，drops 是服务端结算掉落用的表。
// zone 是难度分区（客户端刷怪与悬赏档位都按它取），boss 只有巢穴里那一只，杀了就算通关。
// 表现字段（w/h/weight/color/accent/shape）只给客户端用，这里不声明，Jackson 直接忽略。
public record MobDef(String key, String name, int zone, boolean boss, int hp, int damage, double speed,
                     boolean flying, double aggro, List<Drop> drops) {

  public record Drop(String item, int min, int max, double chance) {}
}
