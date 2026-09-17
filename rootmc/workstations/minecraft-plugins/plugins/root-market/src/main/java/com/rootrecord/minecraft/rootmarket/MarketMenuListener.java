package com.rootrecord.minecraft.rootmarket;

import com.rootrecord.minecraft.rootmcshops.RootMcShopsPlugin;
import com.rootrecord.minecraft.rootmcshops.ShopListing;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.List;
import java.util.Locale;

public final class MarketMenuListener implements Listener {

    private final RootMarketPlugin plugin;

    public MarketMenuListener(RootMarketPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MarketMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!player.getUniqueId().equals(holder.viewerId())) {
            return;
        }
        RootMcShopsPlugin shops = RootMcShopsPlugin.get();
        if (shops == null) {
            player.closeInventory();
            player.sendMessage(plugin.msg("need-chestshops"));
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }

        if (holder.mode() == MarketMenuHolder.Mode.CATEGORIES) {
            if (slot >= holder.keysOrIds().size()) {
                return;
            }
            MarketCategory cat = MarketCategory.fromId(holder.keysOrIds().get(slot));
            MarketMenus.openCategory(plugin, shops, player, cat);
            return;
        }

        if (holder.mode() == MarketMenuHolder.Mode.ITEM && slot == MarketMenus.SLOT_BACK) {
            MarketMenus.openCategory(plugin, shops, player, holder.category());
            return;
        }

        if (holder.mode() == MarketMenuHolder.Mode.HUB && slot == MarketMenus.SLOT_BACK) {
            if (plugin.yaml().config().getBoolean("browse.categories", true)) {
                MarketMenus.openCategories(plugin, shops, player);
            }
            return;
        }

        if (slot == MarketMenus.SLOT_PREV && holder.page() > 0) {
            reopen(player, shops, holder, holder.page() - 1);
            return;
        }
        if (slot == MarketMenus.SLOT_NEXT) {
            reopen(player, shops, holder, holder.page() + 1);
            return;
        }
        if (slot >= MarketMenus.PAGE_SIZE) {
            return;
        }

        List<String> keysOrIds = holder.keysOrIds();
        if (slot >= keysOrIds.size()) {
            return;
        }
        String key = keysOrIds.get(slot);

        if (holder.mode() == MarketMenuHolder.Mode.HUB) {
            MarketMenus.openItem(plugin, shops, player, key, holder.category());
            return;
        }

        if (key.startsWith("v:")) {
            String listingId = key.substring(2);
            int qty = 1;
            var listing = shops.virtualListings() != null ? shops.virtualListings().get(listingId) : null;
            if (listing != null && listing.template() != null) {
                qty = Math.min(listing.qty(), listing.template().getMaxStackSize());
            }
            player.closeInventory();
            com.rootrecord.minecraft.rootmcshops.virtual.VirtualTradeService.purchase(
                    shops, shops.economy(), player, listingId, qty);
            return;
        }

        ShopListing shop = shops.store().getById(key);
        if (shop == null) {
            return;
        }
        if (!shop.isSellShop()) {
            return;
        }
        int qty = Math.max(1, shop.saleQty());
        player.closeInventory();
        com.rootrecord.minecraft.rootmcshops.ShopBuyService.executePurchase(
                player, shops, shops.economy(), shop, qty);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MarketMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void reopen(Player player, RootMcShopsPlugin shops, MarketMenuHolder holder, int page) {
        switch (holder.mode()) {
            case CATEGORIES -> MarketMenus.openCategories(plugin, shops, player);
            case HUB -> {
                List<MarketMenus.HubRow> rows = MarketMenus.buildHubRows(shops, holder.category(), plugin);
                if (rows.isEmpty()) {
                    player.closeInventory();
                    player.sendMessage(plugin.msg("hub-empty"));
                    return;
                }
                MarketMenus.openHubPage(plugin, shops, player, rows, page, holder.category());
            }
            case ITEM -> {
                List<MarketMenus.MixedOffer> offers =
                        MarketMenus.mixedOffersForItem(plugin, shops, holder.itemKey());
                if (offers.isEmpty()) {
                    player.closeInventory();
                    player.sendMessage(plugin.msg("item-empty")
                            .replace("{item}", holder.itemKey().toLowerCase(Locale.ROOT)));
                    return;
                }
                MarketMenus.openItemPage(
                        plugin, shops, player, holder.itemKey(), offers, page, holder.category());
            }
            case PLAYER -> {
                MarketMenus.OwnerMatch match =
                        new MarketMenus.OwnerMatch(holder.ownerUuid(), holder.ownerName());
                List<ShopListing> listings =
                        MarketMenus.listingsForOwner(shops, match.ownerUuid(), match.ownerName());
                if (listings.isEmpty()) {
                    player.closeInventory();
                    player.sendMessage(plugin.msg("player-empty").replace("{player}", match.ownerName()));
                    return;
                }
                MarketMenus.openPlayerPage(plugin, shops, player, match, listings, page);
            }
        }
    }
}
