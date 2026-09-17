package com.rootrecord.minecraft.rootmcshops;

import com.rootrecord.minecraft.common.ChatLinks;
import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.common.RootMcBondTransferResolver;
import com.rootrecord.minecraft.common.RootMcBondTransferService;
import com.rootrecord.minecraft.common.SystemGoldPayout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

/** Quotes and executes purchases from player sell shops. */
public final class ShopBuyService {

    private ShopBuyService() {}

    public static Optional<ShopListing> cheapestInStock(RootMcShopsPlugin plugin, Player player, String itemKey) {
        return listingsInStockForBuyer(plugin, player, itemKey).stream().findFirst();
    }

    public static List<ShopListing> listingsInStockForBuyer(
            RootMcShopsPlugin plugin,
            Player player,
            String itemKey) {
        String key = itemKey.toUpperCase(Locale.ROOT);
        return plugin.store().all().stream()
                .filter(ShopListing::isSellShop)
                .filter(s -> s.itemKey().equalsIgnoreCase(key))
                .filter(s -> plugin.countStock(s, true) > 0)
                .filter(s -> !ShopService.isOwner(s, player))
                .sorted(Comparator.comparingDouble(ShopListing::price).thenComparing(ShopListing::id))
                .collect(Collectors.toList());
    }

    public static void sendCheapestQuote(
            Player player,
            RootMcShopsPlugin plugin,
            ShopEconomy economy,
            String itemKey,
            int qty) {
        List<ShopListing> listings = listingsInStockForBuyer(plugin, player, itemKey);
        if (listings.isEmpty()) {
            sendNoShopsSelling(player, plugin, itemKey);
            return;
        }
        sendSellerList(player, plugin, listings);

        List<ShopListing> tier = ShopMarketSplit.tierAtBestPrice(listings);
        Map<ShopListing, Integer> plan = ShopMarketSplit.allocateEqualShare(
                tier,
                qty,
                shop -> plugin.countStock(shop, true));
        int buyQty = plan.values().stream().mapToInt(Integer::intValue).sum();
        if (buyQty <= 0) {
            sendNoShopsSelling(player, plugin, itemKey);
            return;
        }

        String confirmCmd = "/buy confirm " + itemKey.toUpperCase(Locale.ROOT) + " " + buyQty;
        sendSplitQuote(player, plugin, economy, itemKey, plan, buyQty, confirmCmd);
    }

    private static void sendSplitQuote(
            Player player,
            RootMcShopsPlugin plugin,
            ShopEconomy economy,
            String itemKey,
            Map<ShopListing, Integer> plan,
            int buyQty,
            String confirmCommand) {
        double each = plan.keySet().iterator().next().price();
        double total = each * buyQty;

        player.sendMessage(plugin.colorize(plugin.rawMsg("buy-quote-note")));
        if (plan.size() > ShopMarketSplit.SPLIT_DETAIL_MAX_PLAYERS) {
            player.sendMessage(plugin.colorize(
                    plugin.rawMsg("buy-split-many")
                            .replace("{qty}", String.valueOf(buyQty))
                            .replace("{item}", itemKey.toUpperCase(Locale.ROOT))
                            .replace("{count}", String.valueOf(plan.size()))
                            .replace("{price}", formatGold(each))));
        } else {
            for (Map.Entry<ShopListing, Integer> entry : plan.entrySet()) {
                ShopListing listing = entry.getKey();
                int lineQty = entry.getValue();
                String seller = listing.ownerName() != null && !listing.ownerName().isBlank()
                        ? listing.ownerName()
                        : "player shop";
                player.sendMessage(plugin.colorize(
                        plugin.rawMsg("buy-split-line")
                                .replace("{qty}", String.valueOf(lineQty))
                                .replace("{item}", listing.itemKey())
                                .replace("{seller}", seller)
                                .replace("{price}", formatGold(each))
                                .replace("{stock}", String.valueOf(plugin.countStock(listing)))));
            }
        }

        Component totalLine = Component.text()
                .append(Component.text(String.valueOf(buyQty), NamedTextColor.WHITE))
                .append(Component.text("x ", NamedTextColor.GRAY))
                .append(Component.text(itemKey.toUpperCase(Locale.ROOT), NamedTextColor.WHITE))
                .append(Component.text(" @ ", NamedTextColor.GRAY))
                .append(Component.text(formatGold(each), NamedTextColor.GOLD))
                .append(Component.text(" G each", NamedTextColor.GRAY))
                .append(Component.text(" -> ", NamedTextColor.GRAY))
                .append(Component.text(formatGold(total), NamedTextColor.GREEN))
                .append(Component.text(" G total", NamedTextColor.GRAY))
                .append(Component.text(
                        plan.size() > 1 ? " (split across " + plan.size() + " shops)" : "",
                        NamedTextColor.GRAY))
                .build();
        player.sendMessage(totalLine);

        if (!canAfford(economy, player, total)) {
            player.sendMessage(plugin.colorize(
                    plugin.rawMsg("buy-insufficient")
                            .replace("{total}", formatGold(total))
                            .replace("{balance}", formatGold(economy.balance(player)))));
            return;
        }
        player.sendMessage(ChatLinks.confirmCancel(confirmCommand, "/buy cancel"));
    }

