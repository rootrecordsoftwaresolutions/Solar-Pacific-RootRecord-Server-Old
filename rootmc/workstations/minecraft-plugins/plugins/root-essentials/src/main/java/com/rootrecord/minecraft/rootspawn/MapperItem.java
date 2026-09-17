package com.rootrecord.minecraft.rootspawn;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

public final class MapperItem {

    private static final String PDC_KEY = "mapper_target";

    private MapperItem() {}

    public static ItemStack create(Plugin plugin, BuildTarget target) {
        ItemStack stack = new ItemStack(Material.DIAMOND, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            NamespacedKey key = new NamespacedKey(plugin, PDC_KEY);
            meta.setDisplayName(switch (target) {
                case SPAWN_MAP -> ChatColor.GREEN + "Spawn Perimeter Mapper";
                case SPAWN_REFINE -> ChatColor.AQUA + "Spawn Boundary Refiner";
            });
            meta.setLore(switch (target) {
                case SPAWN_MAP -> List.of(
                        ChatColor.GRAY + "Right-click blocks along the outer spawn edge.",
                        ChatColor.GRAY + "Order matters — walk the full perimeter.",
                        ChatColor.GRAY + "Saves to spawnarea.txt.",
                        ChatColor.DARK_GRAY + "(/rootspawn map)");
                case SPAWN_REFINE -> List.of(
                        ChatColor.GRAY + "Inside ring: shrink border.",
                        ChatColor.GRAY + "Just outside ring: expand border.",
                        ChatColor.GRAY + "Saves to spawnarea-refined.txt.",
                        ChatColor.DARK_GRAY + "(/rootspawn build)");
            });
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, target.name());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static boolean isMapper(Plugin plugin, ItemStack stack) {
        return targetOf(plugin, stack) != null;
    }

    public static BuildTarget targetOf(Plugin plugin, ItemStack stack) {
        if (stack == null || stack.getType() != Material.DIAMOND || !stack.hasItemMeta()) {
            return null;
        }
        NamespacedKey key = new NamespacedKey(plugin, PDC_KEY);
        String raw = stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return BuildTarget.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static void give(Player player, Plugin plugin, BuildTarget target) {
        player.getInventory().addItem(create(plugin, target));
    }
}
