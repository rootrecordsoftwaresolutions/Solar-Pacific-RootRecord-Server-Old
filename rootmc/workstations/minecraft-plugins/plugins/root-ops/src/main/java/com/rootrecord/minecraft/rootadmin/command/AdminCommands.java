package com.rootrecord.minecraft.rootadmin.command;

import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import com.rootrecord.minecraft.rootadmin.util.AdminPermissions;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Locale;

public final class AdminCommands {

    private AdminCommands() {}

    public static final class TpOffline implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public TpOffline(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!AdminPermissions.has(player, "tpoffline")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 1) { player.sendMessage(plugin.colorize("&eUsage: /tpoffline <player>")); return true; }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (!target.isOnline()) { player.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0])); return true; }
            Player online = target.getPlayer();
            if (online == null) { player.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0])); return true; }
            plugin.teleportPlayer(player, () -> online.getLocation(), () ->
                    player.sendMessage(plugin.colorize("&aTeleported to &f" + online.getName())));
            return true;
        }
    }

    public static final class Vanish implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Vanish(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!AdminPermissions.has(player, "vanish")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            boolean on = plugin.playerState().toggleVanish(player.getUniqueId());
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.equals(player)) continue;
                if (on) online.hidePlayer(plugin, player);
                else online.showPlayer(plugin, player);
            }
            player.sendMessage(plugin.colorize(on ? "&aVanish enabled." : "&7Vanish disabled."));
            return true;
        }
    }

    public static final class SocialSpy implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public SocialSpy(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!AdminPermissions.has(player, "socialspy")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            boolean on = plugin.playerState().toggleSocialSpy(player.getUniqueId());
            player.sendMessage(plugin.colorize(on ? "&aSocial spy enabled." : "&7Social spy disabled."));
            return true;
        }
    }

    public static final class Invsee implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Invsee(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!AdminPermissions.has(player, "invsee")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 1) { player.sendMessage(plugin.colorize("&eUsage: /invsee <player>")); return true; }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { player.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0])); return true; }
            player.openInventory(target.getInventory());
            return true;
        }
    }

    public static final class Echest implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Echest(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            com.rootrecord.minecraft.common.RootMcEnderChestService ec =
                    com.rootrecord.minecraft.common.RootMcEnderChestResolver.resolve(plugin);
            if (args.length >= 1) {
                if (!AdminPermissions.has(player, "enderchest.others")
                        && !AdminPermissions.has(player, "enderchest.open")) {
                    player.sendMessage(plugin.msg("no-permission"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    player.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0]));
                    return true;
                }
                if (ec != null) {
                    ec.open(player, target);
                } else {
                    player.openInventory(target.getEnderChest());
                }
                return true;
            }
            if (ec != null) {
                ec.open(player, player);
            } else {
                player.openInventory(player.getEnderChest());
            }
            return true;
        }
    }

    public static final class SetSpawn implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public SetSpawn(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!AdminPermissions.has(sender, "setspawn")) {
                player.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            try {
                plugin.spawnStore().set(player.getLocation());
                player.sendMessage(plugin.colorize("&aSpawn set."));
            } catch (Exception ex) {
                player.sendMessage(plugin.colorize("&cCould not set spawn: &f" + ex.getMessage()));
            }
            return true;
        }
    }

    public static final class TpPos implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public TpPos(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!AdminPermissions.has(player, "tppos")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 3) {
                player.sendMessage(plugin.colorize("&eUsage: /tppos <x> <y> <z> [yaw] [pitch] [world]"));
                return true;
            }
            try {
                double x = Double.parseDouble(args[0]);
                double y = Double.parseDouble(args[1]);
                double z = Double.parseDouble(args[2]);
                float yaw = args.length > 3 ? Float.parseFloat(args[3]) : player.getLocation().getYaw();
                float pitch = args.length > 4 ? Float.parseFloat(args[4]) : player.getLocation().getPitch();
                World world = player.getWorld();
                if (args.length > 5) {
                    world = Bukkit.getWorld(args[5]);
                    if (world == null) {
                        player.sendMessage(plugin.colorize("&cWorld not found: &f" + args[5]));
                        return true;
                    }
                }
                Location dest = new Location(world, x, y, z, yaw, pitch);
                plugin.teleportPlayer(player, dest, () -> player.sendMessage(plugin.colorize("&aTeleported.")));
                return true;
            } catch (NumberFormatException ex) {
                player.sendMessage(plugin.msg("invalid-number"));
                return true;
            }
        }
    }

    public static final class Sudo implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Sudo(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!AdminPermissions.has(sender, "sudo")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 2) {
                sender.sendMessage(plugin.colorize("&eUsage: /sudo <player> <command>"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { sender.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0])); return true; }
            String cmd = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            Bukkit.dispatchCommand(target, cmd);
            sender.sendMessage(plugin.colorize("&aRan as &f" + target.getName() + "&a: &f/" + cmd));
            return true;
        }
    }

    public static final class Give implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Give(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!AdminPermissions.has(sender, "give") && !AdminPermissions.has(sender, "item")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 2) {
                sender.sendMessage(plugin.colorize("&eUsage: /give <player> <item> [amount]"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { sender.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0])); return true; }
            Material mat = Material.matchMaterial(args[1].toUpperCase(Locale.ROOT));
            if (mat == null || mat.isAir()) {
                sender.sendMessage(plugin.colorize("&eUnknown item: &f" + args[1]));
                return true;
            }
            int amount = 1;
            if (args.length > 2) {
                try { amount = Integer.parseInt(args[2]); }
                catch (NumberFormatException ex) { sender.sendMessage(plugin.msg("invalid-number")); return true; }
            }
            amount = Math.max(1, Math.min(2304, amount));
            var leftover = target.getInventory().addItem(new ItemStack(mat, amount));
            leftover.values().forEach(i -> target.getWorld().dropItemNaturally(target.getLocation(), i));
            sender.sendMessage(plugin.colorize("&aGave &f" + amount + "x " + mat.name() + " &ato &f" + target.getName()));
            return true;
        }
    }

    public static final class Speed implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Speed(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!AdminPermissions.has(player, "speed")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            String type = "fly";
            int argIdx = 0;
            if (args.length > 0 && ("walk".equalsIgnoreCase(args[0]) || "fly".equalsIgnoreCase(args[0]))) {
                type = args[0].toLowerCase(Locale.ROOT);
                argIdx = 1;
            }
            Player target = player;
            if (args.length > argIdx + 1) {
                if (!AdminPermissions.has(player, "speed.others")) {
                    player.sendMessage(plugin.msg("no-permission"));
                    return true;
                }
                target = Bukkit.getPlayerExact(args[argIdx + 1]);
                if (target == null) {
                    player.sendMessage(plugin.msg("player-not-found").replace("{player}", args[argIdx + 1]));
                    return true;
                }
            }
            if (args.length <= argIdx) {
                player.sendMessage(plugin.colorize("&eUsage: /speed [walk|fly] <0-10> [player]"));
                return true;
            }
            float speed;
            try {
                speed = Float.parseFloat(args[argIdx]) / 10f;
            } catch (NumberFormatException ex) {
                player.sendMessage(plugin.msg("invalid-number"));
                return true;
            }
            speed = Math.max(0f, Math.min(1f, speed));
            if ("walk".equals(type)) target.setWalkSpeed(speed);
            else target.setFlySpeed(speed);
            sender.sendMessage(plugin.colorize("&aSet &f" + type + " &aspeed for &f" + target.getName()));
            return true;
        }
    }

    public static final class Realname implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Realname(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!AdminPermissions.has(sender, "realname")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 1) {
                sender.sendMessage(plugin.colorize("&eUsage: /realname <nickname>"));
                return true;
            }
            String query = args[0].toLowerCase(Locale.ROOT);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().equalsIgnoreCase(query)
                        || online.getDisplayName().equalsIgnoreCase(query)
                        || online.getDisplayName().replaceAll("\u00A7.", "").equalsIgnoreCase(query)) {
                    sender.sendMessage(plugin.colorize("&f" + online.getDisplayName() + " &7is &f" + online.getName()));
                    return true;
                }
            }
            sender.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0]));
            return true;
        }
    }

    public static final class Whois implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Whois(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!AdminPermissions.has(sender, "whois")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            if (args.length < 1) {
                sender.sendMessage(plugin.colorize("&eUsage: /whois <player>"));
                return true;
            }
            OfflinePlayer target = Bukkit.getPlayerExact(args[0]);
            if (target == null) target = Bukkit.getOfflinePlayer(args[0]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0]));
                return true;
            }
            String name = target.getName() != null ? target.getName() : args[0];
            sender.sendMessage(plugin.colorize("&aWhois: &f" + name));
            sender.sendMessage(plugin.colorize("&7UUID: &f" + target.getUniqueId()));
            sender.sendMessage(plugin.colorize("&7Online: &f" + (target.isOnline() ? "yes" : "no")));
            if (target.isOnline() && target.getPlayer() != null) {
                Player p = target.getPlayer();
                sender.sendMessage(plugin.colorize("&7Display: &f" + p.getDisplayName()));
                if (AdminPermissions.has(sender, "whois.ip")) {
                    var addr = p.getAddress();
                    if (addr != null) sender.sendMessage(plugin.colorize("&7IP: &f" + addr.getAddress().getHostAddress()));
                }
                sender.sendMessage(plugin.colorize("&7AFK: &f" + plugin.playerState().isAfk(p.getUniqueId())));
                sender.sendMessage(plugin.colorize("&7Vanish: &f" + plugin.playerState().isVanished(p.getUniqueId())));
            }
            try {
                double bal = plugin.balance(target.getUniqueId(), name);
                sender.sendMessage(plugin.colorize("&7Balance: &f" + plugin.money(bal) + " " + plugin.currency()));
                int homes = plugin.homeCount(target.getUniqueId());
                sender.sendMessage(plugin.colorize("&7Homes: &f" + homes));
            } catch (Exception ex) {
                sender.sendMessage(plugin.colorize("&7Balance: &cunavailable"));
            }
            return true;
        }
    }

    public static final class Recipe implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Recipe(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!AdminPermissions.has(player, "recipe")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            Material mat;
            if (args.length < 1) {
                mat = player.getInventory().getItemInMainHand().getType();
            } else {
                mat = Material.matchMaterial(args[0].toUpperCase(Locale.ROOT));
            }
            if (mat == null || mat.isAir()) {
                player.sendMessage(plugin.colorize("&eUsage: /recipe [item]"));
                return true;
            }
            var recipes = Bukkit.getRecipesFor(new ItemStack(mat));
            if (recipes.isEmpty()) {
                player.sendMessage(plugin.colorize("&eNo recipe found for &f" + mat.name()));
                return true;
            }
            player.sendMessage(plugin.colorize("&a" + recipes.size() + " recipe(s) for &f" + mat.name()));
            return true;
        }
    }

    public static final class Skull implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Skull(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            if (!AdminPermissions.has(player, "skull")) { player.sendMessage(plugin.msg("no-permission")); return true; }
            OfflinePlayer owner = player;
            if (args.length >= 1) {
                owner = Bukkit.getOfflinePlayer(args[0]);
                if (!owner.hasPlayedBefore() && !owner.isOnline()) {
                    player.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0]));
                    return true;
                }
            }
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(owner);
            skull.setItemMeta(meta);
            var leftover = player.getInventory().addItem(skull);
            leftover.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
            player.sendMessage(plugin.colorize("&aSkull added."));
            return true;
        }
    }

    public static final class Lightning implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Lightning(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!AdminPermissions.has(sender, "lightning")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            Player target;
            if (args.length >= 1) {
                target = Bukkit.getPlayerExact(args[0]);
                if (target == null) { sender.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0])); return true; }
            } else if (sender instanceof Player p) {
                target = p;
            } else {
                sender.sendMessage(plugin.msg("players-only"));
                return true;
            }
            target.getWorld().strikeLightning(target.getLocation());
            sender.sendMessage(plugin.colorize("&aLightning at &f" + target.getName()));
            return true;
        }
    }

    public static final class Ext implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Ext(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            Player target;
            if (args.length >= 1) {
                if (!AdminPermissions.has(sender, "extinguish.others")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
                target = Bukkit.getPlayerExact(args[0]);
                if (target == null) { sender.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0])); return true; }
            } else if (sender instanceof Player p) {
                if (!AdminPermissions.has(sender, "extinguish")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
                target = p;
            } else {
                sender.sendMessage(plugin.msg("players-only"));
                return true;
            }
            target.setFireTicks(0);
            sender.sendMessage(plugin.colorize("&aExtinguished &f" + target.getName()));
            return true;
        }
    }

    public static final class Mutechat implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Mutechat(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!AdminPermissions.has(sender, "mutechat")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            plugin.playerState().setGlobalChatMuted(true);
            Bukkit.broadcastMessage(plugin.colorize("&cGlobal chat has been muted by staff."));
            return true;
        }
    }

    public static final class Unmutechat implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Unmutechat(RootEssentialsPlugin plugin) { this.plugin = plugin; }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!AdminPermissions.has(sender, "mutechat")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
            plugin.playerState().setGlobalChatMuted(false);
            Bukkit.broadcastMessage(plugin.colorize("&aGlobal chat has been unmuted."));
            return true;
        }
    }
}