    private static void sendSellerList(Player player, RootMcShopsPlugin plugin, List<ShopListing> listings) {
        player.sendMessage(plugin.colorize(plugin.rawMsg("buy-sellers-header")));
        int shown = 0;
        for (ShopListing listing : listings) {
            if (shown >= 8) {
                break;
            }
            String seller = listing.ownerName() != null && !listing.ownerName().isBlank()
                    ? listing.ownerName()
                    : "unknown";
            int stock = plugin.countStock(listing);
            player.sendMessage(plugin.colorize(
                    plugin.rawMsg("buy-seller-line")
                            .replace("{seller}", seller)
                            .replace("{price}", formatGold(listing.price()))
                            .replace("{stock}", String.valueOf(stock))
                            .replace("{item}", listing.itemKey())));
            shown++;
        }
        if (listings.size() > shown) {
            player.sendMessage(plugin.colorize(
                    plugin.rawMsg("buy-sellers-more").replace("{count}", String.valueOf(listings.size() - shown))));
        }
    }

    public static void sendNoShopsSelling(Player player, RootMcShopsPlugin plugin, String itemKey) {
        String key = itemKey.toUpperCase(Locale.ROOT);
        boolean ownWithStock = false;
        boolean otherWithStock = false;
        for (ShopListing shop : plugin.store().all()) {
            if (!shop.isSellShop() || !shop.itemKey().equalsIgnoreCase(key) || plugin.countStock(shop, true) <= 0) {
                continue;
            }
            if (ShopService.isOwner(shop, player)) {
                ownWithStock = true;
            } else {
                otherWithStock = true;
            }
        }
        if (ownWithStock && !otherWithStock) {
            player.sendMessage(plugin.colorize(plugin.rawMsg("buy-own-only").replace("{item}", key)));
            return;
        }
        player.sendMessage(plugin.colorize(plugin.rawMsg("buy-no-shops").replace("{item}", key)));
    }

    /** Direct sign/chest click — always this shop only. */
    public static void offerPurchase(
            Player player,
            RootMcShopsPlugin plugin,
            ShopEconomy economy,
            ShopListing shop) {
        Material mat = ShopItemKeys.baseMaterial(shop.itemKey());
        if (mat == null) {
            return;
        }
        if (ShopService.isOwner(shop, player)) {
            player.sendMessage(plugin.msg("buy-own-shop"));
            return;
        }
        int available = plugin.countStock(shop);
        if (available <= 0) {
            player.sendMessage(plugin.colorize(
                    plugin.rawMsg("shop-stock-gone").replace("{item}", shop.itemKey())));
            return;
        }
        int qty = Math.min(available, Math.max(1, shop.saleQty()));
        String confirmCmd = "/buy confirm " + shop.id() + " " + qty;
        sendDirectShopQuote(player, plugin, economy, shop, qty, confirmCmd);
    }

    public static void sendDirectShopQuote(
            Player player,
            RootMcShopsPlugin plugin,
            ShopEconomy economy,
            ShopListing listing,
            int qty,
            String confirmCommand) {
        sendSingleQuote(player, plugin, economy, listing, qty, confirmCommand, "shop-quote-note");
    }

