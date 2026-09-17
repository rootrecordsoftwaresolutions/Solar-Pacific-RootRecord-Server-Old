package com.rootrecord.minecraft.rootadmin.command;

import com.rootrecord.minecraft.rootadmin.RootAdminPlugin;
import com.rootrecord.minecraft.rootadmin.util.AdminPermissions;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

/**
 * {@code /worldborder} [set] &lt;radius&gt; — persist ±radius to root-admin.yml and apply.
 * Bare {@code /worldborder} / {@code /wb} still reapplies the saved config.
 */
public final class WorldBorderCommand implements CommandExecutor, TabCompleter {

    private final RootAdminPlugin plugin;

    public WorldBorderCommand(RootAdminPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!AdminPermissions.has(sender, "worldborder")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length == 0) {
            plugin.applyWorldBorder();
            sender.sendMessage(plugin.msg("world-border-applied"));
            return true;
        }
        String a0 = args[0].toLowerCase(Locale.ROOT);
        if (a0.equals("get") || a0.equals("status")) {
            sendStatus(sender);
            return true;
        }
        Integer radius = parseRadius(args);
        if (radius == null) {
            sender.sendMessage(plugin.colorize(
                    "&eUsage: /" + label + " [set] <radius>  &7(±blocks from center)"));
            sender.sendMessage(plugin.colorize("&e       /" + label + " get"));
            return true;
        }
        persistAndApply(sender, radius);
        return true;
    }

    private static Integer parseRadius(String[] args) {
        if (args.length >= 2
                && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("radius"))) {
            return parsePositive(args[1]);
        }
        if (args.length == 1) {
            return parsePositive(args[0]);
        }
        return null;
    }

    private static Integer parsePositive(String raw) {
        try {
            int n = Integer.parseInt(raw.trim());
            if (n >= 1 && n <= 14_999_992) {
                return n;
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private void persistAndApply(CommandSender sender, int radius) {
        int warning = Math.max(8, Math.min(64, radius / 15));
        plugin.getConfig().set("world-border.enabled", true);
        plugin.getConfig().set("world-border.radius", radius);
        plugin.getConfig().set("world-border.warning-blocks", warning);
        plugin.saveLocalConfig();
        plugin.applyWorldBorder();
        sender.sendMessage(plugin.colorize(
                "&aWorld border saved &f±" + radius
                        + " &7(diameter " + (radius * 2)
                        + ", nether /8) &aand applied."));
    }

    private void sendStatus(CommandSender sender) {
        int radius = plugin.getConfig().getInt("world-border.radius", 500);
        sender.sendMessage(plugin.colorize("&7Config radius &f±" + radius
                + " &7· diameter &f" + (radius * 2)));
        for (String name : plugin.getConfig().getStringList("world-border.worlds")) {
            World world = Bukkit.getWorld(name);
            if (world == null) {
                sender.sendMessage(plugin.colorize("&8- &f" + name + " &7(unloaded)"));
                continue;
            }
            double size = world.getWorldBorder().getSize();
            sender.sendMessage(plugin.colorize(
                    "&8- &f" + name + " &7vanilla &f" + Math.round(size)
                            + " &7wide (±" + Math.round(size / 2.0) + ")"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("set", "get", "500", "1000");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return List.of("500", "1000", "2500");
        }
        return List.of();
    }
}
