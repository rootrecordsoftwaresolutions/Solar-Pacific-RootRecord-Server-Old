package com.rootrecord.minecraft.rootmcshops;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Push a single shop listing to RootMC / cloud when stock or metadata changes. */
final class ShopListingSync {

    private ShopListingSync() {}

    static void onChanged(RootMcShopsPlugin plugin, ShopListing shop) {
        onChanged(plugin, shop, null);
    }

    static void onChanged(RootMcShopsPlugin plugin, ShopListing shop, String previousItemKey) {
        if (shop == null) {
            return;
        }
        notify(plugin, shop.id(), false, shop.itemKey(), previousItemKey);
    }

    static void onRemoved(RootMcShopsPlugin plugin, ShopListing shop) {
        if (shop == null) {
            return;
        }
        notify(plugin, shop.id(), true, shop.itemKey(), null);
    }

    private static void notify(
            RootMcShopsPlugin plugin,
            String shopId,
            boolean deleted,
            String itemKey,
            String previousItemKey) {
        Plugin rootMc = Bukkit.getPluginManager().getPlugin("RootMC");
        if (rootMc == null || !rootMc.isEnabled()) {
            return;
        }
        try {
            rootMc.getClass()
                    .getMethod("requestShopListingSync", String.class, boolean.class, String.class, String.class)
                    .invoke(rootMc, shopId, deleted, itemKey, previousItemKey);
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().fine("RootMC requestShopListingSync unavailable: " + ex.getMessage());
        }
    }
}