    public static boolean executeSplitPurchase(
            Player player,
            RootMcShopsPlugin plugin,
            ShopEconomy economy,
            String itemKey,
            int qty) {
        List<ShopListing> listings = listingsInStockForBuyer(plugin, player, itemKey);
        if (listings.isEmpty()) {
            sendNoShopsSelling(player, plugin, itemKey);
            return true;
        }

        List<ShopListing> tier = ShopMarketSplit.tierAtBestPrice(listings);
        Map<ShopListing, Integer> plan = ShopMarketSplit.allocateEqualShare(
                tier,
                qty,
                shop -> plugin.countStock(shop, true));
        int planned = plan.values().stream().mapToInt(Integer::intValue).sum();
        if (planned <= 0) {
            sendNoShopsSelling(player, plugin, itemKey);
            return true;
        }

        double totalCost = planned * tier.get(0).price();
        if (!canAfford(economy, player, totalCost)) {
            player.sendMessage(plugin.colorize(
                    plugin.rawMsg("buy-insufficient")
                            .replace("{total}", formatGold(totalCost))
                            .replace("{balance}", formatGold(economy.balance(player)))));
            return true;
        }

        int bought = 0;
        double spent = 0;
        List<String> sellers = new ArrayList<>();
        for (Map.Entry<ShopListing, Integer> entry : plan.entrySet()) {
            PurchaseResult result = executePurchaseInternal(
                    player, plugin, economy, entry.getKey(), entry.getValue(), true);
            if (!result.success()) {
                if (bought > 0) {
                    break;
                }
                player.sendMessage(plugin.colorize(
                        plugin.rawMsg("buy-stock-gone").replace("{item}", itemKey.toUpperCase(Locale.ROOT))));
                return true;
            }
            bought += result.qty();
            spent += result.total();
            sellers.add(result.seller());
        }

        if (plan.size() > ShopMarketSplit.SPLIT_DETAIL_MAX_PLAYERS) {
            player.sendMessage(plugin.msg("buy-split-success-many")
                    .replace("{qty}", String.valueOf(bought))
                    .replace("{item}", itemKey.toUpperCase(Locale.ROOT))
                    .replace("{total}", formatGold(spent))
                    .replace("{count}", String.valueOf(plan.size())));
        } else {
            player.sendMessage(plugin.msg("buy-split-success")
                    .replace("{qty}", String.valueOf(bought))
                    .replace("{item}", itemKey.toUpperCase(Locale.ROOT))
                    .replace("{total}", formatGold(spent))
                    .replace("{sellers}", String.join(", ", sellers)));
        }
        return true;
    }

    private static void sendSingleQuote(
            Player player,
            RootMcShopsPlugin plugin,
            ShopEconomy economy,
            ShopListing listing,
            int qty,
            String confirmCommand,
            String noteKey) {
        int stock = plugin.countStock(listing);
        int buyQty = Math.min(qty, stock);
        Material mat = ShopItemKeys.baseMaterial(listing.itemKey());
        if (mat == null || buyQty <= 0) {
            sendNoShopsSelling(player, plugin, listing.itemKey());
            return;
        }

        double each = listing.price();
        double total = each * buyQty;
        String seller = listing.ownerName() != null && !listing.ownerName().isBlank()
                ? listing.ownerName()
                : "player shop";

        player.sendMessage(plugin.colorize(plugin.rawMsg(noteKey)));

        Component line = Component.text()
                .append(Component.text(String.valueOf(buyQty), NamedTextColor.WHITE))
                .append(Component.text("x ", NamedTextColor.GRAY))
                .append(Component.text(listing.itemKey(), NamedTextColor.WHITE))
                .append(Component.text(" @ ", NamedTextColor.GRAY))
                .append(Component.text(formatGold(each), NamedTextColor.GOLD))
                .append(Component.text(" G each", NamedTextColor.GRAY))
                .append(Component.text(" from ", NamedTextColor.GRAY))
                .append(Component.text(seller, NamedTextColor.AQUA))
                .append(Component.text(" (", NamedTextColor.GRAY))
                .append(Component.text(stock + " in stock", NamedTextColor.WHITE))
                .append(Component.text(") -> ", NamedTextColor.GRAY))
                .append(Component.text(formatGold(total), NamedTextColor.GREEN))
                .append(Component.text(" G total", NamedTextColor.GRAY))
                .build();
        player.sendMessage(line);

        if (!canAfford(economy, player, total)) {
            player.sendMessage(plugin.colorize(
                    plugin.rawMsg("buy-insufficient")
                            .replace("{total}", formatGold(total))
                            .replace("{balance}", formatGold(economy.balance(player)))));
            return;
        }
        player.sendMessage(ChatLinks.confirmCancel(confirmCommand, "/buy cancel"));
    }

    public static boolean executePurchase(
            Player player,
            RootMcShopsPlugin plugin,
            ShopEconomy economy,
            ShopListing listing,
            int qty) {
        return executePurchaseInternal(player, plugin, economy, listing, qty, false).success();
    }

