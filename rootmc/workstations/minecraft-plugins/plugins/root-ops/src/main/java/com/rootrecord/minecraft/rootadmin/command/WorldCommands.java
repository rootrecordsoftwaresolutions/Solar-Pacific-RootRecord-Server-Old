package com.rootrecord.minecraft.rootadmin.command;

import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import com.rootrecord.minecraft.rootadmin.util.AdminPermissions;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class WorldCommands {

    private WorldCommands() {}

    private static World world(CommandSender sender) {
        if (sender instanceof Player p) return p.getWorld();
        return sender.getServer().getWorlds().get(0);
    }

    public static final class Time implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Time(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!AdminPermissions.has(sender, "time")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            World w = world(sender);
            if (args.length < 1) {
                sender.sendMessage(plugin.colorize("&eUsage: /timeset <ticks> &7· players use &f/time &7for clocks"));
                return true;
            }
            try {
                long ticks = Long.parseLong(args[0]);
                w.setTime(ticks);
                sender.sendMessage(plugin.colorize("&aTime set to &f" + ticks));
            } catch (NumberFormatException ex) {
                sender.sendMessage(plugin.colorize("&eUsage: /timeset <ticks>"));
            }
            return true;
        }
    }

    public static final class Day implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Day(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!AdminPermissions.has(sender, "time")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            world(sender).setTime(1000L);
            sender.sendMessage(plugin.colorize("&aSet time to day."));
            return true;
        }
    }

    public static final class Night implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Night(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!AdminPermissions.has(sender, "time")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            world(sender).setTime(13000L);
            sender.sendMessage(plugin.colorize("&aSet time to night."));
            return true;
        }
    }

    public static final class Weather implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Weather(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!AdminPermissions.has(sender, "weather")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            World w = world(sender);
            if (args.length < 1) {
                sender.sendMessage(plugin.colorize("&7Storm: &f" + w.hasStorm() + " &7Thunder: &f" + w.isThundering()));
                return true;
            }
            switch (args[0].toLowerCase()) {
                case "sun", "clear" -> { w.setStorm(false); w.setThundering(false); }
                case "rain", "storm" -> { w.setStorm(true); w.setThundering(false); }
                case "thunder" -> { w.setStorm(true); w.setThundering(true); }
                default -> { sender.sendMessage(plugin.colorize("&eUsage: /weather <sun|rain|storm|thunder>")); return true; }
            }
            sender.sendMessage(plugin.colorize("&aWeather updated."));
            return true;
        }
    }
}
