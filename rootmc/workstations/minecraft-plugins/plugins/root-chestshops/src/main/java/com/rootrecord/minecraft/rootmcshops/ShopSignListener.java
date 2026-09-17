package com.rootrecord.minecraft.rootmcshops;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Sign clicks — owner shop editor, buyer purchase quote. */
public final class ShopSignListener implements Listener {

    private final RootMcShopsPlugin plugin;
    private final ShopStore store;
    private final ShopInputManager inputs;
    private final ShopEconomy economy;

    public ShopSignListener(
            RootMcShopsPlugin plugin,
            ShopStore store,
            ShopInputManager inputs,
            ShopEconomy economy) {
        this.plugin = plugin;
        this.store = store;
        this.inputs = inputs;
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
        if (block == null || !ShopSigns.isSignMaterial(block.getType())) {
            return;
        }

        ShopListing shop = store.getBySignBlock(block);
        if (shop == null) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (ShopService.isOwner(shop, player)) {
            inputs.clear(player.getUniqueId());
            ShopEditMenu.open(plugin, player, shop);
            return;
        }

        if (shop.isBuyShop()) {
            ShopSellService.offerSale(player, plugin, economy, shop);
            return;
        }

        ShopBuyService.offerPurchase(player, plugin, economy, shop);
    }
}
