package com.rootrecord.minecraft.rootadmin.command;

import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import com.rootrecord.minecraft.rootadmin.util.AdminPermissions;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AdminTeleportCommands {

    private AdminTeleportCommands() {}

    public static final class Tpo implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Tpo(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!AdminPermissions.has(player, "teleport")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 1) { player.sendMessage(plugin.colorize("&eUsage: /tpo <player>")); return true; }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { player.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0])); return true; }
            plugin.teleportPlayer(player, () -> target.getLocation(), () ->
                    player.sendMessage(plugin.colorize("&aTeleported to &f" + target.getName())));
            return true;
        }
    }

    public static final class TpoHere implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public TpoHere(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!AdminPermissions.has(player, "teleport")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 1) { player.sendMessage(plugin.colorize("&eUsage: /tpohere <player>")); return true; }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { player.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0])); return true; }
            plugin.teleportPlayer(target, () -> player.getLocation(), () ->
                    player.sendMessage(plugin.colorize("&aTeleported &f" + target.getName() + " &ato you.")));
            return true;
        }
    }

    public static final class TpAll implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public TpAll(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!AdminPermissions.has(player, "tpall")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            int count = 0;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.equals(player)) continue;
                if (plugin.teleportPlayer(online, () -> player.getLocation(), null)) {
                    count++;
                }
            }
            player.sendMessage(plugin.colorize("&aTeleporting &f" + count + " &aplayers to you (&f3s&a, stand still)."));
            return true;
        }
    }
}
