package com.rootrecord.minecraft.rootadmin.command;

import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import com.rootrecord.minecraft.rootadmin.util.AdminPermissions;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public final class AdminExtraCommands {

    private AdminExtraCommands() {}

    private static Player resolvePlayer(CommandSender sender, String[] args, int idx) {
        if (args.length > idx) return Bukkit.getPlayerExact(args[idx]);
        return sender instanceof Player p ? p : null;
    }

    public static final class Remove implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Remove(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!AdminPermissions.has(sender, "remove")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 2) { sender.sendMessage(plugin.colorize("&eUsage: /remove <player> <item> [amount]")); return true; }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { sender.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0])); return true; }
            Material mat = Material.matchMaterial(args[1].toUpperCase(Locale.ROOT));
            if (mat == null || mat.isAir()) { sender.sendMessage(plugin.colorize("&eUnknown item: &f" + args[1])); return true; }
            int amount = args.length > 2 ? Integer.parseInt(args[2]) : Integer.MAX_VALUE;
            int removed = 0;
            var inv = target.getInventory();
            for (int i = 0; i < inv.getSize() && removed < amount; i++) {
                ItemStack stack = inv.getItem(i);
                if (stack == null || stack.getType() != mat) continue;
                int take = Math.min(amount - removed, stack.getAmount());
                stack.setAmount(stack.getAmount() - take);
                if (stack.getAmount() <= 0) inv.setItem(i, null);
                removed += take;
            }
            sender.sendMessage(plugin.colorize("&aRemoved &f" + removed + "x " + mat.name() + " &afrom &f" + target.getName()));
            return true;
        }
    }

    public static final class Burn implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Burn(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!AdminPermissions.has(sender, "burn")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            Player target = resolvePlayer(sender, args, 0);
            if (target == null) { sender.sendMessage(plugin.msg("player-not-found").replace("{player}", args.length > 0 ? args[0] : "?")); return true; }
            int ticks = 80;
            if (args.length > 1) {
                try { ticks = Math.max(1, Integer.parseInt(args[args.length - 1])) * 20; }
                catch (NumberFormatException ignored) { }
            }
            target.setFireTicks(ticks);
            sender.sendMessage(plugin.colorize("&aIgnited &f" + target.getName()));
            return true;
        }
    }

    public static final class World implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public World(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage(plugin.msg("players-only")); return true; }
            if (!AdminPermissions.has(player, "world")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 1) { player.sendMessage(plugin.colorize("&eUsage: /world <world>")); return true; }
            org.bukkit.World world = Bukkit.getWorld(args[0]);
            if (world == null) { player.sendMessage(plugin.colorize("&cWorld not found: &f" + args[0])); return true; }
            plugin.teleportPlayer(player, world.getSpawnLocation(), () ->
                    player.sendMessage(plugin.colorize("&aTeleported to world &f" + world.getName())));
            return true;
        }
    }
}
