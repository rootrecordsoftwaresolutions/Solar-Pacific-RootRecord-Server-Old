package com.rootrecord.minecraft.rootmcshops.virtual;

import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.rootmcshops.RootMcShopsPlugin;
import com.rootrecord.minecraft.rootmcshops.ShopEconomy;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Gold + stock settlement for virtual sell listings. */
public final class VirtualTradeService {

    private VirtualTradeService() {}

    public static boolean purchase(
            RootMcShopsPlugin plugin,
            ShopEconomy economy,
            Player buyer,
            String listingId,
            int qty) {
        VirtualListingStore listings = plugin.virtualListings();
        VirtualItemStore bins = plugin.virtualItems();
        if (listings == null || bins == null || !listings.enabled() || qty <= 0) {
            buyer.sendMessage(plugin.msg("virtual-disabled"));
            return false;
        }
        VirtualListing listing = listings.get(listingId);
        if (listing == null || !listing.isSell() || listing.qty() <= 0) {
            buyer.sendMessage(plugin.msg("virtual-listing-gone"));
            return false;
        }
        if (listing.ownerUuid().equals(buyer.getUniqueId())) {
            buyer.sendMessage(plugin.msg("buy-own-shop"));
            return false;
        }
        int buyQty = Math.min(qty, listing.qty());
        double total = listing.price() * buyQty;
        if (!economy.has(buyer, total)) {
            buyer.sendMessage(plugin.msg("buy-insufficient")
                    .replace("{total}", GoldMoney.format(total))
                    .replace("{balance}", GoldMoney.format(economy.balance(buyer))));
            return false;
        }
        if (!economy.withdraw(buyer, total)) {
            buyer.sendMessage(plugin.msg("buy-insufficient")
                    .replace("{total}", GoldMoney.format(total))
                    .replace("{balance}", GoldMoney.format(economy.balance(buyer))));
            return false;
        }

        List<ItemStack> stacks = listings.takeSellStock(listingId, buyer.getUniqueId(), buyQty);
        if (stacks.isEmpty()) {
            economy.depositToPlayer(buyer.getUniqueId(), total);
            buyer.sendMessage(plugin.msg("virtual-listing-gone"));
            return false;
        }
        int got = stacks.stream().mapToInt(ItemStack::getAmount).sum();
        if (got < buyQty) {
            // partial — refund difference
            double refund = listing.price() * (buyQty - got);
            if (refund > 0) {
                economy.depositToPlayer(buyer.getUniqueId(), refund);
            }
            buyQty = got;
            total = listing.price() * buyQty;
        }

        economy.depositToPlayer(listing.ownerUuid(), total);

        for (ItemStack stack : stacks) {
            int put = bins.deposit(buyer.getUniqueId(), stack);
            if (put < stack.getAmount()) {
                ItemStack rest = stack.clone();
                rest.setAmount(stack.getAmount() - put);
                var leftover = buyer.getInventory().addItem(rest);
                for (ItemStack drop : leftover.values()) {
                    buyer.getWorld().dropItemNaturally(buyer.getLocation(), drop);
                    buyer.sendMessage(plugin.msg("virtual-overflow-drop"));
                }
            }
        }

        buyer.sendMessage(plugin.msg("virtual-buy-success")
                .replace("{qty}", String.valueOf(buyQty))
                .replace("{item}", listing.itemKey().toLowerCase(Locale.ROOT))
                .replace("{total}", GoldMoney.format(total))
                .replace("{seller}", listing.ownerName()));

        Player sellerOnline = plugin.getServer().getPlayer(listing.ownerUuid());
        if (sellerOnline != null) {
            sellerOnline.sendMessage(plugin.msg("virtual-sale-notify")
                    .replace("{buyer}", buyer.getName())
                    .replace("{qty}", String.valueOf(buyQty))
                    .replace("{item}", listing.itemKey().toLowerCase(Locale.ROOT))
                    .replace("{total}", GoldMoney.format(total)));
        }
        return true;
    }

    public static boolean listFromBin(
            RootMcShopsPlugin plugin,
            Player player,
            long binId,
            int qty,
            double price) {
        VirtualListingStore listings = plugin.virtualListings();
        if (listings == null || !listings.enabled()) {
            player.sendMessage(plugin.msg("virtual-disabled"));
            return false;
        }
        if (!plugin.validatePrice(
                plugin.virtualItems().getBin(binId) != null
                        ? plugin.virtualItems().getBin(binId).itemKey()
                        : "STONE",
                price)) {
            VirtualBinEntry bin = plugin.virtualItems().getBin(binId);
            plugin.sendPriceTooHigh(player, bin == null ? "?" : bin.itemKey(), price);
            return false;
        }
        String id = listings.createSellFromBin(player.getUniqueId(), player.getName(), binId, qty, price);
        if (id == null) {
            player.sendMessage(plugin.msg("virtual-list-failed"));
            return false;
        }
        player.sendMessage(plugin.msg("virtual-list-success")
                .replace("{qty}", String.valueOf(qty))
                .replace("{price}", GoldMoney.format(price))
                .replace("{id}", id));
        return true;
    }

    public static boolean cancelListing(RootMcShopsPlugin plugin, Player player, String listingId) {
        VirtualListingStore listings = plugin.virtualListings();
        if (listings == null || !listings.enabled()) {
            player.sendMessage(plugin.msg("virtual-disabled"));
            return false;
        }
        boolean admin = player.hasPermission("rootshops.admin");
        if (!listings.cancel(player.getUniqueId(), listingId, admin)) {
            player.sendMessage(plugin.msg("virtual-cancel-failed"));
            return false;
        }
        player.sendMessage(plugin.msg("virtual-cancel-success"));
        return true;
    }
}
