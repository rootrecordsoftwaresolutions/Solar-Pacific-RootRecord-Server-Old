package com.rootrecord.minecraft.rootmcshops;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.List;

public final class PlayerShopsMenuListener implements Listener {

    private final RootMcShopsPlugin plugin;
    private final ShopStore store;

    public PlayerShopsMenuListener(RootMcShopsPlugin plugin, ShopStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PlayerShopsMenuHolder holder)) {
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

        if (slot == PlayerShopsMenu.SLOT_PREV && holder.page() > 0) {
            reopen(player, holder, holder.page() - 1);
            return;
        }
        if (slot == PlayerShopsMenu.SLOT_NEXT) {
            reopen(player, holder, holder.page() + 1);
            return;
        }
        if (slot >= PlayerShopsMenu.PAGE_SIZE) {
            return;
        }

        List<String> shopIds = holder.shopIds();
        if (slot >= shopIds.size()) {
            return;
        }
        ShopListing shop = store.getById(shopIds.get(slot));
        if (shop == null) {
            return;
        }
        player.sendMessage(plugin.colorize(plugin.rawMsg("shops-gui-coords")
                .replace("{player}", holder.ownerDisplayName())
                .replace("{item}", shop.itemKey())
                .replace("{world}", shop.world())
                .replace("{x}", String.valueOf(shop.x()))
                .replace("{y}", String.valueOf(shop.y()))
                .replace("{z}", String.valueOf(shop.z()))
                .replace("{price}", String.format(java.util.Locale.US, "%.3f", shop.price()))));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PlayerShopsMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void reopen(Player player, PlayerShopsMenuHolder holder, int page) {
        PlayerShopsMenu.OwnerMatch match =
                new PlayerShopsMenu.OwnerMatch(holder.ownerUuid(), holder.ownerDisplayName());
        List<ShopListing> listings =
                PlayerShopsMenu.listingsForOwner(store, match.ownerUuid(), match.ownerName());
        if (listings.isEmpty()) {
            player.closeInventory();
            player.sendMessage(plugin.colorize(plugin.rawMsg("shops-player-empty")
                    .replace("{player}", match.ownerName())));
            return;
        }
        PlayerShopsMenu.openPage(plugin, player, match, listings, page);
    }
}
