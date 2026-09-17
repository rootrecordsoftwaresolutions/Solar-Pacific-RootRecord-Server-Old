package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import com.rootrecord.minecraft.rootessentials.treasury.TreasuryManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class GrantCommand implements CommandExecutor {

    private final RootEconomyPlugin plugin;

    public GrantCommand(RootEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rootmc.grant") && !sender.hasPermission("rootessentials.grant")) {
            sender.sendMessage(plugin.colorize("&cYou do not have permission."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.colorize("&eUsage: /grant <player> <amount> [reason]"));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(plugin.colorize("&ePlayer not found: &f" + args[0]));
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(plugin.colorize("&eInvalid amount."));
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage(plugin.colorize("&eAmount must be greater than 0."));
            return true;
        }
        TreasuryManager treasury = plugin.treasury();
        if (treasury == null) {
            sender.sendMessage(plugin.colorize(
                    "&cTreasury is not available  -  Root-Essentials may have failed MySQL init. Check console."));
            return true;
        }
        String reason = args.length >= 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "";
        String targetName = target.getName() == null ? args[0] : target.getName();
        Player operator = sender instanceof Player p ? p : null;
        boolean ok = treasury.grantToPlayer(
                target.getUniqueId(),
                targetName,
                amount,
                operator == null ? null : operator.getUniqueId(),
                operator == null ? sender.getName() : operator.getName(),
                reason);
        if (!ok) {
            sender.sendMessage(plugin.colorize(
                    "&cGrant failed  -  see server log for details."));
            return true;
        }
        sender.sendMessage(plugin.colorize(
                "&aGranted &f" + formatGold(amount) + " G&a from treasury to &f" + targetName
                        + (reason.isBlank() ? "" : "&7 (" + reason + ")")));
        if (target.isOnline() && target.getPlayer() != null) {
            target.getPlayer().sendMessage(plugin.colorize(
                    "&aYou received &f" + formatGold(amount) + " G&a from the server treasury."));
        }
        return true;
    }

    private static String formatGold(double gold) {
        if (gold == Math.rint(gold)) {
            return String.valueOf((long) gold);
        }
        return String.format(java.util.Locale.US, "%.3f", gold);
    }
}
