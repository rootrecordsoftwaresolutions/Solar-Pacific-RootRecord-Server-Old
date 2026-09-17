package com.rootrecord.minecraft.rootmcshops;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

/** Keeps shop sign stock lines in sync when chest contents change (not only on close). */
public final class ShopStockSignListener implements Listener {

    private final RootMcShopsPlugin plugin;
    private final ShopStore store;
    private final ShopStockRefresh refresh;

    public ShopStockSignListener(RootMcShopsPlugin plugin, ShopStore store, ShopStockRefresh refresh) {
        this.plugin = plugin;
        this.store = store;
        this.refresh = refresh;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player && !player.isOnline()) {
            return;
        }
        Inventory inventory = event.getInventory();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                for (ShopListing shop : ShopProtection.shopsForInventory(store, inventory)) {
                    refresh.scheduleOnClose(shop);
                }
            } catch (IllegalStateException ignored) {
                // Chunk unloading during disconnect — skip sign refresh.
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        refreshInventories(event.getView(), event.getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        refreshInventories(event.getView(), event.getInventory(), event.getClickedInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        refreshInventories(event.getView());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        refreshFor(event.getSource());
        refreshFor(event.getDestination());
    }

    private void refreshInventories(InventoryView view, Inventory... extras) {
        if (view != null) {
            refreshFor(view.getTopInventory());
            refreshFor(view.getBottomInventory());
        }
        for (Inventory inventory : extras) {
            refreshFor(inventory);
        }
    }

    private void refreshFor(Inventory inventory) {
        for (ShopListing shop : ShopProtection.shopsForInventory(store, inventory)) {
            refresh.schedule(shop);
        }
    }
}
