package com.rootrecord.minecraft.rootmcshops;

import com.rootrecord.minecraft.common.ChatLinks;
import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.common.RootMcTreasuryResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class SellCommand implements CommandExecutor, TabCompleter {

    private final RootMcShopsPlugin plugin;
    private final ShopStore store;
    private final ShopEconomy economy;
    private final Map<UUID, PendingBulkSale> pendingBulkSales = new ConcurrentHashMap<>();

    public SellCommand(RootMcShopsPlugin plugin, ShopStore store, ShopEconomy economy) {
        this.plugin = plugin;
        this.store = store;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length >= 1 && "cancel".equalsIgnoreCase(args[0])) {
            pendingBulkSales.remove(player.getUniqueId());
            player.sendMessage(plugin.msg("sell-cancelled"));
            return true;
        }

        if (args.length >= 2 && "confirm".equalsIgnoreCase(args[0])) {
            return handleConfirm(player, args);
        }
        if (args.length >= 1 && "confirmall".equalsIgnoreCase(args[0])) {
            return handleConfirmAll(player);
        }

        if (args.length < 1) {
            player.sendMessage(plugin.colorize("&eUsage: /sell <item|all> [amount]"));
            player.sendMessage(plugin.colorize(plugin.rawMsg("sell-hint")));
            return true;
        }
        if ("all".equalsIgnoreCase(args[0])) {
            return quoteSellAll(player);
        }

        String itemKey = ShopItemKeys.normalizeQuery(args[0]);
        Material mat = ShopItemKeys.baseMaterial(itemKey);
        if (mat == null || mat.isAir()) {
            player.sendMessage(plugin.colorize("&cUnknown item: &f" + itemKey));
            return true;
        }
        if (ShopItemKeys.isForbiddenGoldResource(mat)) {
            player.sendMessage(plugin.msg("forbidden-item"));
            return true;
        }

        int qty = 1;
        if (args.length >= 2) {
            try {
                qty = Math.max(1, Math.min(2304, Integer.parseInt(args[1])));
            } catch (NumberFormatException ex) {
                player.sendMessage(plugin.colorize("&eUsage: /sell <item|all> [amount]"));
                return true;
            }
        }

        if (ShopSellService.bestBuyShop(plugin, player, itemKey).isEmpty()) {
            ShopSellService.sendNoBuyShops(player, plugin, itemKey);
            return true;
        }

        ShopSellService.sendBestQuote(player, plugin, economy, itemKey, qty);
        return true;
    }

    private boolean handleConfirm(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.colorize("&eUsage: /sell confirm <shop-id> <amount>"));
            return true;
        }
        int qty;
        try {
            qty = Math.max(1, Math.min(2304, Integer.parseInt(args[args.length - 1])));
        } catch (NumberFormatException ex) {
            player.sendMessage(plugin.colorize("&eInvalid amount."));
            return true;
        }
        String target = String.join(":", java.util.Arrays.copyOfRange(args, 1, args.length - 1));

        ShopListing listing = store.getById(target);
        if (listing != null) {
            return ShopSellService.executeSale(player, plugin, economy, listing, qty);
        }

        String itemKey = ShopItemKeys.normalizeQuery(target);
        if (ShopItemKeys.baseMaterial(itemKey) == null) {
            ShopSellService.sendNoBuyShops(player, plugin, itemKey);
            return true;
        }
        if (ShopItemKeys.isForbiddenGoldResourceKey(itemKey)) {
            player.sendMessage(plugin.msg("forbidden-item"));
            return true;
        }
        return ShopSellService.executeSplitSale(player, plugin, economy, itemKey, qty);
    }

    private boolean quoteSellAll(Player player) {
        Map<String, Integer> inventoryByItem = new HashMap<>();
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                continue;
            }
            if (ShopItemKeys.isForbiddenGoldResource(stack)) {
                continue;
            }
            String itemKey = stack.getType().name();
            inventoryByItem.merge(itemKey, stack.getAmount(), Integer::sum);
        }
        if (inventoryByItem.isEmpty()) {
            player.sendMessage(plugin.colorize(plugin.rawMsg("sell-no-items").replace("{item}", "items")));
            return true;
        }

        List<BulkSaleLine> lines = inventoryByItem.entrySet().stream()
                .map(entry -> buildBulkLine(player, entry.getKey(), entry.getValue()))
                .filter(line -> line != null && line.qty() > 0)
                .sorted(Comparator.comparingDouble(BulkSaleLine::total).reversed())
                .collect(Collectors.toList());
        if (lines.isEmpty()) {
            player.sendMessage(plugin.colorize("&eNo sellable items found in buy shops right now."));
            return true;
        }

        pendingBulkSales.put(player.getUniqueId(), new PendingBulkSale(lines));
        player.sendMessage(plugin.colorize(plugin.rawMsg("sell-all-quote-header")));
        double grossTotal = 0;
        double taxTotal = 0;
        double netTotal = 0;
        for (BulkSaleLine line : lines) {
            TaxSplit split = estimateTax(line.total());
            grossTotal += split.gross();
            taxTotal += split.tax();
            netTotal += split.net();
            player.sendMessage(plugin.colorize(plugin.rawMsg("sell-all-quote-line")
                    .replace("{qty}", String.valueOf(line.qty()))
                    .replace("{item}", line.itemKey())
                    .replace("{buyer}", line.buyer())
                    .replace("{each}", ShopBuyService.formatGold(line.each()))
                    .replace("{gross}", ShopBuyService.formatGold(split.gross()))
                    .replace("{tax}", ShopBuyService.formatGold(split.tax()))
                    .replace("{net}", ShopBuyService.formatGold(split.net()))));
        }
        player.sendMessage(plugin.colorize(plugin.rawMsg("sell-all-quote-total")
                .replace("{gross}", ShopBuyService.formatGold(grossTotal))
                .replace("{tax}", ShopBuyService.formatGold(taxTotal))
                .replace("{net}", ShopBuyService.formatGold(netTotal))));
        player.sendMessage(ChatLinks.confirmCancel("/sell confirmall", "/sell cancel"));
        return true;
    }

    private BulkSaleLine buildBulkLine(Player player, String itemKey, int playerQty) {
        if (playerQty <= 0) {
            return null;
        }
        var best = ShopSellService.bestBuyShop(plugin, player, itemKey);
        if (best.isEmpty()) {
            return null;
        }
        ShopListing listing = best.get();
        int capacity = plugin.countStock(listing);
        int qty = Math.min(playerQty, Math.max(0, capacity));
        if (qty <= 0) {
            return null;
        }
        String buyer = listing.ownerName() == null || listing.ownerName().isBlank() ? "shop" : listing.ownerName();
        return new BulkSaleLine(listing.id(), itemKey, qty, listing.price(), listing.price() * qty, buyer);
    }

    private boolean handleConfirmAll(Player player) {
        PendingBulkSale pending = pendingBulkSales.remove(player.getUniqueId());
        if (pending == null || pending.lines().isEmpty()) {
            player.sendMessage(plugin.colorize("&eNo pending /sell all quote. Run &f/sell all&e first."));
            return true;
        }
        int soldLines = 0;
        int skippedLines = 0;
        double gross = 0;
        double tax = 0;
        double net = 0;
        for (BulkSaleLine line : pending.lines()) {
            ShopListing listing = store.getById(line.shopId());
            if (listing == null || !listing.isBuyShop()) {
                skippedLines++;
                continue;
            }
            ShopSellService.SaleResult result = ShopSellService.executeSale(
                    player, plugin, economy, listing, line.qty(), true);
            if (!result.success()) {
                skippedLines++;
                continue;
            }
            soldLines++;
            gross += result.gross();
            tax += result.tax();
            net += result.net();
            player.sendMessage(plugin.colorize(plugin.rawMsg("sell-all-result-line")
                    .replace("{qty}", String.valueOf(result.qty()))
                    .replace("{item}", result.itemKey())
                    .replace("{buyer}", result.buyer())
                    .replace("{gross}", ShopBuyService.formatGold(result.gross()))
                    .replace("{tax}", ShopBuyService.formatGold(result.tax()))
                    .replace("{net}", ShopBuyService.formatGold(result.net()))));
        }
        if (soldLines == 0) {
            player.sendMessage(plugin.colorize(plugin.rawMsg("sell-all-none")));
            return true;
        }
        player.sendMessage(plugin.colorize(plugin.rawMsg("sell-all-complete")
                .replace("{lines}", String.valueOf(soldLines))
                .replace("{skipped}", String.valueOf(skippedLines))
                .replace("{gross}", ShopBuyService.formatGold(gross))
                .replace("{tax}", ShopBuyService.formatGold(tax))
                .replace("{net}", ShopBuyService.formatGold(net))));
        return true;
    }

    private static TaxSplit estimateTax(double gross) {
        if (gross <= 0) {
            return new TaxSplit(0, 0, 0);
        }
        RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(
                (org.bukkit.plugin.java.JavaPlugin) org.bukkit.Bukkit.getPluginManager().getPlugin("Root-Essentials"));
        if (treasury != null && treasury.transactionTaxEnabled()) {
            double tax = Math.min(gross, GoldMoney.round(gross * treasury.transactionTaxRate()));
            return new TaxSplit(gross, tax, Math.max(0, gross - tax));
        }
        return new TaxSplit(gross, 0, gross);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toUpperCase(Locale.ROOT);
            if ("confirm".startsWith(prefix.toLowerCase(Locale.ROOT))) {
                return List.of("confirm");
            }
            if ("confirmall".startsWith(prefix.toLowerCase(Locale.ROOT))) {
                return List.of("confirmall");
            }
            if ("cancel".startsWith(prefix.toLowerCase(Locale.ROOT))) {
                return List.of("cancel");
            }
            if ("all".startsWith(prefix.toLowerCase(Locale.ROOT))) {
                return List.of("all");
            }
            return store.all().stream()
                    .filter(ShopListing::isBuyShop)
                    .map(ShopListing::itemKey)
                    .distinct()
                    .filter(k -> k.startsWith(prefix))
                    .limit(20)
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && !"confirm".equalsIgnoreCase(args[0])) {
            return Arrays.asList("1", "8", "16", "32", "64");
        }
        return List.of();
    }

    private record BulkSaleLine(String shopId, String itemKey, int qty, double each, double total, String buyer) {}

    private record PendingBulkSale(List<BulkSaleLine> lines) {}

    private record TaxSplit(double gross, double tax, double net) {}
}
