package com.rootrecord.minecraft.roothaste.command;

import com.rootrecord.minecraft.roothaste.PassTheme;
import com.rootrecord.minecraft.roothaste.RootHastePlugin;
import com.rootrecord.minecraft.roothaste.data.JointRecordStore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Handles /torch and /joint — only the active theme's light command may spark. */
public final class LightCommand implements CommandExecutor, TabCompleter {

    private final RootHastePlugin plugin;

    public LightCommand(RootHastePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean jointCmd = "joint".equalsIgnoreCase(command.getName());
        PassTheme theme = plugin.theme();
        boolean cmdMatchesTheme = jointCmd == theme.isJoint();

        if (args.length >= 1) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("reload".equals(sub)) {
                if (!plugin.hasReload(sender)) {
                    sender.sendMessage(plugin.msg("no-permission"));
                    return true;
                }
                plugin.reloadLocal();
                sender.sendMessage(plugin.colorize("&aRoot-Haste reloaded — theme &f" + plugin.theme().name() + "&a."));
                return true;
            }
            if ("price".equals(sub) || "cost".equals(sub)) {
                if (!cmdMatchesTheme) {
                    sender.sendMessage(plugin.msg(jointCmd ? "mode-locked-joint" : "mode-locked-torch"));
                    return true;
                }
                return handlePrice(sender, args);
            }
            if ("longest".equals(sub) || "record".equals(sub) || "stats".equals(sub)) {
                if (!cmdMatchesTheme) {
                    sender.sendMessage(plugin.msg(jointCmd ? "mode-locked-joint" : "mode-locked-torch"));
                    return true;
                }
                sendLongest(sender);
                return true;
            }
        }
        if (!cmdMatchesTheme) {
            sender.sendMessage(plugin.msg(jointCmd ? "mode-locked-joint" : "mode-locked-torch"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        String result = plugin.sessions().tryLight(player);
        if (result != null) {
            sendDenial(player, result);
        }
        return true;
    }

    private boolean handlePrice(CommandSender sender, String[] args) {
        if (!plugin.hasPrice(sender)) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length < 2) {
            double current = plugin.sessions() == null ? 0 : plugin.sessions().costGold();
            sender.sendMessage(plugin.msg("price-current").replace("{fee}", formatGold(current)));
            sender.sendMessage(plugin.msg("price-usage"));
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(plugin.msg("price-invalid"));
            return true;
        }
        if (amount < 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            sender.sendMessage(plugin.msg("price-invalid"));
            return true;
        }
        if (!plugin.setCostGold(amount)) {
            sender.sendMessage(plugin.msg("price-failed"));
            return true;
        }
        sender.sendMessage(plugin.msg("price-set").replace("{fee}", formatGold(amount)));
        return true;
    }

    private void sendLongest(CommandSender sender) {
        JointRecordStore records = plugin.records();
        if (records == null || records.longestMs() <= 0) {
            sender.sendMessage(plugin.msg("no-record"));
            return;
        }
        sender.sendMessage(plugin.msg("longest")
                .replace("{duration}", JointRecordStore.formatDuration(records.longestMs()))
                .replace("{player}", records.sparkedBy().isBlank() ? "?" : records.sparkedBy())
                .replace("{holder}", records.lastHolder().isBlank() ? "?" : records.lastHolder()));
    }

    private void sendDenial(Player player, String result) {
        if (result.startsWith("insufficient:")) {
            player.sendMessage(result.substring("insufficient:".length()));
            return;
        }
        if (result.startsWith("fee-failed:")) {
            player.sendMessage(result.substring("fee-failed:".length()));
            return;
        }
        player.sendMessage(plugin.msg(result));
    }

    private static String formatGold(double amount) {
        if (Math.abs(amount - Math.rint(amount)) < 1e-9) {
            return String.valueOf((long) Math.rint(amount));
        }
        return String.format(Locale.US, "%.2f", amount);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean canAdmin = plugin.hasPrice(sender);
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String opt : List.of("longest", "record", "stats", "price", "cost", "reload")) {
                if (!opt.startsWith(prefix)) {
                    continue;
                }
                if (("reload".equals(opt) || "price".equals(opt) || "cost".equals(opt)) && !canAdmin) {
                    continue;
                }
                out.add(opt);
            }
            return out;
        }
        if (args.length == 2 && canAdmin
                && ("price".equalsIgnoreCase(args[0]) || "cost".equalsIgnoreCase(args[0]))) {
            return List.of("0", "1", "5", "25", "50");
        }
        return List.of();
    }
}
