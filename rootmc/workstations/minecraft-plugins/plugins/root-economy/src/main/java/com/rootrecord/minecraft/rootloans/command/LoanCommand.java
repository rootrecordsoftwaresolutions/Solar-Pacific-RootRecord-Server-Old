package com.rootrecord.minecraft.rootloans.command;

import com.rootrecord.minecraft.rootloans.RootLoansPlugin;
import com.rootrecord.minecraft.rootloans.data.LoansStore;
import com.rootrecord.minecraft.rootloans.service.LoanService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;

public final class LoanCommand implements CommandExecutor {

    private final RootLoansPlugin plugin;

    public LoanCommand(RootLoansPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isLoansReady()) {
            String reason = plugin.loansDisabledReason();
            if ("treasury".equals(reason)) {
                sender.sendMessage(plugin.msg("treasury-unavailable"));
            } else {
                sender.sendMessage(plugin.msg("disabled"));
            }
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(plugin.colorize("&eUsage: /loan <take|info|repay|list> [amount]"));
            return true;
        }
        return switch (args[0].toLowerCase()) {
            case "take" -> handleTake(sender, args);
            case "info" -> handleInfo(sender);
            case "repay" -> handleRepay(sender, args);
            case "list" -> handleList(sender);
            default -> {
                sender.sendMessage(plugin.colorize("&eUsage: /loan <take|info|repay|list> [amount]"));
                yield true;
            }
        };
    }

    private boolean handleTake(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!player.hasPermission("rootloans.use")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.colorize("&eUsage: /loan take <amount>"));
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException ex) {
            player.sendMessage(plugin.msg("invalid-amount"));
            return true;
        }
        LoanService.TakeResult result = plugin.loans().takeLoan(player, amount);
        if (!result.ok()) {
            String msg = plugin.msg(result.messageKey());
            if ("over-limit".equals(result.messageKey())) {
                msg = msg.replace("{max}", plugin.money(result.maxLoan()));
            } else if ("max-loans".equals(result.messageKey())) {
                msg = msg.replace("{max}", String.valueOf(plugin.loansConfig().maxConcurrentLoans()));
            } else if ("rate-limited".equals(result.messageKey())) {
                msg = msg.replace("{max}", String.valueOf(plugin.loansConfig().maxTakesPer24h()))
                        .replace("{time}", formatDuration(result.waitMs()));
            }
            player.sendMessage(msg);
            return true;
        }
        player.sendMessage(plugin.msg("take-success")
                .replace("{principal}", plugin.money(result.principal()))
                .replace("{owed}", plugin.money(result.owed()))
                .replace("{interest}", String.valueOf((long) (plugin.loansConfig().interestRate() * 100))));
        return true;
    }

    private boolean handleInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!player.hasPermission("rootloans.use")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        try {
            var summary = plugin.loans().balanceSummary(player.getUniqueId());
            if (summary.isEmpty()) {
                player.sendMessage(plugin.msg("info-clear")
                        .replace("{max}", plugin.money(plugin.loans().borrowLimitFor(player.getUniqueId())))
                        .replace("{takes}", "0")
                        .replace("{max_takes}", String.valueOf(plugin.loansConfig().maxTakesPer24h())));
                return true;
            }
            var s = summary.get();
            if (s.owed() <= 0) {
                player.sendMessage(plugin.msg("info-clear")
                        .replace("{max}", plugin.money(s.maxLoan()))
                        .replace("{takes}", String.valueOf(s.takesInRolling24h()))
                        .replace("{max_takes}", String.valueOf(s.maxTakesPer24h())));
            } else {
                player.sendMessage(plugin.msg("info-header")
                        .replace("{owed}", plugin.money(s.owed()))
                        .replace("{max}", plugin.money(s.maxLoan()))
                        .replace("{takes}", String.valueOf(s.takesInRolling24h()))
                        .replace("{max_takes}", String.valueOf(s.maxTakesPer24h())));
                List<LoansStore.ActiveLoan> loans = plugin.store().findActiveAll(player.getUniqueId());
                int n = 1;
                for (LoansStore.ActiveLoan loan : loans) {
                    player.sendMessage(plugin.msg("info-loan-row")
                            .replace("{n}", String.valueOf(n++))
                            .replace("{owed}", plugin.money(loan.amountOwed()))
                            .replace("{principal}", plugin.money(loan.principal())));
                }
            }
        } catch (Exception ex) {
            player.sendMessage(plugin.colorize("&cLoan info failed: &f" + ex.getMessage()));
        }
        return true;
    }

    private boolean handleRepay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!player.hasPermission("rootloans.use")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        double amount = 0;
        if (args.length >= 2) {
            try {
                amount = Double.parseDouble(args[1]);
            } catch (NumberFormatException ex) {
                player.sendMessage(plugin.msg("invalid-amount"));
                return true;
            }
        }
        LoanService.ManualRepayResult result = plugin.loans().manualRepay(player, amount);
        if (!result.ok()) {
            String msg = plugin.msg(result.messageKey());
            if ("repay-insufficient".equals(result.messageKey())) {
                msg = msg.replace("{balance}", plugin.money(result.balance()))
                        .replace("{amount}", plugin.money(result.needed()));
            }
            player.sendMessage(msg);
            return true;
        }
        player.sendMessage(plugin.msg("repay-success")
                .replace("{amount}", plugin.money(result.applied()))
                .replace("{owed}", plugin.money(result.remaining())));
        result.payoff().ifPresent(p ->
                player.sendMessage(plugin.msg("paid-off")
                        .replace("{max}", plugin.money(plugin.loans().borrowLimitFor(player.getUniqueId())))));
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!sender.hasPermission("rootloans.list")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        try {
            List<LoansStore.ActiveLoan> rows = plugin.store().listActive();
            if (rows.isEmpty()) {
                sender.sendMessage(plugin.msg("list-empty"));
                return true;
            }
            for (LoansStore.ActiveLoan row : rows) {
                sender.sendMessage(plugin.msg("list-row")
                        .replace("{player}", row.username())
                        .replace("{owed}", plugin.money(row.amountOwed()))
                        .replace("{principal}", plugin.money(row.principal())));
            }
        } catch (Exception ex) {
            sender.sendMessage(plugin.colorize("&cLoan list failed: &f" + ex.getMessage()));
        }
        return true;
    }

    private static String formatDuration(long millis) {
        if (millis <= 0) {
            return "0m";
        }
        Duration d = Duration.ofMillis(millis);
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }
}
