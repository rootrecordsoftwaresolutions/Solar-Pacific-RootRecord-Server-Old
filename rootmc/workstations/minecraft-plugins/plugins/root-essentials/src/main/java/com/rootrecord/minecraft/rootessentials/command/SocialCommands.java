package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import com.rootrecord.minecraft.rootessentials.util.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class SocialCommands {

    private SocialCommands() {}

    public static final class Msg implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Msg(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!Permissions.has(player, "msg")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 2) { player.sendMessage(plugin.colorize("&eUsage: /msg <player> <message>")); return true; }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { player.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0])); return true; }
            if (plugin.playerState().isIgnoring(target.getUniqueId(), player.getUniqueId())) {
                player.sendMessage(plugin.msg("msg-ignored"));
                return true;
            }
            String body = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            String lineTo = plugin.colorize("&8▎ &dPM&8│ &f" + player.getName() + " &7→ &fyou&8 &7»&f " + body);
            String lineFrom = plugin.colorize("&8▎ &dPM&8│ &fyou &7→ &f" + target.getName() + "&8 &7»&f " + body);
            target.sendMessage(lineTo);
            player.sendMessage(lineFrom);
            plugin.playerState().setMessagePartner(player.getUniqueId(), target.getUniqueId());
            plugin.spyMessage(player.getName(), target.getName(), body);
            return true;
        }
    }

    public static final class Reply implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Reply(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!Permissions.has(player, "reply") && !Permissions.has(player, "r")) {
                player.sendMessage(plugin.msg("no-permission")); return true;
            }
            if (args.length < 1) { player.sendMessage(plugin.colorize("&eUsage: /reply <message>")); return true; }
            var partnerId = plugin.playerState().messagePartner(player.getUniqueId());
            if (partnerId == null) { player.sendMessage(plugin.colorize("&eNo one to reply to.")); return true; }
            Player target = Bukkit.getPlayer(partnerId);
            if (target == null) { player.sendMessage(plugin.msg("player-not-found").replace("{player}", "player")); return true; }
            String body = String.join(" ", args);
            return new Msg(plugin).onCommand(sender, command, label, new String[]{target.getName(), body});
        }
    }

    public static final class Mail implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Mail(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!Permissions.has(player, "mail")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            try {
                if (args.length == 0) {
                    var rows = plugin.mail().list(player.getUniqueId());
                    if (rows.isEmpty()) { player.sendMessage(plugin.colorize("&7No mail.")); return true; }
                    player.sendMessage(plugin.colorize("&7Mail (&f" + rows.size() + "&7):"));
                    for (var row : rows) {
                        player.sendMessage(plugin.colorize("&7#" + row.id() + " &ffrom " + row.fromName() + "&7: &f" + row.subject()));
                    }
                    player.sendMessage(plugin.colorize("&7Read: &f/mail read <id>&7, clear: &f/mail clear"));
                    return true;
                }
                if ("clear".equalsIgnoreCase(args[0])) {
                    int n = plugin.mail().clear(player.getUniqueId());
                    player.sendMessage(plugin.colorize("&aCleared &f" + n + "&a messages."));
                    return true;
                }
                if ("read".equalsIgnoreCase(args[0])) {
                    if (args.length < 2) { player.sendMessage(plugin.colorize("&eUsage: /mail read <id>")); return true; }
                    long id = Long.parseLong(args[1]);
                    var row = plugin.mail().read(player.getUniqueId(), id);
                    if (row == null) { player.sendMessage(plugin.colorize("&eMail not found.")); return true; }
                    player.sendMessage(plugin.colorize("&7From &f" + row.fromName() + "&7 — &f" + row.subject()));
                    player.sendMessage(plugin.colorize("&f" + row.body()));
                    return true;
                }
                if ("send".equalsIgnoreCase(args[0])) {
                    if (!Permissions.has(player, "mail.send")) { player.sendMessage(plugin.msg("no-permission")); return true; }
                    if (args.length < 4) { player.sendMessage(plugin.colorize("&eUsage: /mail send <player> <subject> <message>")); return true; }
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        var off = Bukkit.getOfflinePlayer(args[1]);
                        if (!off.hasPlayedBefore()) { player.sendMessage(plugin.msg("player-not-found").replace("{player}", args[1])); return true; }
                        plugin.mail().send(off.getUniqueId(), player.getUniqueId(), player.getName(), args[2], String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)));
                    } else {
                        plugin.mail().send(target.getUniqueId(), player.getUniqueId(), player.getName(), args[2], String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)));
                    }
                    player.sendMessage(plugin.colorize("&aMail sent."));
                    return true;
                }
            } catch (Exception ex) {
                player.sendMessage(plugin.colorize("&cMail failed: &f" + ex.getMessage()));
            }
            player.sendMessage(plugin.colorize("&eUsage: /mail | /mail read <id> | /mail send <player> <subject> <msg> | /mail clear"));
            return true;
        }
    }

    public static final class Me implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Me(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage(plugin.msg("players-only")); return true; }
            if (!Permissions.has(player, "me")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 1) { player.sendMessage(plugin.colorize("&eUsage: /me <action>")); return true; }
            String body = String.join(" ", args);
            String line = plugin.colorize("&8▎ &d•&8│ &7* &f" + player.getDisplayName() + " &7" + body);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!plugin.playerState().isIgnoring(online.getUniqueId(), player.getUniqueId())) {
                    online.sendMessage(line);
                }
            }
            return true;
        }
    }

    public static final class Ignore implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Ignore(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage(plugin.msg("players-only")); return true; }
            if (!Permissions.has(player, "ignore")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length > 0 && "list".equalsIgnoreCase(args[0])) {
                var ids = plugin.playerState().ignored(player.getUniqueId());
                if (ids.isEmpty()) {
                    player.sendMessage(plugin.colorize("&7You are not ignoring anyone."));
                    return true;
                }
                StringBuilder sb = new StringBuilder(plugin.colorize("&7Ignoring: &f"));
                for (UUID id : ids) {
                    var p = Bukkit.getPlayer(id);
                    sb.append(p != null ? p.getName() : id.toString().substring(0, 8)).append("&7, ");
                }
                player.sendMessage(sb.toString().replaceAll(", $", ""));
                return true;
            }
            if (args.length < 1) { player.sendMessage(plugin.colorize("&eUsage: /ignore <player> | /ignore list")); return true; }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { player.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0])); return true; }
            if (target.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage(plugin.colorize("&cYou cannot ignore yourself."));
                return true;
            }
            plugin.playerState().ignore(player.getUniqueId(), target.getUniqueId());
            player.sendMessage(plugin.colorize("&aIgnoring &f" + target.getName() + "&a."));
            return true;
        }
    }

    public static final class Unignore implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Unignore(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage(plugin.msg("players-only")); return true; }
            if (!Permissions.has(player, "ignore")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 1) { player.sendMessage(plugin.colorize("&eUsage: /unignore <player>")); return true; }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { player.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0])); return true; }
            plugin.playerState().unignore(player.getUniqueId(), target.getUniqueId());
            player.sendMessage(plugin.colorize("&aNo longer ignoring &f" + target.getName() + "&a."));
            return true;
        }
    }
}
