package com.rootrecord.minecraft.rootmapper;

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

    private static final String PDC_KEY = "mapper_session";

    private MapperItem() {}

    public static ItemStack create(Plugin plugin, MappedArea area, MapperMode mode) {
        ItemStack stack = new ItemStack(Material.DIAMOND, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            NamespacedKey key = new NamespacedKey(plugin, PDC_KEY);
            MapperSession session = new MapperSession(area.id(), mode);
            meta.setDisplayName(switch (mode) {
                case WAYPOINT -> ChatColor.GOLD + area.displayLabel() + " Mapper";
                case REFINE -> ChatColor.AQUA + area.displayLabel() + " Refiner";
                case LAVA -> ChatColor.RED + area.displayLabel() + " Lava Mapper";
            });
            meta.setLore(switch (mode) {
                case WAYPOINT -> List.of(
                        ChatColor.GRAY + "Right-click blocks along the area edge.",
                        ChatColor.GRAY + "Walk the full perimeter in order.",
                        ChatColor.GRAY + "Saves to " + area.polygonFile() + ".",
                        ChatColor.DARK_GRAY + "(/mapper start " + area.id() + ")");
                case REFINE -> List.of(
                        ChatColor.GRAY + "Inside ring: shrink border.",
                        ChatColor.GRAY + "Just outside ring: expand border.",
                        ChatColor.GRAY + "Saves to " + area.polygonFile() + ".",
                        ChatColor.DARK_GRAY + "(/mapper refine " + area.id() + ")");
                case LAVA -> List.of(
                        ChatColor.GRAY + "Click ceiling, then floor.",
                        ChatColor.GRAY + "Repeat for each lava spot.",
                        ChatColor.GRAY + "Saves to " + area.lavaFile() + ".",
                        ChatColor.DARK_GRAY + "(/mapper lava " + area.id() + ")");
            });
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, session.pdcValue());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static MapperSession sessionOf(Plugin plugin, ItemStack stack) {
        if (stack == null || stack.getType() != Material.DIAMOND || !stack.hasItemMeta()) {
            return null;
        }
        NamespacedKey key = new NamespacedKey(plugin, PDC_KEY);
        String raw = stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return MapperSession.fromPdc(raw);
    }

    public static void give(Player player, Plugin plugin, MappedArea area, MapperMode mode) {
        player.getInventory().addItem(create(plugin, area, mode));
    }
}
