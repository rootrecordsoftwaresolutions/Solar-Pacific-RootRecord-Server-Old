package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.common.ChatLinks;
import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import com.rootrecord.minecraft.rootessentials.data.EconomySystemAccounts;
import com.rootrecord.minecraft.rootessentials.service.EconomyPlayerState;
import com.rootrecord.minecraft.rootessentials.treasury.EconomyBaseline;
import com.rootrecord.minecraft.rootessentials.treasury.TreasuryManager;
import com.rootrecord.minecraft.rootessentials.util.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PayCommand implements CommandExecutor, TabCompleter {

    private static final long DONATION_CONFIRM_MS = 120_000L;
    private static final double PAY_ALL_MIN_G = 1.0;
    private static final Set<String> RESERVE_ALIASES = Set.of(
            "reserve", "towny-server", "server", "server-reserve", "ava", "ava_ivy", "ivy");

    private final RootEconomyPlugin plugin;

    public PayCommand(RootEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!Permissions.has(player, "pay")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length < 1) {
            sendUsage(player);
            return true;
        }
        if (isReserveTarget(args[0])) {
            return handleReserveDonation(player, args);
        }
        if (args.length >= 1 && "all".equalsIgnoreCase(args[0])) {
            return handlePayAll(player, args);
        }
        if (args.length < 2) {
            sendUsage(player);
            return true;
        }
        return handlePlayerPay(player, args);
    }

    private boolean handleReserveDonation(Player player, String[] args) {
        if (args.length >= 2 && "cancel".equalsIgnoreCase(args[1])) {
            plugin.playerState().clearReserveDonation(player.getUniqueId());
            player.sendMessage(plugin.msg("pay-reserve-cancelled"));
            return true;
        }
        if (args.length >= 2 && "confirm".equalsIgnoreCase(args[1])) {
            executeReserveDonation(player);
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.colorize("&eUsage: &f/pay reserve <amount>"));
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException ex) {
            player.sendMessage(plugin.msg("invalid-number"));
            return true;
        }
        if (amount <= 0) {
            player.sendMessage(plugin.msg("pay-invalid-amount"));
            return true;
        }
        offerReserveDonation(player, amount);
        return true;
    }

    private void offerReserveDonation(Player player, double amount) {
        try {
            double balance = plugin.balance(player.getUniqueId(), player.getName());
            if (balance + 1e-9 < amount) {
                player.sendMessage(plugin.msg("pay-insufficient")
                        .replace("{amount}", plugin.money(amount))
                        .replace("{balance}", plugin.money(balance))
                        .replace("{currency}", plugin.currency()));
                return;
            }
            plugin.playerState().offerReserveDonation(player.getUniqueId(), amount);
            TreasuryManager treasury = plugin.treasury();
            String taxHint = "";
            if (treasury != null && treasury.dynamicTaxEnabled()) {
                double rate = treasury.effectiveTransactionTaxRate() * 100.0;
                taxHint = plugin.colorize("&7Current transaction tax: &f"
                        + String.format(Locale.US, "%.3f", rate) + "%");
                player.sendMessage(taxHint);
            }
            player.sendMessage(plugin.msg("pay-reserve-confirm-prompt")
                    .replace("{amount}", plugin.money(amount))
                    .replace("{currency}", plugin.currency()));
            player.sendMessage(plugin.colorize(
                    "&7This &fcredits&7 the Server Reserve from your wallet  -  no transaction tax."));
            player.sendMessage(ChatLinks.confirmCancel("/pay reserve confirm", "/pay reserve cancel"));
        } catch (Exception ex) {
            player.sendMessage(plugin.colorize("&cDonation failed: &f" + ex.getMessage()));
        }
    }

    private void executeReserveDonation(Player player) {
        EconomyPlayerState.PendingReserveDonation pending =
                plugin.playerState().pendingReserveDonation(player.getUniqueId());
        if (pending == null) {
            player.sendMessage(plugin.msg("pay-reserve-none-pending"));
            return;
        }
        if (System.currentTimeMillis() - pending.createdAtMs() > DONATION_CONFIRM_MS) {
            plugin.playerState().clearReserveDonation(player.getUniqueId());
            player.sendMessage(plugin.msg("pay-reserve-expired"));
            return;
        }
        double amount = pending.amountGold();
        try {
            EconomyBaseline.NoteSupplySnapshot before = plugin.reserveSnapshot(null).noteSupply();
            boolean ok = plugin.donateToReserve(player.getUniqueId(), player.getName(), amount);
            plugin.playerState().clearReserveDonation(player.getUniqueId());
            if (!ok) {
                double bal = plugin.balance(player.getUniqueId(), player.getName());
                player.sendMessage(plugin.msg("pay-insufficient")
                        .replace("{amount}", plugin.money(amount))
                        .replace("{balance}", plugin.money(bal))
                        .replace("{currency}", plugin.currency()));
                return;
            }
            EconomyBaseline.NoteSupplySnapshot after = plugin.reserveSnapshot(null).noteSupply();
            player.sendMessage(plugin.msg("pay-reserve-success")
                    .replace("{amount}", plugin.money(amount))
                    .replace("{currency}", plugin.currency()));
            if (after.backingPct() != null) {
                player.sendMessage(plugin.msg("pay-reserve-backing")
                        .replace("{backing}", formatPct(after.backingPct())));
            }
            if (before.overIssued() && !after.overIssued()) {
                player.sendMessage(plugin.colorize("&aNotes are now fully backed  -  transaction tax is &f0%&a."));
            } else if (before.overIssueG() > after.overIssueG() + 0.009) {
                player.sendMessage(plugin.msg("pay-reserve-over-issue")
                        .replace("{before}", plugin.money(before.overIssueG()))
                        .replace("{after}", plugin.money(after.overIssueG()))
                        .replace("{currency}", plugin.currency()));
            }
        } catch (Exception ex) {
            player.sendMessage(plugin.colorize("&cDonation failed: &f" + ex.getMessage()));
        }
    }

    private boolean handlePayAll(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.colorize(
                    "&eUsage: &f/pay all <amount> &7 -  min &f1 G &7per online player (except you)"));
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException ex) {
            player.sendMessage(plugin.msg("invalid-number"));
            return true;
        }
        if (amount + 1e-9 < PAY_ALL_MIN_G) {
            player.sendMessage(plugin.msg("pay-all-min")
                    .replace("{min}", plugin.money(PAY_ALL_MIN_G))
                    .replace("{currency}", plugin.currency()));
            return true;
        }
        List<Player> online = new ArrayList<>(plugin.getServer().getOnlinePlayers());
        online.removeIf(p -> p.getUniqueId().equals(player.getUniqueId()));
        if (online.isEmpty()) {
            player.sendMessage(plugin.msg("pay-all-nobody"));
            return true;
        }
        List<Player> eligible = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        try {
            for (Player target : online) {
                if (plugin.acceptsPay(target.getUniqueId())) {
                    eligible.add(target);
                } else {
                    skipped.add(target.getName());
                }
            }
        } catch (Exception ex) {
            player.sendMessage(plugin.colorize("&cPayment failed: &f" + ex.getMessage()));
            return true;
        }
        if (eligible.isEmpty()) {
            player.sendMessage(plugin.msg("pay-all-none-accepting"));
            return true;
        }
        double totalGross = amount * eligible.size();
        try {
            double balance = plugin.balance(player.getUniqueId(), player.getName());
            if (balance + 1e-9 < totalGross) {
                player.sendMessage(plugin.msg("pay-all-insufficient")
                        .replace("{total}", plugin.money(totalGross))
                        .replace("{count}", Integer.toString(eligible.size()))
                        .replace("{amount}", plugin.money(amount))
                        .replace("{balance}", plugin.money(balance))
                        .replace("{currency}", plugin.currency()));
                return true;
            }
            int paid = 0;
            for (Player target : eligible) {
                boolean ok = plugin.transfer(player, target, amount);
                if (!ok) {
                    player.sendMessage(plugin.msg("pay-all-partial")
                            .replace("{paid}", Integer.toString(paid))
                            .replace("{count}", Integer.toString(eligible.size()))
                            .replace("{currency}", plugin.currency()));
                    return true;
                }
                paid++;
                target.sendMessage(plugin.msg("pay-received")
                        .replace("{amount}", plugin.money(amount))
                        .replace("{player}", player.getName())
                        .replace("{currency}", plugin.currency()));
            }
            player.sendMessage(plugin.msg("pay-all-sent")
                    .replace("{amount}", plugin.money(amount))
                    .replace("{count}", Integer.toString(paid))
                    .replace("{total}", plugin.money(totalGross))
                    .replace("{currency}", plugin.currency()));
            if (!skipped.isEmpty()) {
                player.sendMessage(plugin.msg("pay-all-skipped")
                        .replace("{count}", Integer.toString(skipped.size()))
                        .replace("{names}", String.join(", ", skipped)));
            }
            return true;
        } catch (Exception ex) {
            player.sendMessage(plugin.colorize("&cPayment failed: &f" + ex.getMessage()));
            return true;
        }
    }

    private boolean handlePlayerPay(Player player, String[] args) {
        var target = plugin.getServer().getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0]));
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(plugin.msg("pay-self"));
            return true;
        }
        if (EconomySystemAccounts.isAvaReserveAlias(target.getUniqueId(), target.getName())) {
            return handleReserveDonation(player, new String[] {"reserve", args[1]});
        }
        try {
            if (!plugin.acceptsPay(target.getUniqueId())) {
                player.sendMessage(plugin.msg("pay-disabled"));
                return true;
            }
        } catch (Exception ex) {
            player.sendMessage(plugin.colorize("&cPayment failed: &f" + ex.getMessage()));
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException ex) {
            player.sendMessage(plugin.msg("invalid-number"));
            return true;
        }
        if (amount <= 0) {
            player.sendMessage(plugin.msg("pay-invalid-amount"));
            return true;
        }
        try {
            boolean ok = plugin.transfer(player, target, amount);
            if (!ok) {
                double bal = plugin.balance(player.getUniqueId(), player.getName());
                player.sendMessage(plugin.msg("pay-insufficient")
                        .replace("{amount}", plugin.money(amount))
                        .replace("{balance}", plugin.money(bal))
                        .replace("{currency}", plugin.currency()));
                return true;
            }
            player.sendMessage(plugin.msg("pay-sent")
                    .replace("{amount}", plugin.money(amount))
                    .replace("{player}", target.getName())
                    .replace("{currency}", plugin.currency()));
            target.sendMessage(plugin.msg("pay-received")
                    .replace("{amount}", plugin.money(amount))
                    .replace("{player}", player.getName())
                    .replace("{currency}", plugin.currency()));
            return true;
        } catch (Exception ex) {
            player.sendMessage(plugin.colorize("&cPayment failed: &f" + ex.getMessage()));
            return true;
        }
    }

    private boolean isReserveTarget(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String key = name.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (RESERVE_ALIASES.contains(key)) {
            return true;
        }
        var online = plugin.getServer().getPlayerExact(name);
        return online != null
                && EconomySystemAccounts.isAvaReserveAlias(online.getUniqueId(), online.getName());
    }

    private static String formatPct(double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    private void sendUsage(Player player) {
        player.sendMessage(plugin.colorize(
                "&eUsage: &f/pay <player> <amount> &7| &f/pay all <amount> &7| &f/pay reserve <amount>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || !Permissions.has(sender, "pay")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> options = new ArrayList<>(RESERVE_ALIASES);
            options.add("all");
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                options.add(online.getName());
            }
            return filterPrefix(options, args[0]);
        }
        if (args.length == 2 && "all".equalsIgnoreCase(args[0])) {
            return filterPrefix(List.of("1", "5", "10", "25", "100"), args[1]);
        }
        if (args.length == 2 && isReserveTarget(args[0])) {
            return filterPrefix(List.of("confirm", "cancel", "1", "10", "100"), args[1]);
        }
        return List.of();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
