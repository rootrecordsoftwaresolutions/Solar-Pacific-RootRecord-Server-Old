package com.rootrecord.minecraft.rootmcshops;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public final class BuyCommand implements CommandExecutor, TabCompleter {

    private final RootMcShopsPlugin plugin;
    private final ShopStore store;
    private final ShopEconomy economy;

    public BuyCommand(RootMcShopsPlugin plugin, ShopStore store, ShopEconomy economy) {
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
            player.sendMessage(plugin.msg("buy-cancelled"));
            return true;
        }

        if (args.length >= 2 && "confirm".equalsIgnoreCase(args[0])) {
            return handleConfirm(player, args);
        }

        if (args.length < 1) {
            player.sendMessage(plugin.colorize("&eUsage: /buy <item> [amount]"));
            player.sendMessage(plugin.colorize(plugin.rawMsg("buy-hint")));
            return true;
        }

        String itemKey = ShopItemKeys.normalizeQuery(args[0]);
        Material mat = ShopItemKeys.baseMaterial(itemKey);
        if (mat == null || mat.isAir()) {
            player.sendMessage(plugin.colorize("&cUnknown item: &f" + itemKey));
            return true;
        }

        int qty = 1;
        if (args.length >= 2) {
            try {
                qty = Math.max(1, Math.min(2304, Integer.parseInt(args[1])));
            } catch (NumberFormatException ex) {
                player.sendMessage(plugin.colorize("&eUsage: /buy <item> [amount]"));
                return true;
            }
        }

        if (ShopBuyService.cheapestInStock(plugin, player, itemKey).isEmpty()) {
            ShopBuyService.sendNoShopsSelling(player, plugin, itemKey);
            return true;
        }

        ShopBuyService.sendCheapestQuote(player, plugin, economy, itemKey, qty);
        return true;
    }

    private boolean handleConfirm(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.colorize("&eUsage: /buy confirm <shop-id> <amount>"));
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
            return ShopBuyService.executePurchase(player, plugin, economy, listing, qty);
        }

        String itemKey = ShopItemKeys.normalizeQuery(target);
        if (ShopItemKeys.baseMaterial(itemKey) == null) {
            ShopBuyService.sendNoShopsSelling(player, plugin, itemKey);
            return true;
        }
        return ShopBuyService.executeSplitPurchase(player, plugin, economy, itemKey, qty);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toUpperCase(Locale.ROOT);
            if ("confirm".startsWith(prefix.toLowerCase(Locale.ROOT))) {
                return List.of("confirm");
            }
            if ("cancel".startsWith(prefix.toLowerCase(Locale.ROOT))) {
                return List.of("cancel");
            }
            return store.all().stream()
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
}
