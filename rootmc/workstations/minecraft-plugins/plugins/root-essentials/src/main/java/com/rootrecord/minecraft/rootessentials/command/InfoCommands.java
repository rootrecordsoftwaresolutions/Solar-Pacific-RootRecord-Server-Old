package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.common.ChatUi;
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

public final class InfoCommands {

    private InfoCommands() {}

    public static final class Help implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Help(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!Permissions.has(sender, "help")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            sender.sendMessage(plugin.colorize("&7Use &f/cmds &7for the full command list."));
            return true;
        }
    }

    public static final class List implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public List(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!Permissions.has(sender, "list")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            StringBuilder sb = new StringBuilder(plugin.colorize("&7Online (&f" + Bukkit.getOnlinePlayers().size() + "&7): "));
            for (Player p : Bukkit.getOnlinePlayers()) {
                String name = plugin.playerState().isAfk(p.getUniqueId()) ? p.getName() + " (AFK)" : p.getName();
                sb.append("&f").append(name).append("&7, ");
            }
            sender.sendMessage(sb.toString().replaceAll(", $", ""));
            return true;
        }
    }

    public static final class Motd implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Motd(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!Permissions.has(sender, "motd")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            java.util.List<String> lines = plugin.motdLines();
            ChatUi.banner(sender, "Message of the day");
            if (lines == null || lines.isEmpty()) {
                ChatUi.entry(sender, "Motd", "unset · tell staff", "alert");
                ChatUi.tip(sender, "/rules  ·  /cmds  ·  /map");
                return true;
            }
            for (String line : lines) {
                if (line == null || line.isBlank()) {
                    ChatUi.blank(sender);
                    continue;
                }
                sender.sendMessage(plugin.colorize(line));
            }
            ChatUi.links(
                    sender,
                    "Constitution", "https://rootmc.net/wiki/constitution/");
            return true;
        }
    }

    public static final class Rules implements CommandExecutor {
        private static final java.util.List<String> DEFAULT_RULES = java.util.List.of(
                "&cNo cheating. &7No x-ray, dupes, macros, hacked clients, automation, or exploit abuse. Report bugs instead of using them.",
                "&cNo harassment. &7No slurs, threats, sexual content toward players, targeted toxicity, or trying to drive people off the server.",
                "&eRespect land. &7Do not grief claims/towns, bypass protections, lava/water grief, or abuse wilderness edges to damage builds.",
                "&eLand matters. &7Claim or join protected land. Local rules apply as long as they follow server rules.",
                "&6Gold economy. &7Gold (G) is player-earned through mining, trade, shops, loans, bonds, and taxes. Do not fake markets or manipulate bugs.",
                "&6Fair shops. &7Shop prices must stay within the configured market cap. Predatory pricing or bypass tricks can be removed.",
                "&bPvP and conflict. &7PvP is limited by server settings and consent systems. Do not use traps or mechanics to bypass intended protections.",
                "&bStaff tools. &7Do not ask for command cheats like /heal, /feed, /repair, or item spawning. Staff-only means staff-only.",
                "&aGet help. &7Use chat or Discord for questions. Link with &f/link &7for stats, app features, and Discord verification.");

        private final RootEssentialsPlugin plugin;
        public Rules(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!Permissions.has(sender, "rules")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            ChatUi.banner(sender, "RootMC Rules");
            java.util.List<String> lines = plugin.rulesLines();
            if (lines == null || lines.isEmpty()) {
                lines = DEFAULT_RULES;
            }
            for (String line : lines) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                sender.sendMessage(plugin.colorize("&8• " + line));
            }
            ChatUi.links(sender, "Constitution", "https://rootmc.net/wiki/constitution/");
            ChatUi.tip(sender, "/cmds  ·  /discord  ·  /map");
            return true;
        }
    }

    public static final class Ping implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Ping(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!Permissions.has(player, "ping")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            player.sendMessage(plugin.colorize("&7Ping: &f" + player.getPing() + "ms"));
            return true;
        }
    }

    public static final class Compass implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Compass(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!Permissions.has(player, "compass")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            int yaw = (int) ((player.getLocation().getYaw() % 360 + 360) % 360);
            String dir = yaw < 45 || yaw >= 315 ? "N" : yaw < 135 ? "E" : yaw < 225 ? "S" : "W";
            player.sendMessage(plugin.colorize("&7Facing: &f" + dir + " &7(" + yaw + "°)"));
            return true;
        }
    }

    public static final class Depth implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Depth(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!Permissions.has(player, "depth")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            player.sendMessage(plugin.colorize("&7Depth: &f" + player.getLocation().getBlockY()));
            return true;
        }
    }

    public static final class GetPos implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public GetPos(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!Permissions.has(player, "getpos")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            var loc = player.getLocation();
            player.sendMessage(plugin.colorize("&7Pos: &f" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
                    + " &7in &f" + loc.getWorld().getName()));
            return true;
        }
    }

    public static final class Worth implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Worth(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!Permissions.has(player, "worth")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            Material mat = player.getInventory().getItemInMainHand().getType();
            if (mat.isAir()) { player.sendMessage(plugin.msg("sell-empty-hand")); return true; }
            Double each = plugin.itemPrice(mat);
            if (each == null || each <= 0) {
                player.sendMessage(plugin.msg("sell-not-priced").replace("{item}", mat.name()));
                return true;
            }
            String source = plugin.marketBridgeAvailable() ? "market avg" : "worth table";
            player.sendMessage(plugin.colorize("&7Worth (&f" + source + "&7): &f" + mat.name()
                    + " &7= &f" + plugin.money(each) + " " + plugin.currency()));
            return true;
        }
    }

    public static final class Near implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Near(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!Permissions.has(player, "near")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            int radius = 200;
            StringBuilder sb = new StringBuilder(plugin.colorize("&7Nearby: "));
            for (Player other : player.getWorld().getPlayers()) {
                if (other.equals(player) || player.getLocation().distanceSquared(other.getLocation()) > radius * radius) continue;
                sb.append("&f").append(other.getName()).append("&7, ");
            }
            String out = sb.toString().replaceAll(", $", "");
            player.sendMessage(out.endsWith(": ") ? plugin.colorize("&7Nearby: &enone") : out);
            return true;
        }
    }

    public static final class Afk implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Afk(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!Permissions.has(player, "afk")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            boolean now = plugin.playerState().toggleAfk(player.getUniqueId());
            Bukkit.broadcastMessage(plugin.colorize(now ? "&7" + player.getName() + " is now AFK." : "&7" + player.getName() + " is no longer AFK."));
            return true;
        }
    }
}
