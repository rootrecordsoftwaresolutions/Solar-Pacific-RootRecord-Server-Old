package com.rootrecord.minecraft.rootmcshops;

import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Debounced sign + cloud refresh when shop chest contents change. */
final class ShopStockRefresh {

    private final RootMcShopsPlugin plugin;
    private final ShopStore store;
    private final ConcurrentHashMap<String, BukkitTask> pending = new ConcurrentHashMap<>();

    ShopStockRefresh(RootMcShopsPlugin plugin, ShopStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    void schedule(ShopListing shop) {
        schedule(shop, 2L);
    }

    void scheduleOnClose(ShopListing shop) {
        schedule(shop, 1L);
    }

    void refreshNow(ShopListing shop) {
        schedule(shop, 1L);
    }

    private void schedule(ShopListing shop, long delayTicks) {
        if (shop == null) {
            return;
        }
        String id = shop.id();
        BukkitTask existing = pending.remove(id);
        if (existing != null) {
            existing.cancel();
        }
        Runnable work = () -> {
            pending.remove(id);
            ShopListing fresh = store.getById(id);
            if (fresh == null) {
                return;
            }
            ShopListing current = ShopSigns.refreshListingSign(plugin, store, fresh);
            ShopListingSync.onChanged(plugin, current);
        };
        pending.put(
                id,
                delayTicks <= 0
                        ? plugin.getServer().getScheduler().runTask(plugin, work)
                        : plugin.getServer().getScheduler().runTaskLater(
                                plugin,
                                work,
                                delayTicks));
    }

    void scheduleAll(List<ShopListing> shops) {
        if (shops == null) {
            return;
        }
        for (ShopListing shop : shops) {
            schedule(shop);
        }
    }
}
