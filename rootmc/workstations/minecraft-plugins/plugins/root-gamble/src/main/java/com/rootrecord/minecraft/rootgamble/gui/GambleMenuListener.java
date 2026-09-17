package com.rootrecord.minecraft.rootgamble.gui;

import com.rootrecord.minecraft.rootgamble.RootGamblePlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public final class GambleMenuListener implements Listener {

    private final RootGamblePlugin plugin;

    public GambleMenuListener(RootGamblePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GambleMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (holder.kind() != GambleMenuHolder.Kind.HUB) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == 26) {
            player.closeInventory();
            return;
        }
        String game = switch (slot) {
            case 10 -> "roulette";
            case 12 -> "hilo";
            case 14 -> "lotto";
            case 16 -> "coin";
            case 22 -> "dice";
            default -> null;
        };
        if (game == null) {
            return;
        }
        player.closeInventory();
        plugin.help().show(player, game);
    }
}
