package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import com.rootrecord.minecraft.rootessentials.util.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class CosmeticCommands {

    private CosmeticCommands() {}

    public static final class Nick implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Nick(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!Permissions.has(player, "nick")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 1) {
                player.setDisplayName(player.getName());
                player.setPlayerListName(player.getName());
                ChatUi.entry(player, "Nick", "cleared", "ok");
                return true;
            }
            double cost = plugin.serviceFee("nick", 100.0);
            if (!charge(plugin, player, cost, "nick")) {
                return true;
            }
            String nick = String.join(" ", args);
            if (Permissions.has(player, "nick.color") || Permissions.has(player, "chat.color")) {
                nick = plugin.colorize(nick.replace('&', '\u00A7'));
            }
            player.setDisplayName(nick);
            player.setPlayerListName(nick.length() > 16 ? nick.substring(0, 16) : nick);
            String body = cost > 0
                    ? "set · -" + plugin.money(cost) + " " + plugin.currency()
                    : "set";
            ChatUi.entry(player, "Nick", body, "ok");
            return true;
        }
    }

    public static final class Hat implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Hat(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!Permissions.has(player, "hat")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand == null || hand.getType().isAir()) {
                player.sendMessage(plugin.msg("sell-empty-hand"));
                return true;
            }
            double cost = plugin.serviceFee("hat", 1.0);
            if (!charge(plugin, player, cost, "hat")) {
                return true;
            }
            ItemStack helmet = player.getInventory().getHelmet();
            player.getInventory().setHelmet(hand.clone());
            player.getInventory().setItemInMainHand(helmet);
            String body = cost > 0
                    ? "equipped · -" + plugin.money(cost) + " " + plugin.currency()
                    : "equipped";
            ChatUi.entry(player, "Hat", body, "ok");
            return true;
        }
    }

    private static boolean charge(RootEssentialsPlugin plugin, Player player, double cost, String channel) {
        if (cost <= 0) {
            return true;
        }
        try {
            double balance = plugin.balance(player.getUniqueId(), player.getName());
            if (balance + 1e-9 < cost) {
                player.sendMessage(plugin.msg(channel + "-insufficient")
                        .replace("{amount}", plugin.money(cost))
                        .replace("{balance}", plugin.money(balance))
                        .replace("{currency}", plugin.currency()));
                return false;
            }
            if (!plugin.withdraw(player.getUniqueId(), player.getName(), cost)) {
                player.sendMessage(plugin.msg(channel + "-insufficient")
                        .replace("{amount}", plugin.money(cost))
                        .replace("{balance}", plugin.money(balance))
                        .replace("{currency}", plugin.currency()));
                return false;
            }
            plugin.sinkServiceFee(player.getUniqueId(), player.getName(), cost, channel);
            return true;
        } catch (Exception ex) {
            String label = channel.isEmpty() ? "Charge" : Character.toUpperCase(channel.charAt(0)) + channel.substring(1);
            player.sendMessage(plugin.colorize("&c" + label + " charge failed: &f" + ex.getMessage()));
            return false;
        }
    }
}
