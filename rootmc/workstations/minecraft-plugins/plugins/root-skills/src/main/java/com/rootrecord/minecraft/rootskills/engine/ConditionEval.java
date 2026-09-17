package com.rootrecord.minecraft.rootskills.engine;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public final class ConditionEval {

    public boolean matches(Player player, List<Map<String, Object>> conditions, Map<String, Object> context) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        for (Map<String, Object> cond : conditions) {
            String type = String.valueOf(cond.getOrDefault("type", cond.getOrDefault("id", ""))).toLowerCase();
            switch (type) {
                case "sneaking" -> {
                    if (player == null || !player.isSneaking()) {
                        return false;
                    }
                }
                case "chance" -> {
                    double chance = toDouble(cond.get("value"), toDouble(cond.get("chance"), 1.0));
                    if (Math.random() > chance) {
                        return false;
                    }
                }
                case "min_mana" -> {
                    // evaluated by caller usually; soft pass
                }
                default -> {
                    // unknown conditions pass
                }
            }
        }
        return true;
    }

    private static double toDouble(Object o, double def) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o != null) {
            try {
                return Double.parseDouble(o.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }
}