    private static PurchaseResult executePurchaseInternal(
            Player player,
            RootMcShopsPlugin plugin,
            ShopEconomy economy,
            ShopListing listing,
            int qty,
            boolean quiet) {
        Material mat = ShopItemKeys.baseMaterial(listing.itemKey());
        if (mat == null || mat.isAir()) {
            if (!quiet) {
                player.sendMessage(plugin.colorize("&cUnknown item: &f" + listing.itemKey()));
            }
            return PurchaseResult.failed();
        }
        if (ShopService.isOwner(listing, player)) {
            if (!quiet) {
                player.sendMessage(plugin.msg("buy-own-shop"));
            }
            return PurchaseResult.failed();
        }

        int stock = plugin.countStock(listing);
        if (stock <= 0) {
            if (!quiet) {
                sendNoShopsSelling(player, plugin, listing.itemKey());
            }
            return PurchaseResult.failed();
        }

        int buyQty = Math.min(qty, stock);
        double total = listing.price() * buyQty;
        if (!canAfford(economy, player, total)) {
            if (!quiet) {
                player.sendMessage(plugin.colorize(
                        plugin.rawMsg("buy-insufficient")
                                .replace("{total}", formatGold(total))
                                .replace("{balance}", formatGold(economy.balance(player)))));
            }
            return PurchaseResult.failed();
        }

        String itemKey = plugin.itemKeyForStock(listing);
        if (ShopItemKeys.BONDED_NOTE.equalsIgnoreCase(itemKey)) {
            return executeBondPurchaseInternal(player, plugin, economy, listing, buyQty, quiet, itemKey);
        }

        ItemStack template = ShopContainers.firstMatchingStack(listing, itemKey);
        if (!plugin.withdrawStock(listing, buyQty)) {
            if (!quiet) {
                player.sendMessage(plugin.colorize(
                        plugin.rawMsg("buy-stock-gone").replace("{item}", listing.itemKey())));
            }
            return PurchaseResult.failed();
        }

        double net = economy.withholdTaxedPayment(player, total, "shop_buy");
        if (net < 0) {
            plugin.depositStock(listing, buyQty);
            if (!quiet) {
                player.sendMessage(plugin.colorize(
                        plugin.rawMsg("buy-insufficient")
                                .replace("{total}", formatGold(total))
                                .replace("{balance}", formatGold(economy.balance(player)))));
            }
            return PurchaseResult.failed();
        }

        plugin.depositToOwner(listing, net);
        ShopSigns.refreshListingSign(plugin, plugin.store(), listing);
        notifyOwnerSale(plugin, listing, player, buyQty, total, net);
        broadcastSale(plugin, listing, player, buyQty, total);

        if (template == null) {
            template = ShopItemKeys.stackForKey(itemKey);
            if (template == null) {
                template = new ItemStack(mat, 1);
            }
        }
        giveItems(player, mat, template, buyQty);

        if (!quiet) {
            player.sendMessage(plugin.msg("buy-success")
                    .replace("{qty}", String.valueOf(buyQty))
                    .replace("{item}", listing.itemKey())
                    .replace("{total}", formatGold(total))
                    .replace("{seller}", listing.ownerName() != null ? listing.ownerName() : "shop"));
        }
        ShopListingSync.onChanged(plugin, listing);
        String seller = listing.ownerName() != null ? listing.ownerName() : "shop";
        return new PurchaseResult(true, buyQty, total, seller);
    }

