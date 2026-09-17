package com.rootrecord.minecraft.rootclaims.gui;

import com.rootrecord.minecraft.rootclaims.ClaimChestService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

public final class ClaimChestListener implements Listener {

    private final ClaimChestService chests;

    public ClaimChestListener(ClaimChestService chests) {
        this.chests = chests;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ClaimChestHolder holder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        chests.onClose(player, holder);
    }
}
