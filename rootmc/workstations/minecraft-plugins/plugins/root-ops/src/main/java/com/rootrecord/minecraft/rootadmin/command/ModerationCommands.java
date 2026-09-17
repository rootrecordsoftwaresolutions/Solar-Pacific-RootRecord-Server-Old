package com.rootrecord.minecraft.rootadmin.command;

import com.rootrecord.minecraft.rootadmin.moderation.BanSeizeService;
import com.rootrecord.minecraft.rootadmin.util.AdminPermissions;
import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import com.rootrecord.minecraft.rootessentials.data.ModerationStore;
import com.rootrecord.minecraft.rootessentials.util.TimeParser;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Date;

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
            return applyBan(plugin, sender, args[0], null, args, 1);
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
            return applyBan(plugin, sender, args[0], expires, args, 2);
        }
    }

    private static boolean applyBan(
            RootEssentialsPlugin plugin,
            CommandSender sender,
            String name,
            Instant expires,
            String[] args,
            int reasonStart) {
        try {
            OfflinePlayer target = Bukkit.getOfflinePlayer(name);
            String reason = args.length > reasonStart
                    ? String.join(" ", java.util.Arrays.copyOfRange(args, reasonStart, args.length))
                    : (expires == null ? "Banned" : "Temporarily banned");
            String displayName = target.getName() == null ? name : target.getName();

            if (isProtectedAdmin(target)) {
                sender.sendMessage(plugin.colorize(
                        "&cCannot ban &f" + displayName + "&c: admins cannot ban admins."));
                return true;
            }

            String ip = resolveBanIp(plugin, target);
            if (ip == null || ip.isBlank()) {
                sender.sendMessage(plugin.colorize(
                        "&cCannot ban &f" + displayName
                                + "&c: no IP on file. Player must have joined once, or ban while online."));
                return true;
            }

            BanSeizeService.Report seize = BanSeizeService.seizeAll(plugin, target);

            plugin.moderation().ban(target.getUniqueId(), displayName, reason, sender.getName(), expires, ip);
            applyPaperIpBan(ip, reason, sender.getName(), expires);

            Player online = target.getPlayer();
            if (online != null) {
                online.kickPlayer(reason);
            }

            String ipNote = "&7IP &f" + ip;
            String label = expires == null ? "Banned" : "Temp-banned";
            sender.sendMessage(plugin.colorize("&a" + label + " &f" + displayName + " &7" + ipNote));
            sender.sendMessage(plugin.colorize(
                    "&7Confiscated to reserve: &f" + String.format(java.util.Locale.US, "%.3f", seize.totalReserveG())
                            + " G &8(" + seize.summary() + ")"));
        } catch (Exception ex) {
            sender.sendMessage(plugin.colorize("&cBan failed: &f" + ex.getMessage()));
        }
        return true;
    }

    /** Admins cannot ban admins (op, ban-capable staff, or common admin group nodes). */
    private static boolean isProtectedAdmin(OfflinePlayer target) {
        if (target != null && target.isOp()) {
            return true;
        }
        Player online = target != null ? target.getPlayer() : null;
        if (online == null) {
            return false;
        }
        return online.isOp()
                || AdminPermissions.has(online, "ban")
                || online.hasPermission("group.admin")
                || online.hasPermission("group.owner")
                || online.hasPermission("rootadmin.*");
    }

    private static String resolveBanIp(RootEssentialsPlugin plugin, OfflinePlayer target) {
        Player online = target.getPlayer();
        if (online != null && online.getAddress() != null && online.getAddress().getAddress() != null) {
            return ModerationStore.normalizeIp(online.getAddress().getAddress().getHostAddress());
        }
        try {
            return plugin.moderation().lastIp(target.getUniqueId()).orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private static void applyPaperIpBan(String ip, String reason, String source, Instant expires) {
        if (ip == null || ip.isBlank()) {
            return;
        }
        try {
            Date expiry = expires == null ? null : Date.from(expires);
            Bukkit.getBanList(BanList.Type.IP).addBan(ip, reason, expiry, source);
        } catch (Exception ignored) {
            // Paper/Spigot ban list optional
        }
    }

    @SuppressWarnings("deprecation")
    private static void removePaperIpBan(String ip) {
        if (ip == null || ip.isBlank()) {
            return;
        }
        try {
            Bukkit.getBanList(BanList.Type.IP).pardon(ip);
        } catch (Exception ignored) {
            // optional
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
                String ip = null;
                try {
                    var ban = plugin.moderation().activeBan(target.getUniqueId());
                    if (ban.isPresent()) {
                        ip = ban.get().bannedIp();
                    }
                    if (ip == null || ip.isBlank()) {
                        ip = plugin.moderation().lastIp(target.getUniqueId()).orElse(null);
                    }
                } catch (Exception ignored) {
                    // best-effort
                }
                plugin.moderation().unban(target.getUniqueId());
                removePaperIpBan(ip);
                sender.sendMessage(plugin.colorize("&aUnbanned &f" + args[0]
                        + (ip == null ? "" : " &7(+ IP " + ip + ")")));
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
