package com.rootrecord.minecraft.rootmcshops;

import com.rootrecord.minecraft.common.ChatLinks;
import com.rootrecord.minecraft.common.RootMcBondTransferResolver;
import com.rootrecord.minecraft.common.RootMcBondTransferService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Quotes and executes sales to player buy shops (shop pays you for items). */
public final class ShopSellService {

    private ShopSellService() {}

    public static List<ShopListing> buyShopsWithCapacityForSeller(
            RootMcShopsPlugin plugin,
            Player player,
            String itemKey) {
        String key = itemKey.toUpperCase(Locale.ROOT);
        return plugin.store().all().stream()
                .filter(ShopListing::isBuyShop)
                .filter(s -> s.itemKey().equalsIgnoreCase(key))
                .filter(s -> plugin.countStock(s, true) > 0)
                .filter(s -> !ShopService.isOwner(s, player))
                .sorted(Comparator.comparingDouble(ShopListing::price).reversed().thenComparing(ShopListing::id))
                .collect(Collectors.toList());
    }

    public static Optional<ShopListing> bestBuyShop(RootMcShopsPlugin plugin, Player player, String itemKey) {
        return buyShopsWithCapacityForSeller(plugin, player, itemKey).stream().findFirst();
    }

    /** @deprecated use {@link #bestBuyShop(RootMcShopsPlugin, Player, String)} */
    public static Optional<ShopListing> bestBuyShop(RootMcShopsPlugin plugin, String itemKey) {
        return plugin.store().all().stream()
                .filter(ShopListing::isBuyShop)
                .filter(s -> s.itemKey().equalsIgnoreCase(itemKey.toUpperCase(Locale.ROOT)))
                .filter(s -> plugin.countStock(s, true) > 0)
                .max(Comparator.comparingDouble(ShopListing::price));
    }

    public static List<ShopListing> buyShopsWithCapacity(RootMcShopsPlugin plugin, String itemKey) {
        return buyShopsWithCapacityForSeller(plugin, null, itemKey);
    }

    public static void sendBestQuote(
            Player player,
            RootMcShopsPlugin plugin,
            ShopEconomy economy,
            String itemKey,
            int qty) {
        List<ShopListing> listings = buyShopsWithCapacityForSeller(plugin, player, itemKey);
        if (listings.isEmpty()) {
            sendNoBuyShops(player, plugin, itemKey);
            return;
        }
        sendBuyerList(player, plugin, listings);

        int playerQty = countPlayerItems(player, itemKey);
        if (playerQty <= 0) {
            player.sendMessage(plugin.colorize(
                    plugin.rawMsg("sell-no-items").replace("{item}", itemKey.toUpperCase(Locale.ROOT))));
            return;
        }

        List<ShopListing> tier = ShopMarketSplit.tierAtBestPrice(listings);
        int sellQty = Math.min(qty, playerQty);
        Map<ShopListing, Integer> plan = ShopMarketSplit.allocateEqualShare(
                tier,
                sellQty,
                shop -> plugin.countStock(shop, true));
        int planned = plan.values().stream().mapToInt(Integer::intValue).sum();
        if (planned <= 0) {
            sendNoBuyShops(player, plugin, itemKey);
            return;
        }

        String confirmCmd = "/sell confirm " + itemKey.toUpperCase(Locale.ROOT) + " " + planned;
        sendSplitSellQuote(player, plugin, economy, itemKey, plan, planned, confirmCmd);
    }

