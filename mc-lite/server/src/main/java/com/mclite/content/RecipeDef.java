package com.mclite.content;

import java.util.Map;

public record RecipeDef(String id, String name, Map<String, Integer> in, Map<String, Integer> out,
                        boolean needsWorkbench) {
}
