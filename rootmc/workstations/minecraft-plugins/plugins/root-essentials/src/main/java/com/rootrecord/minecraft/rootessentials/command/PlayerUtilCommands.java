package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import com.rootrecord.minecraft.rootessentials.util.Permissions;
import com.rootrecord.minecraft.rootessentials.util.TimeParser;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

public final class PlayerUtilCommands {

    private PlayerUtilCommands() {}

    private static Player target(CommandSender sender, String[] args, int idx) {
        if (args.length > idx) return Bukkit.getPlayerExact(args[idx]);
        return sender instanceof Player p ? p : null;
    }

    /** /feed and /heal — same effect (full health + hunger); self-use costs fees.heal / fees.feed. */
    public static final class Feed implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Feed(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            return restoreVitality(plugin, sender, args, "feed");
        }
    }

    public static final class Heal implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Heal(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            return restoreVitality(plugin, sender, args, "heal");
        }
    }

    private static boolean restoreVitality(
            RootEssentialsPlugin plugin, CommandSender sender, String[] args, String channel) {
        Player t = target(sender, args, 0);
        if (t == null) {
            sender.sendMessage(plugin.msg("player-not-found")
                    .replace("{player}", args.length > 0 ? args[0] : "?"));
            return true;
        }
        boolean self = sender instanceof Player p && p.getUniqueId().equals(t.getUniqueId());
        if (!self && !Permissions.has(sender, channel + ".others")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (self && !Permissions.has(sender, channel)) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        double cost = 0;
        if (self) {
            cost = plugin.serviceFee(channel, 15.0);
            if (!chargeVitality(plugin, (Player) sender, cost, channel)) {
                return true;
            }
        }
        t.setHealth(t.getMaxHealth());
        t.setFireTicks(0);
        t.setFoodLevel(20);
        t.setSaturation(20f);
        if (self) {
            String body = cost > 0
                    ? "full · -" + plugin.money(cost) + " " + plugin.currency()
                    : "full";
            ChatUi.entry((Player) sender, channel.equals("feed") ? "Feed" : "Heal", body, "ok");
        } else {
            sender.sendMessage(plugin.colorize("&aRestored &f" + t.getName() + " &7(heal + feed)"));
        }
        return true;
    }

    private static boolean chargeVitality(
            RootEssentialsPlugin plugin, Player player, double cost, String channel) {
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
            player.sendMessage(plugin.colorize("&c" + channel + " charge failed: &f" + ex.getMessage()));
            return false;
        }
    }

    public static final class Repair implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Repair(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!Permissions.has(player, "repair")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            boolean all = args.length > 0 && "all".equalsIgnoreCase(args[0]);
            if (all) {
                return repairAll(player);
            }
            return repairHeld(player);
        }

        private boolean repairHeld(Player player) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType().isAir() || !(item.getItemMeta() instanceof Damageable d)) {
                player.sendMessage(plugin.msg("repair-hold"));
                return true;
            }
            if (d.getDamage() <= 0) {
                player.sendMessage(plugin.msg("repair-already"));
                return true;
            }
            double cost = plugin.serviceFee("repair", 1000.0);
            if (!chargeRepair(player, cost)) {
                return true;
            }
            d.setDamage(0);
            item.setItemMeta((org.bukkit.inventory.meta.ItemMeta) d);
            player.sendMessage(plugin.msg("repair-paid")
                    .replace("{amount}", plugin.money(cost))
                    .replace("{currency}", plugin.currency()));
            return true;
        }

        private boolean repairAll(Player player) {
            double per = plugin.serviceFee("repair-all-per-item", plugin.serviceFee("repair", 1000.0));
            java.util.List<ItemStack> damaged = new java.util.ArrayList<>();
            for (ItemStack item : player.getInventory().getContents()) {
                if (item == null || item.getType().isAir() || !(item.getItemMeta() instanceof Damageable d)) {
                    continue;
                }
                if (d.getDamage() <= 0) {
                    continue;
                }
                damaged.add(item);
            }
            if (damaged.isEmpty()) {
                player.sendMessage(plugin.msg("repair-all-none"));
                return true;
            }
            double cost = per * damaged.size();
            if (!chargeRepair(player, cost)) {
                return true;
            }
            for (ItemStack item : damaged) {
                if (!(item.getItemMeta() instanceof Damageable d)) {
                    continue;
                }
                d.setDamage(0);
                item.setItemMeta((org.bukkit.inventory.meta.ItemMeta) d);
            }
            player.sendMessage(plugin.msg("repair-all-paid")
                    .replace("{count}", String.valueOf(damaged.size()))
                    .replace("{amount}", plugin.money(cost))
                    .replace("{currency}", plugin.currency()));
            return true;
        }

        private boolean chargeRepair(Player player, double cost) {
            if (cost <= 0) {
                return true;
            }
            try {
                double balance = plugin.balance(player.getUniqueId(), player.getName());
                if (balance + 1e-9 < cost) {
                    player.sendMessage(plugin.msg("repair-insufficient")
                            .replace("{amount}", plugin.money(cost))
                            .replace("{balance}", plugin.money(balance))
                            .replace("{currency}", plugin.currency()));
                    return false;
                }
                if (!plugin.withdraw(player.getUniqueId(), player.getName(), cost)) {
                    player.sendMessage(plugin.msg("repair-insufficient")
                            .replace("{amount}", plugin.money(cost))
                            .replace("{balance}", plugin.money(balance))
                            .replace("{currency}", plugin.currency()));
                    return false;
                }
                plugin.sinkServiceFee(player.getUniqueId(), player.getName(), cost, "repair");
                return true;
            } catch (Exception ex) {
                player.sendMessage(plugin.colorize("&cRepair charge failed: &f" + ex.getMessage()));
                return false;
            }
        }
    }

    public static final class Top implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Top(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            if (!Permissions.has(player, "top")) {
                player.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            double cost = plugin.serviceFee("top", 10.0);
            if (cost > 0) {
                try {
                    double balance = plugin.balance(player.getUniqueId(), player.getName());
                    if (balance + 1e-9 < cost) {
                        player.sendMessage(plugin.msg("top-insufficient")
                                .replace("{amount}", plugin.money(cost))
                                .replace("{balance}", plugin.money(balance))
                                .replace("{currency}", plugin.currency()));
                        return true;
                    }
                } catch (Exception ex) {
                    player.sendMessage(plugin.msg("top-failed"));
                    return true;
                }
            }
            Location loc = player.getLocation();
            int y = loc.getWorld().getHighestBlockYAt(loc);
            Location dest = new Location(loc.getWorld(), loc.getX(), y + 1, loc.getZ(), loc.getYaw(), loc.getPitch());
            final double charge = cost;
            plugin.teleportPlayer(player, dest, () -> {
                if (charge > 0) {
                    try {
                        if (!plugin.withdraw(player.getUniqueId(), player.getName(), charge)) {
                            player.sendMessage(plugin.msg("top-insufficient")
                                    .replace("{amount}", plugin.money(charge))
                                    .replace("{balance}", plugin.money(0))
                                    .replace("{currency}", plugin.currency()));
                            return;
                        }
                        plugin.sinkServiceFee(player.getUniqueId(), player.getName(), charge, "top");
                    } catch (Exception ex) {
                        player.sendMessage(plugin.msg("top-failed"));
                        return;
                    }
                }
                String body = charge > 0
                        ? "surface · -" + plugin.money(charge) + " " + plugin.currency()
                        : "surface";
                ChatUi.entry(player, "Top", body, "ok");
            });
            return true;
        }
    }

    public static final class Clear implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Clear(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            Player t = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : (sender instanceof Player p ? p : null);
            if (t == null) { sender.sendMessage(plugin.msg("player-not-found").replace("{player}", args.length > 0 ? args[0] : "?")); return true; }
            boolean self = sender instanceof Player p && p.getUniqueId().equals(t.getUniqueId());
            if (!self && !Permissions.has(sender, "clearinventory.others")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            if (self && !Permissions.has(sender, "clearinventory")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            t.getInventory().clear();
            sender.sendMessage(plugin.colorize("&aCleared inventory for &f" + t.getName()));
            return true;
        }
    }

    public static final class Trash implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Trash(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!Permissions.has(player, "disposal")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            player.openInventory(Bukkit.createInventory(player, 27, plugin.colorize("&8Trash")));
            return true;
        }
    }

    public static final class Suicide implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Suicide(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!Permissions.has(player, "suicide")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            player.setHealth(0);
            return true;
        }
    }

    public static final class Seen implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Seen(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!Permissions.has(sender, "seen")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 1) { sender.sendMessage(plugin.colorize("&eUsage: /seen <player>")); return true; }
            var off = Bukkit.getOfflinePlayer(args[0]);
            if (!off.hasPlayedBefore() && !off.isOnline()) {
                sender.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0]));
                return true;
            }
            if (off.isOnline()) {
                sender.sendMessage(plugin.colorize("&f" + off.getName() + " &ais online now."));
            } else {
                long ago = System.currentTimeMillis() - off.getLastPlayed();
                sender.sendMessage(plugin.colorize(
                        "&f" + off.getName() + " &7last seen &f" + TimeParser.formatAgo(ago) + " &7ago."));
            }
            return true;
        }
    }
}
