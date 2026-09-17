package com.rootrecord.minecraft.rootmcshops;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** /shops <player>, /items [player], /market [player] — browse shop/market listings. */
public final class ShopsCommand implements CommandExecutor, TabCompleter {

    private final RootMcShopsPlugin plugin;
    private final ShopStore store;

    public ShopsCommand(RootMcShopsPlugin plugin, ShopStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        boolean itemsCmd = isBrowseCommand(command, label);
        if (args.length < 1) {
            if (itemsCmd) {
                openPlayerMarketGui(player);
                return true;
            }
            player.sendMessage(plugin.colorize(plugin.rawMsg("shops-usage").replace("{label}", label)));
            return true;
        }
        PlayerShopsMenu.open(plugin, player, args[0]);
        return true;
    }

    private void openPlayerMarketGui(Player player) {
        PlayerShopsMenu.open(plugin, player, player.getName());
    }

    private static boolean isBrowseCommand(Command command, String label) {
        String name = command != null && command.getName() != null ? command.getName() : label;
        if (name == null) {
            return false;
        }
        String key = name.toLowerCase(Locale.ROOT);
        return key.equals("items") || key.equals("market");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        Set<String> names = new LinkedHashSet<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            names.add(online.getName());
        }
        for (ShopListing shop : store.all()) {
            if (shop.isSellShop() && shop.ownerName() != null && !shop.ownerName().isBlank()) {
                names.add(shop.ownerName());
            }
        }
        return names.stream()
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .limit(40)
                .toList();
    }
}
