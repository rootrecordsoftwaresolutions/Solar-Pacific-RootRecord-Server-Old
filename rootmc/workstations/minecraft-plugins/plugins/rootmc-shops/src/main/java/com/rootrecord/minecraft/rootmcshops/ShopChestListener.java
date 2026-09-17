package com.rootrecord.minecraft.rootmcshops;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Container right-click — buyers get a purchase quote; owners open stock normally. */
public final class ShopChestListener implements Listener {

    private final RootMcShopsPlugin plugin;
    private final ShopStore store;
    private final ShopEconomy economy;

    public ShopChestListener(RootMcShopsPlugin plugin, ShopStore store, ShopInputManager inputs, ShopEconomy economy) {
        this.plugin = plugin;
        this.store = store;
        this.economy = economy;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!ShopContainers.isShopContainer(block)) {
            return;
        }

        Player player = event.getPlayer();
        ShopListing shop = ShopProtection.shopForContainerBlock(store, block);
        if (shop == null) {
            return;
        }
        if (ShopService.isOwner(shop, player)) {
            return;
        }

        if (shop.isBuyShop()) {
            event.setCancelled(true);
            ShopSellService.offerSale(player, plugin, economy, shop);
            return;
        }

        event.setCancelled(true);
        ShopBuyService.offerPurchase(player, plugin, economy, shop);
    }
}
