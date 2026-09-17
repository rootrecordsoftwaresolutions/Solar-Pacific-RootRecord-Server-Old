package com.rootrecord.minecraft.common;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.List;

/**
 * Marks gold items paid out by the closed economy (/mint gold, bonds, shops, vault)
 * so the gold-found activity tracker does not count them as world loot.
 */
public final class SystemGoldPayout {

    private static final NamespacedKey KEY = new NamespacedKey("rootrecord", "system_gold");

    private SystemGoldPayout() {}

    public static void mark(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !isGoldMaterial(stack.getType())) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
    }

    public static void mark(ItemStack[] stacks) {
        if (stacks == null) {
            return;
        }
        for (ItemStack stack : stacks) {
            mark(stack);
        }
    }

    public static void mark(Collection<ItemStack> stacks) {
        if (stacks == null) {
            return;
        }
        for (ItemStack stack : stacks) {
            mark(stack);
        }
    }

    public static List<ItemStack> markedStacks(GoldMintHelper.Condensed condensed) {
        List<ItemStack> stacks = GoldMintHelper.toItemStacks(condensed);
        mark(stacks);
        return stacks;
    }

    public static boolean isMarked(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    private static boolean isGoldMaterial(Material material) {
        return switch (material) {
            case GOLD_NUGGET, RAW_GOLD, GOLD_INGOT, GOLD_BLOCK -> true;
            default -> false;
        };
    }
}
