package com.rootrecord.minecraft.rootterritories.listener;

import com.rootrecord.minecraft.rootterritories.service.WildernessAlertService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public final class WildernessAlertListener implements Listener {

    private final WildernessAlertService alerts;

    public WildernessAlertListener(WildernessAlertService alerts) {
        this.alerts = alerts;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        notifyActor(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        notifyActor(event.getPlayer(), event);
    }

    private void notifyActor(Player player, org.bukkit.event.block.BlockEvent event) {
        if (player == null) {
            return;
        }
        alerts.handleBlockChange(player, event.getBlock().getLocation());
    }
}
