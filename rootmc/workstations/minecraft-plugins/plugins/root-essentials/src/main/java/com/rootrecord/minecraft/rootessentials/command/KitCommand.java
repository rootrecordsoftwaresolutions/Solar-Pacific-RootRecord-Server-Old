package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import com.rootrecord.minecraft.rootessentials.util.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

public final class KitCommand implements CommandExecutor {

    private final RootEssentialsPlugin plugin;

    public KitCommand(RootEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!Permissions.has(player, "kit")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(plugin.colorize("&eUsage: /kit <name> | /kit list | /kit reset <player> <kit>"));
            return true;
        }
        if ("list".equalsIgnoreCase(args[0])) {
            var names = plugin.kitNames().stream().filter(k -> Permissions.hasKit(player, k)).sorted().toList();
            if (names.isEmpty()) {
                player.sendMessage(plugin.colorize("&7No kits available."));
            } else {
                player.sendMessage(plugin.colorize("&aKits: &f" + String.join(", ", names)));
            }
            return true;
        }
        if ("reset".equalsIgnoreCase(args[0])) {
            if (!Permissions.has(player, "kit.reset")) {
                player.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            if (args.length < 3) {
                player.sendMessage(plugin.colorize("&eUsage: /kit reset <player> <kit>"));
                return true;
            }
            var target = Bukkit.getOfflinePlayer(args[1]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                player.sendMessage(plugin.msg("player-not-found").replace("{player}", args[1]));
                return true;
            }
            String kit = args[2].toLowerCase(Locale.ROOT);
            try {
                boolean ok = plugin.kitClaims().clearClaim(target.getUniqueId(), kit);
                player.sendMessage(plugin.colorize(ok
                        ? "&aReset kit &f" + kit + " &afor &f" + args[1]
                        : "&eNo kit claim found for &f" + args[1] + " &e(" + kit + ")"));
            } catch (Exception ex) {
                player.sendMessage(plugin.colorize("&cKit reset failed: &f" + ex.getMessage()));
            }
            return true;
        }
        String kit = args[0].toLowerCase(Locale.ROOT);
        if (!Permissions.hasKit(player, kit)) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        List<String> items = plugin.kitItems(kit);
        if (items.isEmpty()) {
            player.sendMessage(plugin.colorize("&eUnknown kit: &f" + kit));
            return true;
        }
        try {
            if (plugin.kitOneTime(kit) && plugin.kitClaims().hasClaimed(player.getUniqueId(), kit)) {
                player.sendMessage(plugin.colorize("&eYou already claimed kit &f" + kit + "&e."));
                return true;
            }
            for (String entry : items) {
                String[] parts = entry.trim().split("\\s+");
                if (parts.length < 2) continue;
                Material mat = Material.matchMaterial(parts[0].toUpperCase(Locale.ROOT));
                if (mat == null || mat.isAir()) continue;
                int amount = Integer.parseInt(parts[1]);
                var leftover = player.getInventory().addItem(new ItemStack(mat, amount));
                leftover.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
            }
            if (plugin.kitOneTime(kit)) {
                plugin.kitClaims().markClaimed(player.getUniqueId(), kit);
            }
            player.sendMessage(plugin.colorize("&aKit &f" + kit + "&a received."));
        } catch (Exception ex) {
            player.sendMessage(plugin.colorize("&cKit failed: &f" + ex.getMessage()));
        }
        return true;
    }
}
