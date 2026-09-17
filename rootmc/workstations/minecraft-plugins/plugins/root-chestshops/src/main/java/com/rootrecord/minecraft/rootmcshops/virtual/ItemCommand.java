package com.rootrecord.minecraft.rootmcshops.virtual;

import com.rootrecord.minecraft.rootmcshops.RootMcShopsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ItemCommand implements CommandExecutor, TabCompleter {

    private final RootMcShopsPlugin plugin;

    public ItemCommand(RootMcShopsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (plugin.virtualItems() == null || !plugin.virtualItems().enabled()) {
            player.sendMessage(plugin.msg("virtual-disabled"));
            return true;
        }
        if (args.length == 0) {
            VirtualStorageMenus.openStorage(plugin, player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "deposit", "dep" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("all")) {
                    VirtualStorageService.depositAll(plugin, player);
                } else {
                    VirtualStorageService.depositHand(plugin, player);
                }
            }
            case "withdraw", "wd" -> {
                if (args.length < 2) {
                    player.sendMessage(plugin.msg("virtual-withdraw-usage"));
                    return true;
                }
                if (args[1].equalsIgnoreCase("hand")) {
                    player.sendMessage(plugin.msg("virtual-withdraw-usage"));
                    return true;
                }
                int qty = 64;
                if (args.length >= 3) {
                    try {
                        qty = Math.max(1, Integer.parseInt(args[2]));
                    } catch (NumberFormatException ignored) {
                        qty = 64;
                    }
                }
                VirtualStorageService.withdrawMatching(plugin, player, args[1], qty);
            }
            case "listings", "list" -> VirtualStorageMenus.openMyListings(plugin, player);
            case "cancel" -> {
                if (args.length < 2) {
                    player.sendMessage(plugin.msg("virtual-cancel-usage"));
                    return true;
                }
                VirtualTradeService.cancelListing(plugin, player, args[1]);
            }
            case "help" -> sendHelp(player);
            default -> VirtualStorageMenus.openStorage(plugin, player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(plugin.msg("virtual-help-header"));
        for (String line : List.of(
                "virtual-help-open",
                "virtual-help-deposit",
                "virtual-help-withdraw",
                "virtual-help-listings")) {
            player.sendMessage(plugin.msg(line));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("deposit", "withdraw", "listings", "cancel", "help")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("deposit")) {
            if ("hand".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                out.add("hand");
            }
            if ("all".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                out.add("all");
            }
        }
        return out;
    }
}
