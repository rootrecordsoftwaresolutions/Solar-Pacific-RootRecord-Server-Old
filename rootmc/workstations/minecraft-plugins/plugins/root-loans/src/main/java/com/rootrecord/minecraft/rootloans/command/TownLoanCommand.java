package com.rootrecord.minecraft.rootloans.command;

import com.rootrecord.minecraft.rootloans.RootLoansPlugin;
import com.rootrecord.minecraft.rootloans.data.TownLoansStore;
import com.rootrecord.minecraft.rootloans.service.TownLoanService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public final class TownLoanCommand implements CommandExecutor {

    private final RootLoansPlugin plugin;

    public TownLoanCommand(RootLoansPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isTownLoansReady()) {
            sender.sendMessage(plugin.msg("town-disabled"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(plugin.colorize(
                    "&eUsage: /townloan <take|repay|info|list> [amount] &7— Server Reserve → town bank (mayors)"));
            return true;
        }
        return switch (args[0].toLowerCase()) {
            case "take", "borrow" -> handleTake(sender, args);
            case "repay", "pay" -> handleRepay(sender, args);
            case "info", "status" -> handleInfo(sender);
            case "list" -> handleList(sender);
            default -> {
                sender.sendMessage(plugin.colorize("&eUsage: /townloan <take|repay|info|list> [amount]"));
                yield true;
            }
        };
    }

    private boolean handleTake(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!player.hasPermission("rootloans.town.use")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.colorize("&eUsage: /townloan take <amount>"));
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException ex) {
            player.sendMessage(plugin.msg("invalid-amount"));
            return true;
        }
        TownLoanService.TakeResult result = plugin.townLoans().takeLoan(player, amount);
        if (!result.ok()) {
            String msg = plugin.msg(result.messageKey());
            if ("town-over-limit".equals(result.messageKey())) {
                msg = msg.replace("{max}", plugin.money(result.maxLoan()));
            }
            player.sendMessage(msg);
            return true;
        }
        player.sendMessage(plugin.msg("town-take-success")
                .replace("{town}", result.townName())
                .replace("{principal}", plugin.money(result.principal()))
                .replace("{owed}", plugin.money(result.owed()))
                .replace("{interest}", String.valueOf((long) (plugin.townLoansConfig().interestRate() * 100))));
        return true;
    }

    private boolean handleRepay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!player.hasPermission("rootloans.town.use")) {
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
        TownLoanService.ManualRepayResult result = plugin.townLoans().manualRepay(player, amount);
        if (!result.ok()) {
            String msg = plugin.msg(result.messageKey());
            if ("town-bank-insufficient".equals(result.messageKey())) {
                msg = msg.replace("{balance}", plugin.money(result.balance()))
                        .replace("{amount}", plugin.money(result.needed()));
            }
            player.sendMessage(msg);
            return true;
        }
        player.sendMessage(plugin.msg("town-repay-success")
                .replace("{town}", result.townName())
                .replace("{amount}", plugin.money(result.applied()))
                .replace("{owed}", plugin.money(result.remaining())));
        result.payoff().ifPresent(p ->
                player.sendMessage(plugin.msg("town-paid-off")
                        .replace("{max}", plugin.money(plugin.townLoansConfig().maxLoan()))));
        return true;
    }

    private boolean handleInfo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!player.hasPermission("rootloans.town.use")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        var loan = plugin.townLoans().activeLoanForMayor(player);
        if (loan.isEmpty()) {
            player.sendMessage(plugin.msg("town-info-clear")
                    .replace("{max}", plugin.money(plugin.townLoansConfig().maxLoan())));
            return true;
        }
        TownLoansStore.ActiveTownLoan active = loan.get();
        player.sendMessage(plugin.msg("town-info-header")
                .replace("{town}", active.townName())
                .replace("{owed}", plugin.money(active.amountOwed()))
                .replace("{max}", plugin.money(plugin.townLoansConfig().maxLoan())));
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!sender.hasPermission("rootloans.town.list")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        try {
            List<TownLoansStore.ActiveTownLoan> rows = plugin.townLoansStore().listActive();
            if (rows.isEmpty()) {
                sender.sendMessage(plugin.msg("town-list-empty"));
                return true;
            }
            for (TownLoansStore.ActiveTownLoan row : rows) {
                sender.sendMessage(plugin.msg("town-list-row")
                        .replace("{town}", row.townName())
                        .replace("{owed}", plugin.money(row.amountOwed()))
                        .replace("{principal}", plugin.money(row.principal())));
            }
        } catch (Exception ex) {
            sender.sendMessage(plugin.colorize("&cTown loan list failed: &f" + ex.getMessage()));
        }
        return true;
    }
}
