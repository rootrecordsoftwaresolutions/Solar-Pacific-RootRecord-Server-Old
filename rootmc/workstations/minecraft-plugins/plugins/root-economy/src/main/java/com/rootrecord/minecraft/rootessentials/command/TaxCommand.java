package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import com.rootrecord.minecraft.rootessentials.treasury.ReserveLedgerTaxTiers;
import com.rootrecord.minecraft.rootessentials.treasury.TreasuryManager;
import com.rootrecord.minecraft.rootessentials.web.RootMcEconomyWeb;
import com.rootrecord.minecraft.common.RootMcVotePerk;
import com.rootrecord.minecraft.common.ShadedServiceBridge;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/** Live transaction + claim tax. Gold/skills bonus lives on /gold. */
public final class TaxCommand implements CommandExecutor {

    private final RootEconomyPlugin plugin;

    public TaxCommand(RootEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            sendTax(sender);
            return true;
        } catch (Exception ex) {
            sender.sendMessage(plugin.colorize("&cTax lookup failed: &f" + ex.getMessage()));
            return true;
        }
    }

    private void sendTax(CommandSender sender) {
        TreasuryManager treasury = plugin.treasury();
        ChatUi.banner(sender, "Taxes");

        if (treasury == null || !treasury.transactionTaxEnabled()) {
            ChatUi.entry(sender, "Transaction", "tax off | tell staff", "alert");
        } else {
            double reserveRate = treasury.reserveTierTaxRate();
            double host = treasury.powerTaxRate();
            double effective = treasury.effectiveTransactionTaxRate();
            double reserve = treasury.reserveUtilization();

            ChatUi.entry(sender, "Effective", taxPct(effective) + "%");
            if (treasury.dynamicTaxEnabled()) {
                ChatUi.entry(
                        sender,
                        "Reserve",
                        plugin.money(reserve) + " G · " + taxPct(reserveRate) + "% · "
                                + ReserveLedgerTaxTiers.tierLabel(reserve));
            } else {
                ChatUi.entry(sender, "Reserve", taxPct(reserveRate) + "%");
            }
            ChatUi.entry(sender, "Floor", "0.05% on every transfer");
            if (host >= 0.0005d) {
                ChatUi.entry(sender, "Env", "+" + taxPct(host) + "% solar offline", "alert");
            } else {
                ChatUi.entry(sender, "Env", "none · solar connected", "ok");
            }
            ChatUi.tip(sender, "Tiers: 0.05% min · 1% <1k · 2% <0 · 3% <-1k · 4% <-2k · 5% <-4k · 10% <-10k");
            appendVotePerk(sender, treasury);
        }

        ChatUi.section(sender, "Claims");
        ChatUi.entry(sender, "First plot", "tax-free · /c claim founding");
        ChatUi.entry(sender, "Later plots", "reserve tax on extra areas + expansions");
        ChatUi.tip(sender, "No Towny tax. Mine site taxes: /gold");

        ChatUi.links(
                sender,
                "Economy", RootMcEconomyWeb.economy(),
                "Constitution", "https://rootmc.net/wiki/constitution/#taxes-fees-reference");
        ChatUi.tip(sender, "/gold  |  /mint  |  /reserve  |  /economy");
    }

    private void appendVotePerk(CommandSender sender, TreasuryManager treasury) {
        if (!(sender instanceof Player player)) {
            ChatUi.entry(sender, "Vote perk", "5 listing votes / 24h → ½ tax + ½ death · /vote");
            return;
        }
        RootMcVotePerk perk = ShadedServiceBridge.resolveVotePerk(plugin);
        if (perk != null && perk.active(player.getUniqueId())) {
            long expires = perk.expiresAtEpochMs(player.getUniqueId());
            String left = expires <= 0
                    ? "active"
                    : formatLeft(Duration.between(Instant.now(), Instant.ofEpochMilli(expires)));
            double halved = treasury.effectiveTransactionTaxRate() * perk.taxMultiplier(player.getUniqueId());
            ChatUi.entry(
                    sender,
                    "Vote perk",
                    "½ tax · your rate " + taxPct(halved) + "% · " + left + " left",
                    "ok");
            return;
        }
        int have = perk == null ? 0 : Math.max(0, perk.votesInWindow(player.getUniqueId()));
        int need = perk == null ? 5 : Math.max(0, perk.requiredVotes() - have);
        ChatUi.entry(
                sender,
                "Vote perk",
                have + "/" + (perk == null ? 5 : perk.requiredVotes())
                        + " votes this window · " + need + " more for ½ tax · /vote",
                "open");
    }

    private static String formatLeft(Duration remaining) {
        if (remaining == null || remaining.isNegative() || remaining.isZero()) {
            return "now";
        }
        long hours = remaining.toHours();
        long minutes = remaining.toMinutes() % 60L;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return Math.max(1, minutes) + "m";
    }

    private static String taxPct(double rate) {
        double pct = rate * 100.0;
        if (Math.abs(pct - Math.round(pct)) < 0.001) {
            return String.format(Locale.US, "%.0f", pct);
        }
        if (pct < 1) {
            return String.format(Locale.US, "%.2f", pct);
        }
        return String.format(Locale.US, "%.1f", pct);
    }
}