    private static PurchaseResult executeBondPurchaseInternal(
            Player player,
            RootMcShopsPlugin plugin,
            ShopEconomy economy,
            ShopListing listing,
            int buyQty,
            boolean quiet,
            String itemKey) {
        java.util.List<ItemStack> reserved = new java.util.ArrayList<>(buyQty);
        for (int i = 0; i < buyQty; i++) {
            ItemStack cert = ShopContainers.withdrawOneMatching(listing, itemKey, true);
            if (cert == null) {
                for (ItemStack back : reserved) {
                    ShopContainers.depositOne(listing, back);
                }
                if (!quiet) {
                    player.sendMessage(plugin.colorize(
                            plugin.rawMsg("buy-stock-gone").replace("{item}", listing.itemKey())));
                }
                return PurchaseResult.failed();
            }
            reserved.add(cert);
        }

        double total = listing.price() * buyQty;
        double net = economy.withholdTaxedPayment(player, total, "shop_buy");
        if (net < 0) {
            for (ItemStack back : reserved) {
                ShopContainers.depositOne(listing, back);
            }
            if (!quiet) {
                player.sendMessage(plugin.colorize(
                        plugin.rawMsg("buy-insufficient")
                                .replace("{total}", formatGold(total))
                                .replace("{balance}", formatGold(economy.balance(player)))));
            }
            return PurchaseResult.failed();
        }

        plugin.depositToOwner(listing, net);
        ShopSigns.refreshListingSign(plugin, plugin.store(), listing);
        notifyOwnerSale(plugin, listing, player, buyQty, total, net);
        broadcastSale(plugin, listing, player, buyQty, total);

        RootMcBondTransferService bondTransfer = RootMcBondTransferResolver.resolve(plugin);
        for (ItemStack cert : reserved) {
            SystemGoldPayout.mark(cert);
            var leftover = player.getInventory().addItem(cert);
            if (!leftover.isEmpty()) {
                leftover.values().forEach(item ->
                        player.getWorld().dropItemNaturally(player.getLocation(), item));
            }
            if (bondTransfer != null) {
                bondTransfer.transferCertificates(player, cert);
            }
        }

        if (!quiet) {
            player.sendMessage(plugin.msg("buy-success")
                    .replace("{qty}", String.valueOf(buyQty))
                    .replace("{item}", listing.itemKey())
                    .replace("{total}", formatGold(total))
                    .replace("{seller}", listing.ownerName() != null ? listing.ownerName() : "shop"));
        }
        ShopListingSync.onChanged(plugin, listing);
        String seller = listing.ownerName() != null ? listing.ownerName() : "shop";
        return new PurchaseResult(true, buyQty, total, seller);
    }

    private static void giveItems(Player player, Material mat, ItemStack template, int buyQty) {
        int remaining = buyQty;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, mat.getMaxStackSize());
            ItemStack give = template.clone();
            give.setAmount(stackSize);
            SystemGoldPayout.mark(give);
            var leftover = player.getInventory().addItem(give);
            if (!leftover.isEmpty()) {
                leftover.values().forEach(item ->
                        player.getWorld().dropItemNaturally(player.getLocation(), item));
            }
            remaining -= stackSize;
        }
    }

    static String formatGold(double amount) {
        return String.format(Locale.US, "%.3f", GoldMoney.round(amount));
    }

    private static boolean canAfford(ShopEconomy economy, Player player, double total) {
        if (total <= 0) {
            return false;
        }
        return economy.balance(player) + 0.005 >= total;
    }

    private static void notifyOwnerSale(
            RootMcShopsPlugin plugin,
            ShopListing listing,
            Player buyer,
            int qty,
            double total,
            double net) {
        if (listing.ownerUuid() == null || listing.ownerUuid().isBlank()) {
            return;
        }
        UUID ownerId;
        try {
            ownerId = UUID.fromString(listing.ownerUuid());
        } catch (IllegalArgumentException ex) {
            return;
        }
        if (ownerId.equals(buyer.getUniqueId())) {
            return;
        }
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline()) {
            return;
        }
        double tax = Math.max(0, total - net);
        owner.sendMessage(plugin.msg("sale-notify")
                .replace("{buyer}", buyer.getName())
                .replace("{qty}", String.valueOf(qty))
                .replace("{item}", listing.itemKey())
                .replace("{total}", formatGold(total))
                .replace("{gross}", formatGold(total))
                .replace("{tax}", formatGold(tax))
                .replace("{net}", formatGold(net)));
    }

    private static void broadcastSale(
            RootMcShopsPlugin plugin,
            ShopListing listing,
            Player buyer,
            int qty,
            double total) {
        String seller = listing.ownerName() != null && !listing.ownerName().isBlank()
                ? listing.ownerName()
                : "shop";
        String line = plugin.rawMsg("buy-seller-log")
                .replace("{buyer}", buyer.getName())
                .replace("{seller}", seller)
                .replace("{qty}", String.valueOf(qty))
                .replace("{item}", listing.itemKey())
                .replace("{total}", formatGold(total));
        plugin.getLogger().info(line.replaceAll("(?i)[\u00A7&][0-9a-fk-or]", ""));
    }

    private record PurchaseResult(boolean success, int qty, double total, String seller) {
        static PurchaseResult failed() {
            return new PurchaseResult(false, 0, 0, "");
        }
    }
}
