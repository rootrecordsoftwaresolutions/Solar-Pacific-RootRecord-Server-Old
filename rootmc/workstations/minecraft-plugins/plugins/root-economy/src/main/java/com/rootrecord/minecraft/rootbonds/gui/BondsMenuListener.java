package com.rootrecord.minecraft.rootbonds.gui;

import com.rootrecord.minecraft.rootbonds.RootBondsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class BondsMenuListener implements Listener {

    private final RootBondsPlugin plugin;

    public BondsMenuListener(RootBondsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof GovBondMenuHolder govHolder) {
            handleGovMenuClick(event, govHolder);
            return;
        }
        if (event.getInventory().getHolder() instanceof BondNoteMenuHolder noteHolder) {
            handleNoteMenuClick(event, noteHolder);
            return;
        }
        if (!(event.getInventory().getHolder() instanceof BondsMenuHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.getUniqueId().equals(holder.playerId())) {
            event.setCancelled(true);
            return;
        }
        int raw = event.getRawSlot();

        if (raw == 7) {
            event.setCancelled(true);
            handleRedeem(player);
            return;
        }

        if (raw == 6) {
            event.setCancelled(true);
            return;
        }

        if (raw == 0) {
            event.setCancelled(true);
            player.closeInventory();
            plugin.menuRegistry().openCreateMenu(player);
            return;
        }

        if (raw == 2) {
            event.setCancelled(true);
            plugin.menuRegistry().openGovernmentSettings(player, "town");
            return;
        }

        if (raw == 5) {
            event.setCancelled(true);
            plugin.menuRegistry().openGovernmentSettings(player, "nation");
            return;
        }

        int goldStart = plugin.bonds().config().goldChestStartSlot();
        int goldSlots = plugin.bonds().config().goldChestSlots();
        if (goldSlots <= 0 || raw < goldStart) {
            event.setCancelled(true);
            return;
        }

        if (raw >= goldStart + goldSlots) {
            return;
        }

        ItemStack current = event.getCurrentItem();
        if (current == null || !isGoldItem(current)) {
            event.setCancelled(true);
            return;
        }

        // Legacy coupon chest — compound path no longer pays partial claims.
        event.setCancelled(true);
        player.sendMessage(BondsMenuRegistry.legacyColor(
                "&eBond earnings compound into your notes. Redeem a note in &ffull&e for wallet Notes."));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof BondsMenuHolder
                || event.getInventory().getHolder() instanceof BondNoteMenuHolder
                || event.getInventory().getHolder() instanceof GovBondMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void handleGovMenuClick(InventoryClickEvent event, GovBondMenuHolder holder) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        if (!player.getUniqueId().equals(holder.playerId())) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        int raw = event.getRawSlot();
        if (raw == 0) {
            player.closeInventory();
            plugin.menuRegistry().open(player);
            return;
        }
        if (raw == 2) {
            plugin.menuRegistry().toggleGovernmentAutoBond(player, holder);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof BondNoteMenuHolder) {
            if (event.getPlayer() instanceof Player player) {
                plugin.menuRegistry().close(player);
            }
            return;
        }
        if (!(event.getInventory().getHolder() instanceof BondsMenuHolder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        plugin.menuRegistry().close(player);
        int goldStart = plugin.bonds().config().goldChestStartSlot();
        int goldSlots = plugin.bonds().config().goldChestSlots();
        if (goldSlots <= 0) {
            return;
        }
        Inventory inv = event.getInventory();
        for (int i = goldStart; i < goldStart + goldSlots; i++) {
            inv.setItem(i, null);
        }
    }

    private void handleNoteMenuClick(InventoryClickEvent event, BondNoteMenuHolder holder) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        if (!player.getUniqueId().equals(holder.playerId())) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        int raw = event.getRawSlot();
        if (raw == 0 || raw == 8) {
            player.closeInventory();
            plugin.menuRegistry().open(player);
            return;
        }
        if (raw == 7) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() != org.bukkit.Material.EMERALD) {
                return;
            }
            handleRedeemBond(player, holder.bondId());
        }
    }

    private void handleRedeemBond(Player player, UUID bondId) {
        if (bondId == null) {
            player.sendMessage(BondsMenuRegistry.legacyColor("&eInvalid bonded note."));
            return;
        }
        String fail = plugin.bonds().redeemBondDetail(player, bondId);
        if (fail == null) {
            player.sendMessage(BondsMenuRegistry.legacyColor(
                    "&aNote redeemed in full — compounded balance paid as wallet Notes."));
            player.closeInventory();
        } else {
            player.sendMessage(BondsMenuRegistry.legacyColor("&c" + fail));
        }
    }

    private void handleRedeem(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        UUID bondId = plugin.bonds().certificates().readBondId(hand);
        if (bondId == null) {
            player.sendMessage(BondsMenuRegistry.legacyColor(
                    "&eHold a &fbonded note&e in your main hand (paper named Bonded note)."));
            return;
        }
        handleRedeemBond(player, bondId);
    }

    private static boolean isGoldItem(ItemStack stack) {
        return switch (stack.getType()) {
            case GOLD_BLOCK, GOLD_INGOT, GOLD_NUGGET -> true;
            default -> false;
        };
    }
}
