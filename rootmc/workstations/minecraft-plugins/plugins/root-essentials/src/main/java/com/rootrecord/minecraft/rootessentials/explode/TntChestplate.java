package com.rootrecord.minecraft.rootessentials.explode;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;

/**
 * TNT can be worn in the chestplate slot (Paper equippable component).
 * {@code /explode} requires that vest and consumes it on confirm.
 */
public final class TntChestplate {

    private TntChestplate() {
    }

    public static boolean isWorn(Player player) {
        if (player == null) {
            return false;
        }
        ItemStack chest = player.getInventory().getChestplate();
        return isTnt(chest);
    }

    public static boolean consumeWorn(Player player) {
        if (!isWorn(player)) {
            return false;
        }
        player.getInventory().setChestplate(null);
        return true;
    }

    public static boolean isTnt(ItemStack stack) {
        return stack != null && stack.getType() == Material.TNT && stack.getAmount() > 0;
    }

    /** Stamp TNT so clients/servers allow it in the chest armor slot. */
    public static void makeWearable(ItemStack stack) {
        if (!isTnt(stack)) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        if (meta.hasEquippable()) {
            EquippableComponent existing = meta.getEquippable();
            if (existing.getSlot() == EquipmentSlot.CHEST) {
                return;
            }
        }
        EquippableComponent equippable = meta.getEquippable();
        equippable.setSlot(EquipmentSlot.CHEST);
        equippable.setSwappable(true);
        equippable.setDispensable(true);
        meta.setEquippable(equippable);
        stack.setItemMeta(meta);
    }

    public static void stampInventory(Player player) {
        if (player == null) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (!isTnt(stack)) {
                continue;
            }
            makeWearable(stack);
            contents[i] = stack;
            changed = true;
        }
        if (changed) {
            inv.setContents(contents);
        }
        ItemStack chest = inv.getChestplate();
        if (isTnt(chest)) {
            makeWearable(chest);
            inv.setChestplate(chest);
        }
    }
}
