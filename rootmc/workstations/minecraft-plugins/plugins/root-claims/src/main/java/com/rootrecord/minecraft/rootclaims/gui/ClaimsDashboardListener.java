package com.rootrecord.minecraft.rootclaims.gui;

import com.rootrecord.minecraft.rootclaims.ClaimKey;
import com.rootrecord.minecraft.rootclaims.ClaimRecord;
import com.rootrecord.minecraft.rootclaims.command.ClaimCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class ClaimsDashboardListener implements Listener {

    private final ClaimsDashboard dashboard;
    private final ClaimsSettingsGui settingsGui;
    private final ClaimCommand command;

    public ClaimsDashboardListener(
            ClaimsDashboard dashboard, ClaimsSettingsGui settingsGui, ClaimCommand command) {
        this.dashboard = dashboard;
        this.settingsGui = settingsGui;
        this.command = command;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof ClaimsSettingsHolder settingsHolder) {
            handleSettingsClick(event, settingsHolder);
            return;
        }
        if (!(event.getInventory().getHolder() instanceof ClaimsDashboardHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.getUniqueId().equals(holder.playerId())) {
            return;
        }
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getRawSlot();
        ClaimKey key = holder.claimAtSlot(slot);
        if (key != null) {
            if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
                ClaimRecord claim = dashboard.claimForKey(player, key);
                player.closeInventory();
                settingsGui.open(player, claim);
            } else {
                dashboard.teleportToClaim(player, key);
            }
            return;
        }
        switch (slot) {
            case ClaimsDashboard.SLOT_NEW_CLAIM -> {
                player.closeInventory();
                command.beginClaimFromGui(player);
            }
            case ClaimsDashboard.SLOT_LINES -> {
                player.closeInventory();
                command.beginLinesFromGui(player);
            }
            case ClaimsDashboard.SLOT_SPAWN -> {
                player.closeInventory();
                command.beginSpawnFromGui(player);
            }
            case ClaimsDashboard.SLOT_CHEST -> {
                player.closeInventory();
                command.beginChestFromGui(player);
            }
            case ClaimsDashboard.SLOT_SETTINGS -> {
                player.closeInventory();
                ClaimRecord claim = dashboard.settingsTarget(player);
                settingsGui.open(player, claim);
            }
            default -> {
            }
        }
    }

    private void handleSettingsClick(InventoryClickEvent event, ClaimsSettingsHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.getUniqueId().equals(holder.playerId())) {
            return;
        }
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        switch (event.getRawSlot()) {
            case ClaimsSettingsGui.SLOT_PUBLIC_SPAWN -> settingsGui.togglePublicSpawn(player, holder.claimKey());
            case ClaimsSettingsGui.SLOT_MOBS -> settingsGui.toggleMobs(player, holder.claimKey());
            case ClaimsSettingsGui.SLOT_BACK -> {
                player.closeInventory();
                dashboard.open(player);
            }
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ClaimsDashboardHolder
                || event.getInventory().getHolder() instanceof ClaimsSettingsHolder) {
            event.setCancelled(true);
        }
    }
}
