package com.rootrecord.minecraft.rootadmin.command;

import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import com.rootrecord.minecraft.rootadmin.util.AdminPermissions;
import com.rootrecord.minecraft.rootessentials.util.TimeParser;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Locale;

public final class ModerationCommands {

    private ModerationCommands() {}

    public static final class Kick implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Kick(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!AdminPermissions.has(sender, "kick")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 1) { sender.sendMessage(plugin.colorize("&eUsage: /kick <player> [reason]")); return true; }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { sender.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0])); return true; }
            String reason = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "Kicked";
            target.kickPlayer(reason);
            sender.sendMessage(plugin.colorize("&aKicked &f" + target.getName()));
            return true;
        }
    }

    public static final class Ban implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Ban(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!AdminPermissions.has(sender, "ban")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 1) { sender.sendMessage(plugin.colorize("&eUsage: /ban <player> [reason]")); return true; }
            return ban(sender, args[0], null, args);
        }
        private boolean ban(CommandSender sender, String name, Instant expires, String[] args) {
            try {
                OfflinePlayer target = Bukkit.getOfflinePlayer(name);
                String reason = args.length > (expires == null ? 1 : 2)
                        ? String.join(" ", java.util.Arrays.copyOfRange(args, expires == null ? 1 : 2, args.length))
                        : "Banned";
                plugin.moderation().ban(target.getUniqueId(), target.getName() == null ? name : target.getName(), reason, sender.getName(), expires);
                Player online = target.getPlayer();
                if (online != null) online.kickPlayer(reason);
                sender.sendMessage(plugin.colorize("&aBanned &f" + name));
            } catch (Exception ex) {
                sender.sendMessage(plugin.colorize("&cBan failed: &f" + ex.getMessage()));
            }
            return true;
        }
    }

    public static final class TempBan implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public TempBan(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!AdminPermissions.has(sender, "tempban")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 2) { sender.sendMessage(plugin.colorize("&eUsage: /tempban <player> <duration> [reason]")); return true; }
            Instant expires = TimeParser.parseExpiry(args[1]);
            if (expires == null) { sender.sendMessage(plugin.colorize("&eInvalid duration. Use 1d, 2h, 30m, etc.")); return true; }
            try {
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                String reason = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "Temporarily banned";
                plugin.moderation().ban(target.getUniqueId(), target.getName() == null ? args[0] : target.getName(), reason, sender.getName(), expires);
                Player online = target.getPlayer();
                if (online != null) online.kickPlayer(reason);
                sender.sendMessage(plugin.colorize("&aTemp-banned &f" + args[0]));
            } catch (Exception ex) {
                sender.sendMessage(plugin.colorize("&cTempban failed: &f" + ex.getMessage()));
            }
            return true;
        }
    }

    public static final class Unban implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Unban(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!AdminPermissions.has(sender, "unban")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 1) { sender.sendMessage(plugin.colorize("&eUsage: /unban <player>")); return true; }
            try {
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                plugin.moderation().unban(target.getUniqueId());
                sender.sendMessage(plugin.colorize("&aUnbanned &f" + args[0]));
            } catch (Exception ex) {
                sender.sendMessage(plugin.colorize("&cUnban failed: &f" + ex.getMessage()));
            }
            return true;
        }
    }

    public static final class Mute implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Mute(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!AdminPermissions.has(sender, "mute")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 1) { sender.sendMessage(plugin.colorize("&eUsage: /mute <player> [reason]")); return true; }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { sender.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0])); return true; }
            String reason = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "Muted";
            try {
                plugin.moderation().mute(target.getUniqueId(), target.getName(), reason, sender.getName(), null);
                sender.sendMessage(plugin.colorize("&aMuted &f" + target.getName()));
            } catch (Exception ex) {
                sender.sendMessage(plugin.colorize("&cMute failed: &f" + ex.getMessage()));
            }
            return true;
        }
    }

    public static final class Unmute implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Unmute(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!AdminPermissions.has(sender, "unmute")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 1) { sender.sendMessage(plugin.colorize("&eUsage: /unmute <player>")); return true; }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            try {
                plugin.moderation().unmute(target.getUniqueId());
                sender.sendMessage(plugin.colorize("&aUnmuted &f" + args[0]));
            } catch (Exception ex) {
                sender.sendMessage(plugin.colorize("&cUnmute failed: &f" + ex.getMessage()));
            }
            return true;
        }
    }
}
