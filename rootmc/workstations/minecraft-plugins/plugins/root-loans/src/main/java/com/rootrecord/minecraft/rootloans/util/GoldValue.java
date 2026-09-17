package com.rootrecord.minecraft.rootloans.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class GoldValue {

    private GoldValue() {}

    /** Spendable G equivalent for gold items (matches Root Essentials mint peg). */
    public static double goldItemValue(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return 0;
        }
        Double each = rate(stack.getType());
        return each == null ? 0 : each * stack.getAmount();
    }

    public static Double rate(Material material) {
        if (material == null) {
            return null;
        }
        return switch (material) {
            case GOLD_NUGGET -> 1.0 / 9.0;
            case RAW_GOLD, GOLD_INGOT -> 1.0;
            case GOLD_BLOCK -> 9.0;
            default -> null;
        };
    }
}
