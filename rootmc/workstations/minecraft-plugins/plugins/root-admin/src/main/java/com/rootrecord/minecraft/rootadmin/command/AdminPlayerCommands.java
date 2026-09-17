package com.rootrecord.minecraft.rootadmin.command;

import com.rootrecord.minecraft.common.RootMcPublicReachout;
import com.rootrecord.minecraft.common.ShadedServiceBridge;
import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import com.rootrecord.minecraft.rootadmin.util.AdminPermissions;
import com.rootrecord.minecraft.rootadmin.util.GamemodeBypass;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

public final class AdminPlayerCommands {

    private AdminPlayerCommands() {}

    private static Player target(CommandSender sender, String[] args, int idx) {
        if (args.length > idx) return Bukkit.getPlayerExact(args[idx]);
        return sender instanceof Player p ? p : null;
    }

    public static final class Fly implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Fly(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            Player t = target(sender, args, 0);
            if (t == null) { sender.sendMessage(plugin.msg("player-not-found").replace("{player}", args.length > 0 ? args[0] : "?")); return true; }
            boolean self = sender instanceof Player p && p.getUniqueId().equals(t.getUniqueId());
            if (!self && !AdminPermissions.has(sender, "fly.others")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            if (self && !AdminPermissions.has(sender, "fly")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            boolean fly = !t.getAllowFlight();
            t.setAllowFlight(fly);
            t.setFlying(fly);
            sender.sendMessage(plugin.colorize("&aFly " + (fly ? "enabled" : "disabled") + " for &f" + t.getName()));
            return true;
        }
    }

    public static final class God implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public God(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            Player t = target(sender, args, 0);
            if (t == null) { sender.sendMessage(plugin.msg("player-not-found").replace("{player}", args.length > 0 ? args[0] : "?")); return true; }
            if (!AdminPermissions.has(sender, "god")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            boolean on = plugin.playerState().toggleGod(t.getUniqueId());
            sender.sendMessage(plugin.colorize("&aGod mode " + (on ? "enabled" : "disabled") + " for &f" + t.getName()));
            return true;
        }
    }

    public static final class Gamemode implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Gamemode(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof ConsoleCommandSender)) {
                sender.sendMessage(plugin.colorize("&cGamemode is server-console only. Use survival in-game."));
                return true;
            }
            String modeToken = args.length > 0 ? args[0] : switch (label.toLowerCase()) {
                case "gms" -> "survival";
                case "gmc" -> "creative";
                case "gma" -> "adventure";
                case "gmsp" -> "spectator";
                default -> null;
            };
            if (modeToken == null) { sender.sendMessage(plugin.colorize("&eUsage: /gamemode <mode> [player]")); return true; }
            GameMode mode = parseMode(modeToken);
            if (mode == null) { sender.sendMessage(plugin.colorize("&eInvalid gamemode.")); return true; }
            Player t;
            if (args.length > 1) {
                t = Bukkit.getPlayerExact(args[1]);
            } else {
                sender.sendMessage(plugin.colorize("&eUsage: /gamemode <mode> <player>"));
                return true;
            }
            if (t == null) {
                sender.sendMessage(plugin.msg("player-not-found").replace("{player}", args[1]));
                return true;
            }
            GamemodeBypass.allowNextChange(t.getUniqueId());
            t.setGameMode(mode);
            sender.sendMessage(plugin.colorize("&aSet &f" + t.getName() + " &ato &f" + mode.name().toLowerCase()));
            return true;
        }
        private static GameMode parseMode(String raw) {
            return switch (raw.toLowerCase()) {
                case "0", "s", "survival" -> GameMode.SURVIVAL;
                case "1", "c", "creative" -> GameMode.CREATIVE;
                case "2", "a", "adventure" -> GameMode.ADVENTURE;
                case "3", "sp", "spectator" -> GameMode.SPECTATOR;
                default -> null;
            };
        }
    }

    public static final class Broadcast implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Broadcast(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!AdminPermissions.has(sender, "broadcast")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 1) { sender.sendMessage(plugin.colorize("&eUsage: /broadcast <message>")); return true; }
            String msg = String.join(" ", args);
            String line = plugin.colorize("&8▎ &6Broadcast&8│ &f" + msg);
            Bukkit.broadcastMessage(line);
            RootMcPublicReachout reachout = ShadedServiceBridge.resolvePublicReachout(plugin);
            if (reachout != null) {
                reachout.relayGlobalBroadcast(line, "broadcast");
            }
            return true;
        }
    }
}
