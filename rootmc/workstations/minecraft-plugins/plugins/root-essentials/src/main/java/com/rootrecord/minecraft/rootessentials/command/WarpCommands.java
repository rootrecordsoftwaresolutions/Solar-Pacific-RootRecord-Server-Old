package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import com.rootrecord.minecraft.rootessentials.util.Permissions;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class WarpCommands {

    private static final Pattern WARP_NAME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]{2,31}$");

    private WarpCommands() {}

    public static final class Warp implements CommandExecutor, TabCompleter {
        private final RootEssentialsPlugin plugin;

        public Warp(RootEssentialsPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.msg("players-only"));
                return true;
            }
            if (!Permissions.has(player, "warp")) {
                player.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            if (args.length < 1) {
                player.sendMessage(plugin.colorize("&eUsage: &f/warp <name> &7| &f/warp create <name>"));
                return true;
            }
            if (args[0].equalsIgnoreCase("create")) {
                return handleCreate(player, args);
            }
            return handleTeleport(player, args[0]);
        }

        private boolean handleCreate(Player player, String[] args) {
            if (args.length < 2) {
                player.sendMessage(plugin.colorize("&eUsage: &f/warp create <name>"));
                return true;
            }
            String name = args[1];
            if (!WARP_NAME.matcher(name).matches()) {
                player.sendMessage(plugin.msg("warp-create-invalid-name"));
                return true;
            }
            double cost = plugin.serviceFee("warp-create", 500.0);
            try {
                if (plugin.warps().get(name) != null) {
                    player.sendMessage(plugin.msg("warp-create-exists").replace("{name}", name.toLowerCase(Locale.ROOT)));
                    return true;
                }
                double balance = plugin.balance(player.getUniqueId(), player.getName());
                if (balance + 1e-9 < cost) {
                    player.sendMessage(plugin.msg("warp-create-insufficient")
                            .replace("{amount}", plugin.money(cost))
                            .replace("{balance}", plugin.money(balance))
                            .replace("{currency}", plugin.currency()));
                    return true;
                }
                if (!plugin.withdraw(player.getUniqueId(), player.getName(), cost)) {
                    player.sendMessage(plugin.msg("warp-create-insufficient")
                            .replace("{amount}", plugin.money(cost))
                            .replace("{balance}", plugin.money(balance))
                            .replace("{currency}", plugin.currency()));
                    return true;
                }
                try {
                    plugin.warps().upsert(name, player.getLocation());
                } catch (Exception ex) {
                    plugin.deposit(player.getUniqueId(), player.getName(), cost);
                    throw ex;
                }
                plugin.sinkServiceFee(player.getUniqueId(), player.getName(), cost, "warp-create");
                player.sendMessage(plugin.msg("warp-create-success")
                        .replace("{name}", name.toLowerCase(Locale.ROOT))
                        .replace("{amount}", plugin.money(cost))
                        .replace("{currency}", plugin.currency()));
            } catch (Exception ex) {
                player.sendMessage(plugin.colorize("&cWarp create failed: &f" + ex.getMessage()));
            }
            return true;
        }

        private boolean handleTeleport(Player player, String warpName) {
            try {
                Location loc = plugin.warps().get(warpName);
                if (loc == null) {
                    ChatUi.entry(player, "Warp", "unknown · /warps", "alert");
                    return true;
                }
                String display = warpName.toLowerCase(Locale.ROOT);
                plugin.teleportPlayer(player, loc, () -> ChatUi.entry(player, "Warp", display, "ok"));
            } catch (Exception ex) {
                ChatUi.entry(player, "Warp", "error · try /warps again", "alert");
            }
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length == 1) {
                List<String> out = new ArrayList<>();
                if ("create".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add("create");
                }
                try {
                    plugin.warps().names().stream()
                            .filter(n -> n.startsWith(args[0].toLowerCase(Locale.ROOT)))
                            .forEach(out::add);
                } catch (Exception ignored) {
                    // skip
                }
                return out;
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
                return List.of();
            }
            return List.of();
        }
    }

    public static final class Warps implements CommandExecutor {
        private final RootEssentialsPlugin plugin;

        public Warps(RootEssentialsPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!Permissions.has(sender, "warps")) {
                sender.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            try {
                List<String> names = plugin.warps().names();
                if (names.isEmpty()) {
                    sender.sendMessage(plugin.colorize("&7No warps configured."));
                    return true;
                }
                sender.sendMessage(plugin.colorize("&7Warps (&f" + names.size() + "&7): &f" + String.join(", ", names)));
            } catch (Exception ex) {
                sender.sendMessage(plugin.colorize("&cCould not list warps."));
            }
            return true;
        }
    }
}
