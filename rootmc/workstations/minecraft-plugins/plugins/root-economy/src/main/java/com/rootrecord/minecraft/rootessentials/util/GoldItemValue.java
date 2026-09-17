package com.rootrecord.minecraft.rootessentials.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Physical gold item → G.
 * <p>Mint backing (nugget / ingot / block only): nugget ¹⁄₉, ingot 1, block 9.
 * Raw gold is commodity only — smelt then {@code /mint}. Ore drops use {@link #oreDropRate}.
 */
public final class GoldItemValue {

    private GoldItemValue() {}

    public static double stackValue(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return 0;
        }
        Double each = mintRate(stack.getType());
        return each == null ? 0 : each * stack.getAmount();
    }

    public static double stacksValue(Iterable<ItemStack> stacks) {
        double total = 0;
        if (stacks == null) {
            return 0;
        }
        for (ItemStack stack : stacks) {
            total += stackValue(stack);
        }
        return total;
    }

    public static double stackOreDropValue(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return 0;
        }
        Double each = oreDropRate(stack.getType());
        return each == null ? 0 : each * stack.getAmount();
    }

    public static double stacksOreDropValue(Iterable<ItemStack> stacks) {
        if (stacks == null) {
            return 0;
        }
        double total = 0;
        for (ItemStack stack : stacks) {
            total += stackOreDropValue(stack);
        }
        return total;
    }

    /** Mint backing peg G per item, or {@code null} if not mintable (no raw gold). */
    public static Double mintRate(Material material) {
        if (material == null) {
            return null;
        }
        return switch (material) {
            case GOLD_NUGGET -> 1.0 / 9.0;
            case GOLD_INGOT -> 1.0;
            case GOLD_BLOCK -> 9.0;
            default -> null;
        };
    }

    /** Commodity / ore-drop value (includes raw gold). Not mint backing. */
    public static Double oreDropRate(Material material) {
        Double mint = mintRate(material);
        if (mint != null) {
            return mint;
        }
        if (material == null) {
            return null;
        }
        return switch (material) {
            case RAW_GOLD -> 1.0;
            case RAW_GOLD_BLOCK -> 9.0;
            default -> null;
        };
    }

    public static boolean isGoldItem(Material material) {
        return oreDropRate(material) != null;
    }

    public static boolean isGoldOre(Material material) {
        if (material == null) {
            return false;
        }
        return material == Material.GOLD_ORE
                || material == Material.DEEPSLATE_GOLD_ORE
                || material == Material.NETHER_GOLD_ORE;
    }

    /** Gold items + ore forms that become gold after smelting. */
    public static boolean isGoldRelated(Material material) {
        return isGoldItem(material) || isGoldOre(material);
    }
}