    private static void sendSplitSellQuote(
            Player player,
            RootMcShopsPlugin plugin,
            ShopEconomy economy,
            String itemKey,
            Map<ShopListing, Integer> plan,
            int sellQty,
            String confirmCommand) {
        double each = plan.keySet().iterator().next().price();
        double gross = each * sellQty;

        player.sendMessage(plugin.colorize(plugin.rawMsg("sell-quote-note")));
        if (plan.size() > ShopMarketSplit.SPLIT_DETAIL_MAX_PLAYERS) {
            player.sendMessage(plugin.colorize(
                    plugin.rawMsg("sell-split-many")
                            .replace("{qty}", String.valueOf(sellQty))
                            .replace("{item}", itemKey.toUpperCase(Locale.ROOT))
                            .replace("{count}", String.valueOf(plan.size()))
                            .replace("{price}", ShopBuyService.formatGold(each))));
        } else {
            for (Map.Entry<ShopListing, Integer> entry : plan.entrySet()) {
                ShopListing listing = entry.getKey();
                int lineQty = entry.getValue();
                String buyer = listing.ownerName() != null && !listing.ownerName().isBlank()
                        ? listing.ownerName()
                        : "player shop";
                player.sendMessage(plugin.colorize(
                        plugin.rawMsg("sell-split-line")
                                .replace("{qty}", String.valueOf(lineQty))
                                .replace("{item}", listing.itemKey())
                                .replace("{buyer}", buyer)
                                .replace("{price}", ShopBuyService.formatGold(each))
                                .replace("{capacity}", String.valueOf(plugin.countStock(listing)))));
            }
        }

        Component line = Component.text()
                .append(Component.text("Sell ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(sellQty), NamedTextColor.WHITE))
                .append(Component.text("x ", NamedTextColor.GRAY))
                .append(Component.text(itemKey.toUpperCase(Locale.ROOT), NamedTextColor.WHITE))
                .append(Component.text(" @ ", NamedTextColor.GRAY))
                .append(Component.text(ShopBuyService.formatGold(each), NamedTextColor.GOLD))
                .append(Component.text(" G each", NamedTextColor.GRAY))
                .append(Component.text(" → ", NamedTextColor.GRAY))
                .append(Component.text(ShopBuyService.formatGold(gross), NamedTextColor.GREEN))
                .append(Component.text(" G gross", NamedTextColor.GRAY))
                .append(Component.text(
                        plan.size() > 1 ? " (split across " + plan.size() + " buy shops)" : "",
                        NamedTextColor.GRAY))
                .build();
        player.sendMessage(line);

        for (ShopListing listing : plan.keySet()) {
            double lineTotal = each * plan.get(listing);
            if (!economy.hasOwner(listing.ownerUuid(), lineTotal)) {
                String buyer = listing.ownerName() != null ? listing.ownerName() : "shop";
                player.sendMessage(plugin.colorize(
                        plugin.rawMsg("sell-owner-broke")
                                .replace("{buyer}", buyer)
                                .replace("{total}", ShopBuyService.formatGold(lineTotal))));
                return;
            }
        }
        player.sendMessage(ChatLinks.confirmCancel(confirmCommand, "/sell cancel"));
    }

    public static boolean executeSplitSale(
            Player player,
            RootMcShopsPlugin plugin,
            ShopEconomy economy,
            String itemKey,
            int qty) {
        List<ShopListing> listings = buyShopsWithCapacityForSeller(plugin, player, itemKey);
        if (listings.isEmpty()) {
            sendNoBuyShops(player, plugin, itemKey);
            return true;
        }
        int playerQty = countPlayerItems(player, itemKey);
        if (playerQty <= 0) {
            player.sendMessage(plugin.colorize(
                    plugin.rawMsg("sell-no-items").replace("{item}", itemKey.toUpperCase(Locale.ROOT))));
            return true;
        }

        List<ShopListing> tier = ShopMarketSplit.tierAtBestPrice(listings);
        int sellQty = Math.min(qty, playerQty);
        Map<ShopListing, Integer> plan = ShopMarketSplit.allocateEqualShare(
                tier,
                sellQty,
                shop -> plugin.countStock(shop, true));
        int planned = plan.values().stream().mapToInt(Integer::intValue).sum();
        if (planned <= 0) {
            sendNoBuyShops(player, plugin, itemKey);
            return true;
        }

        int sold = 0;
        double gross = 0;
        double tax = 0;
        double net = 0;
        List<String> buyers = new ArrayList<>();
        for (Map.Entry<ShopListing, Integer> entry : plan.entrySet()) {
            SaleResult result = executeSale(player, plugin, economy, entry.getKey(), entry.getValue(), true);
            if (!result.success()) {
                if (sold <= 0) {
                    sendNoBuyShops(player, plugin, itemKey);
                    return true;
                }
                break;
            }
            sold += result.qty();
            gross += result.gross();
            tax += result.tax();
            net += result.net();
            buyers.add(result.buyer());
        }

        if (plan.size() > ShopMarketSplit.SPLIT_DETAIL_MAX_PLAYERS) {
            player.sendMessage(plugin.msg("sell-split-success-many")
                    .replace("{qty}", String.valueOf(sold))
                    .replace("{item}", itemKey.toUpperCase(Locale.ROOT))
                    .replace("{gross}", ShopBuyService.formatGold(gross))
                    .replace("{tax}", ShopBuyService.formatGold(tax))
                    .replace("{net}", ShopBuyService.formatGold(net))
                    .replace("{count}", String.valueOf(plan.size())));
        } else {
            player.sendMessage(plugin.msg("sell-split-success")
                    .replace("{qty}", String.valueOf(sold))
                    .replace("{item}", itemKey.toUpperCase(Locale.ROOT))
                    .replace("{gross}", ShopBuyService.formatGold(gross))
                    .replace("{tax}", ShopBuyService.formatGold(tax))
                    .replace("{net}", ShopBuyService.formatGold(net))
                    .replace("{buyers}", String.join(", ", buyers)));
        }
        return true;
    }

    /** Direct sign/chest click — always this buy shop only. */
    public static void offerSale(
            Player player,
            RootMcShopsPlugin plugin,
            ShopEconomy economy,
            ShopListing shop) {
        if (!shop.isBuyShop()) {
            ShopBuyService.offerPurchase(player, plugin, economy, shop);
            return;
        }
        int capacity = plugin.countStock(shop);
        if (capacity <= 0) {
            player.sendMessage(plugin.colorize(
                    plugin.rawMsg("shop-buy-full").replace("{item}", shop.itemKey())));
            return;
        }
        int playerQty = countPlayerItems(player, shop.itemKey());
        if (playerQty <= 0) {
            player.sendMessage(plugin.colorize(
                    plugin.rawMsg("sell-no-items").replace("{item}", shop.itemKey())));
            return;
        }
        int qty = Math.min(capacity, Math.min(playerQty, Math.max(1, shop.saleQty())));
        String confirmCmd = "/sell confirm " + shop.id() + " " + qty;
        sendDirectQuote(player, plugin, economy, shop, qty, confirmCmd);
    }

    public static void sendDirectQuote(
            Player player,
            RootMcShopsPlugin plugin,
            ShopEconomy economy,
            ShopListing listing,
            int qty,
            String confirmCommand) {
        sendQuote(player, plugin, economy, listing, qty, confirmCommand, "shop-sell-quote-note");
    }

    private static void sendQuote(
            Player player,
            RootMcShopsPlugin plugin,
            ShopEconomy economy,
            ShopListing listing,
            int qty,
            String confirmCommand,
            String noteKey) {
        int capacity = plugin.countStock(listing);
        int playerQty = countPlayerItems(player, listing.itemKey());
        int sellQty = Math.min(qty, Math.min(capacity, playerQty));
        if (sellQty <= 0) {
            sendNoBuyShops(player, plugin, listing.itemKey());
            return;
        }

        double each = listing.price();
        double total = each * sellQty;
        String buyer = listing.ownerName() != null && !listing.ownerName().isBlank()
                ? listing.ownerName()
                : "player shop";

        player.sendMessage(plugin.colorize(plugin.rawMsg(noteKey)));

        Component line = Component.text()
                .append(Component.text("Sell ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(sellQty), NamedTextColor.WHITE))
                .append(Component.text("x ", NamedTextColor.GRAY))
                .append(Component.text(listing.itemKey(), NamedTextColor.WHITE))
                .append(Component.text(" @ ", NamedTextColor.GRAY))
                .append(Component.text(ShopBuyService.formatGold(each), NamedTextColor.GOLD))
                .append(Component.text(" G each", NamedTextColor.GRAY))
                .append(Component.text(" to ", NamedTextColor.GRAY))
                .append(Component.text(buyer, NamedTextColor.AQUA))
                .append(Component.text(" (", NamedTextColor.GRAY))
                .append(Component.text(capacity + " wanted", NamedTextColor.WHITE))
                .append(Component.text(") → ", NamedTextColor.GRAY))
                .append(Component.text(ShopBuyService.formatGold(total), NamedTextColor.GREEN))
                .append(Component.text(" G total", NamedTextColor.GRAY))
                .build();
        player.sendMessage(line);

        if (!economy.hasOwner(listing.ownerUuid(), total)) {
            player.sendMessage(plugin.colorize(
                    plugin.rawMsg("sell-owner-broke")
                            .replace("{buyer}", buyer)
                            .replace("{total}", ShopBuyService.formatGold(total))));
            return;
        }

        player.sendMessage(ChatLinks.confirmCancel(confirmCommand, "/sell cancel"));
    }

    public record SaleResult(boolean success, int qty, String itemKey, String buyer, double gross, double tax, double net) {
        public static SaleResult failed() {
            return new SaleResult(false, 0, "", "", 0, 0, 0);
        }
    }

    public static boolean executeSale(
            Player player,
            RootMcShopsPlugin plugin,
            ShopEconomy economy,
            ShopListing listing,
            int qty) {
        return executeSale(player, plugin, economy, listing, qty, false).success();
    }

    public static SaleResult executeSale(
            Player player,
            RootMcShopsPlugin plugin,
            ShopEconomy economy,
            ShopListing listing,
            int qty,
            boolean quiet) {
        if (!listing.isBuyShop()) {
            boolean ok = ShopBuyService.executePurchase(player, plugin, economy, listing, qty);
            return ok ? new SaleResult(true, qty, listing.itemKey(), listing.ownerName(), 0, 0, 0) : SaleResult.failed();
        }
        Material mat = ShopItemKeys.baseMaterial(listing.itemKey());
        if (mat == null || mat.isAir()) {
            if (!quiet) {
                player.sendMessage(plugin.colorize("&cUnknown item: &f" + listing.itemKey()));
            }
            return SaleResult.failed();
        }
        if (ShopService.isOwner(listing, player)) {
            if (!quiet) {
                player.sendMessage(plugin.msg("sell-own-shop"));
            }
            return SaleResult.failed();
        }

        int capacity = plugin.countStock(listing);
        int playerQty = countPlayerItems(player, listing.itemKey());
        if (capacity <= 0 || playerQty <= 0) {
            if (!quiet) {
                sendNoBuyShops(player, plugin, listing.itemKey());
            }
            return SaleResult.failed();
        }
        int sellQty = Math.min(qty, Math.min(capacity, playerQty));
        double total = listing.price() * sellQty;

        if (!economy.hasOwner(listing.ownerUuid(), total)) {
            if (!quiet) {
                player.sendMessage(plugin.colorize(
                        plugin.rawMsg("sell-owner-broke")
                                .replace("{buyer}", listing.ownerName() != null ? listing.ownerName() : "shop")
                                .replace("{total}", ShopBuyService.formatGold(total))));
            }
            return SaleResult.failed();
        }

        String itemKey = listing.itemKey();
        if (ShopItemKeys.BONDED_NOTE.equalsIgnoreCase(itemKey)) {
            return executeBondSale(player, plugin, economy, listing, sellQty, total, quiet);
        }

        ItemStack template = firstPlayerStack(player, listing.itemKey());
        if (!withdrawPlayerItems(player, listing.itemKey(), sellQty)) {
            if (!quiet) {
                player.sendMessage(plugin.colorize(
                        plugin.rawMsg("sell-no-items").replace("{item}", listing.itemKey())));
            }
            return SaleResult.failed();
        }
        if (!ShopContainers.depositMatchingItems(listing, listing.itemKey(), sellQty, template)) {
            giveBack(player, listing.itemKey(), sellQty, template);
            if (!quiet) {
                player.sendMessage(plugin.colorize(
                        plugin.rawMsg("shop-buy-full").replace("{item}", listing.itemKey())));
            }
            return SaleResult.failed();
        }
        String ownerName = listing.ownerName() != null && !listing.ownerName().isBlank()
                ? listing.ownerName()
                : "shop";
        double net = economy.withholdTaxedPayment(
                java.util.UUID.fromString(listing.ownerUuid()),
                ownerName,
                total,
                "shop_sell");
        if (net < 0) {
            ShopContainers.withdrawMatchingItems(listing, listing.itemKey(), sellQty);
            giveBack(player, listing.itemKey(), sellQty, template);
            if (!quiet) {
                player.sendMessage(plugin.colorize(
                        plugin.rawMsg("sell-owner-broke")
                                .replace("{buyer}", listing.ownerName() != null ? listing.ownerName() : "shop")
                                .replace("{total}", ShopBuyService.formatGold(total))));
            }
            return SaleResult.failed();
        }
        double tax = Math.max(0, total - net);
        economy.depositToPlayer(player.getUniqueId(), net);
        ShopSigns.refreshListingSign(plugin, plugin.store(), listing);
        notifyOwnerPurchase(plugin, listing, player, sellQty, total);
        broadcastSale(plugin, listing, player, sellQty, total);

        if (!quiet) {
            player.sendMessage(plugin.msg("sell-success")
                    .replace("{qty}", String.valueOf(sellQty))
                    .replace("{item}", listing.itemKey())
                    .replace("{total}", ShopBuyService.formatGold(total))
                    .replace("{gross}", ShopBuyService.formatGold(total))
                    .replace("{tax}", ShopBuyService.formatGold(tax))
                    .replace("{net}", ShopBuyService.formatGold(net))
                    .replace("{buyer}", ownerName));
        }
        ShopListingSync.onChanged(plugin, listing);
        return new SaleResult(true, sellQty, listing.itemKey(), ownerName, total, tax, net);
    }

    private static SaleResult executeBondSale(
            Player player,
            RootMcShopsPlugin plugin,
            ShopEconomy economy,
            ShopListing listing,
            int sellQty,
            double total,
            boolean quiet) {
        java.util.List<ItemStack> sold = new java.util.ArrayList<>(sellQty);
        for (int i = 0; i < sellQty; i++) {
            ItemStack cert = ShopContainers.withdrawOneFromPlayer(player, ShopItemKeys.BONDED_NOTE);
            if (cert == null) {
                for (ItemStack back : sold) {
                    giveBack(player, ShopItemKeys.BONDED_NOTE, 1, back);
                }
                if (!quiet) {
                    player.sendMessage(plugin.colorize(
                            plugin.rawMsg("sell-no-items").replace("{item}", listing.itemKey())));
                }
                return SaleResult.failed();
            }
            if (!ShopContainers.depositOne(listing, cert)) {
                giveBack(player, ShopItemKeys.BONDED_NOTE, 1, cert);
                for (ItemStack back : sold) {
                    ShopContainers.withdrawOneMatching(listing, ShopItemKeys.BONDED_NOTE, true);
                    giveBack(player, ShopItemKeys.BONDED_NOTE, 1, back);
                }
                if (!quiet) {
                    player.sendMessage(plugin.colorize(
                            plugin.rawMsg("shop-buy-full").replace("{item}", listing.itemKey())));
                }
                return SaleResult.failed();
            }
            sold.add(cert);
        }

        String ownerName = listing.ownerName() != null && !listing.ownerName().isBlank()
                ? listing.ownerName()
                : "shop";
        UUID ownerId;
        try {
            ownerId = UUID.fromString(listing.ownerUuid());
        } catch (IllegalArgumentException ex) {
            for (ItemStack back : sold) {
                ShopContainers.withdrawOneMatching(listing, ShopItemKeys.BONDED_NOTE, true);
                giveBack(player, ShopItemKeys.BONDED_NOTE, 1, back);
            }
            return SaleResult.failed();
        }

        double net = economy.withholdTaxedPayment(ownerId, ownerName, total, "shop_sell");
        if (net < 0) {
            for (ItemStack back : sold) {
                ShopContainers.withdrawOneMatching(listing, ShopItemKeys.BONDED_NOTE, true);
                giveBack(player, ShopItemKeys.BONDED_NOTE, 1, back);
            }
            if (!quiet) {
                player.sendMessage(plugin.colorize(
                        plugin.rawMsg("sell-owner-broke")
                                .replace("{buyer}", ownerName)
                                .replace("{total}", ShopBuyService.formatGold(total))));
            }
            return SaleResult.failed();
        }

        RootMcBondTransferService bondTransfer = RootMcBondTransferResolver.resolve(plugin);
        if (bondTransfer != null) {
            bondTransfer.transferCertificatesTo(ownerId, ownerName, sold.toArray(new ItemStack[0]));
        }

        double tax = Math.max(0, total - net);
        economy.depositToPlayer(player.getUniqueId(), net);
        ShopSigns.refreshListingSign(plugin, plugin.store(), listing);
        notifyOwnerPurchase(plugin, listing, player, sellQty, total);
        broadcastSale(plugin, listing, player, sellQty, total);

        if (!quiet) {
            player.sendMessage(plugin.msg("sell-success")
                    .replace("{qty}", String.valueOf(sellQty))
                    .replace("{item}", listing.itemKey())
                    .replace("{total}", ShopBuyService.formatGold(total))
                    .replace("{gross}", ShopBuyService.formatGold(total))
                    .replace("{tax}", ShopBuyService.formatGold(tax))
                    .replace("{net}", ShopBuyService.formatGold(net))
                    .replace("{buyer}", ownerName));
        }
        ShopListingSync.onChanged(plugin, listing);
        return new SaleResult(true, sellQty, listing.itemKey(), ownerName, total, tax, net);
    }

    private static void sendBuyerList(Player player, RootMcShopsPlugin plugin, List<ShopListing> listings) {
        player.sendMessage(plugin.colorize(plugin.rawMsg("sell-buyers-header")));
        int shown = 0;
        for (ShopListing listing : listings) {
            if (shown >= 8) {
                break;
            }
            String buyer = listing.ownerName() != null && !listing.ownerName().isBlank()
                    ? listing.ownerName()
                    : "unknown";
            int capacity = plugin.countStock(listing);
            player.sendMessage(plugin.colorize(
                    plugin.rawMsg("sell-buyer-line")
                            .replace("{buyer}", buyer)
                            .replace("{price}", ShopBuyService.formatGold(listing.price()))
                            .replace("{capacity}", String.valueOf(capacity))
                            .replace("{item}", listing.itemKey())));
            shown++;
        }
        if (listings.size() > shown) {
            player.sendMessage(plugin.colorize(
                    plugin.rawMsg("sell-buyers-more").replace("{count}", String.valueOf(listings.size() - shown))));
        }
    }

    public static void sendNoBuyShops(Player player, RootMcShopsPlugin plugin, String itemKey) {
        player.sendMessage(plugin.colorize(
                plugin.rawMsg("sell-no-shops").replace("{item}", itemKey.toUpperCase(Locale.ROOT))));
    }

    private static int countPlayerItems(Player player, String itemKey) {
        if (itemKey == null || itemKey.isBlank()) {
            return 0;
        }
        int count = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (ShopItemKeys.matches(stack, itemKey)) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private static ItemStack firstPlayerStack(Player player, String itemKey) {
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (ShopItemKeys.matches(stack, itemKey)) {
                return stack.clone();
            }
        }
        return null;
    }

    private static boolean withdrawPlayerItems(Player player, String itemKey, int quantity) {
        int remaining = quantity;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack stack = contents[slot];
            if (!ShopItemKeys.matches(stack, itemKey)) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) {
                contents[slot] = null;
            }
            remaining -= take;
        }
        player.getInventory().setStorageContents(contents);
        return remaining == 0;
    }

    private static void giveBack(Player player, String itemKey, int quantity, ItemStack template) {
        Material mat = ShopItemKeys.baseMaterial(itemKey);
        if (mat == null || mat.isAir()) {
            return;
        }
        int remaining = quantity;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, mat.getMaxStackSize());
            ItemStack stack = template != null ? template.clone() : new ItemStack(mat, stackSize);
            stack.setAmount(stackSize);
            var leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                leftover.values().forEach(item ->
                        player.getWorld().dropItemNaturally(player.getLocation(), item));
            }
            remaining -= stackSize;
        }
    }

    private static void notifyOwnerPurchase(
            RootMcShopsPlugin plugin,
            ShopListing listing,
            Player seller,
            int qty,
            double total) {
        if (listing.ownerUuid() == null || listing.ownerUuid().isBlank()) {
            return;
        }
        UUID ownerId;
        try {
            ownerId = UUID.fromString(listing.ownerUuid());
        } catch (IllegalArgumentException ex) {
            return;
        }
        if (ownerId.equals(seller.getUniqueId())) {
            return;
        }
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline()) {
            return;
        }
        owner.sendMessage(plugin.msg("buy-notify")
                .replace("{seller}", seller.getName())
                .replace("{qty}", String.valueOf(qty))
                .replace("{item}", listing.itemKey())
                .replace("{total}", ShopBuyService.formatGold(total)));
    }

    private static void broadcastSale(
            RootMcShopsPlugin plugin,
            ShopListing listing,
            Player seller,
            int qty,
            double total) {
        String buyer = listing.ownerName() != null && !listing.ownerName().isBlank()
                ? listing.ownerName()
                : "shop";
        String line = plugin.rawMsg("sell-buyer-log")
                .replace("{seller}", seller.getName())
                .replace("{buyer}", buyer)
                .replace("{qty}", String.valueOf(qty))
                .replace("{item}", listing.itemKey())
                .replace("{total}", ShopBuyService.formatGold(total));
        plugin.getLogger().info(line.replaceAll("(?i)[§&][0-9a-fk-or]", ""));
    }
}
