package com.rootrecord.minecraft.rootmcshops.virtual;

import com.rootrecord.minecraft.rootmcshops.RootMcShopsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class VirtualStorageListener implements Listener {

    private final RootMcShopsPlugin plugin;

    public VirtualStorageListener(RootMcShopsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof VirtualStorageHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.getUniqueId().equals(holder.viewerId())) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }

        if (holder.mode() == VirtualStorageHolder.Mode.STORAGE) {
            if (slot == VirtualStorageMenus.SLOT_CLOSE) {
                player.closeInventory();
                return;
            }
            if (slot == VirtualStorageMenus.SLOT_LISTINGS) {
                VirtualStorageMenus.openMyListings(plugin, player);
                return;
            }
            if (slot == VirtualStorageMenus.SLOT_DEPOSIT_HAND) {
                player.closeInventory();
                VirtualStorageService.depositHand(plugin, player);
                return;
            }
            if (slot == VirtualStorageMenus.SLOT_DEPOSIT_ALL) {
                player.closeInventory();
                VirtualStorageService.depositAll(plugin, player);
                return;
            }
            if (slot >= holder.binIds().size()) {
                return;
            }
            long binId = holder.binIds().get(slot);
            VirtualBinEntry bin = plugin.virtualItems().getBin(binId);
            if (bin == null) {
                VirtualStorageMenus.openStorage(plugin, player);
                return;
            }
            if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
                int qty = Math.min(bin.qty(), Math.max(1, bin.template() != null ? bin.template().getMaxStackSize() : 64));
                if (event.isShiftClick()) {
                    qty = bin.qty();
                }
                VirtualStorageMenus.beginListPrompt(plugin, player, binId, qty);
                return;
            }
            int qty = 1;
            if (event.isShiftClick()) {
                qty = bin.qty();
            } else if (bin.template() != null) {
                qty = Math.min(bin.qty(), bin.template().getMaxStackSize());
            }
            player.closeInventory();
            VirtualStorageService.withdrawBin(plugin, player, binId, qty);
            return;
        }

        if (holder.mode() == VirtualStorageHolder.Mode.MY_LISTINGS) {
            if (slot == VirtualStorageMenus.SLOT_CLOSE) {
                VirtualStorageMenus.openStorage(plugin, player);
                return;
            }
            if (slot >= holder.listingIds().size()) {
                return;
            }
            String listingId = holder.listingIds().get(slot);
            player.closeInventory();
            VirtualTradeService.cancelListing(plugin, player, listingId);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof VirtualStorageHolder) {
            event.setCancelled(true);
        }
    }
}
