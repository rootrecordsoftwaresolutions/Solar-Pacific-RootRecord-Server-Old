package com.rootrecord.minecraft.rootessentials.treasury;

import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import com.rootrecord.minecraft.rootessentials.web.RootMcEconomyWeb;
import org.bukkit.command.CommandSender;

/** Essentials-only Gold Notes vs /mint â€” catalog lines, not a stats essay. */
public final class EconomyNoteSupplyDisplay {

    private EconomyNoteSupplyDisplay() {}

    public static void sendHeadline(
            RootEconomyPlugin plugin,
            CommandSender sender,
            EconomyBaseline.NoteSupplySnapshot supply,
            String title,
            boolean includeLinks) {
        sendHeadline(plugin, sender, supply, null, title, includeLinks);
    }

    /**
     * Reserve line is mint backing − circulating Notes (can be negative).
     * {@code ledgerReserveG}, when set, is the treasury ledger (opening + net), shown separately.
     */
    public static void sendHeadline(
            RootEconomyPlugin plugin,
            CommandSender sender,
            EconomyBaseline.NoteSupplySnapshot supply,
            Double ledgerReserveG,
            String title,
            boolean includeLinks) {
        ChatUi.banner(sender, stripLegacy(title));

        String cur = plugin.currency();
        ChatUi.gold(sender, "Notes", money(plugin, supply.totalNotesG()), cur);
        ChatUi.gold(sender, "Minted", money(plugin, supply.goldMinedG()), cur);

        if (supply.overIssued()) {
            ChatUi.entry(sender, "Backing", "+" + money(plugin, supply.overIssueG()) + " " + cur, "alert");
        } else if (supply.backingPct() != null) {
            ChatUi.entry(sender, "Backing", pct(supply.backingPct()) + "%", "ok");
        } else {
            ChatUi.entry(sender, "Backing", "fully backed", "ok");
        }

        sendTaxLine(plugin, sender, plugin.treasury());
        sendClaimTaxLine(sender);

        ChatUi.gold(sender, "Reserve", money(plugin, supply.reserveNotesG()), cur);
        if (ledgerReserveG != null) {
            ChatUi.gold(sender, "Ledger", money(plugin, ledgerReserveG), cur);
        }

        if (sender instanceof org.bukkit.entity.Player player) {
            sendPlayerBondLines(plugin, player);
        }

        if (includeLinks) {
            ChatUi.links(
                    sender,
                    "Economy", RootMcEconomyWeb.economy(),
                    "Reserve", RootMcEconomyWeb.reserve(),
                    "Constitution", RootMcEconomyWeb.constitution());
        }
    }

    /** Peg, backing, total gold, and live mint/transaction tax â€” shared by /mint and /reserve. */
    public static void sendMintCatalog(
            RootEconomyPlugin plugin,
            CommandSender sender,
            EconomyBaseline.NoteSupplySnapshot supply) {
        if (supply != null) {
            sendBackingLine(sender, supply);
        }
        ChatUi.row(sender, "Peg", "nugget 1/9 | ingot 1 | block 9");
        sendTaxLine(plugin, sender, plugin.treasury());
        sendClaimTaxLine(sender);
    }

    /** @deprecated Prefer {@link #sendMintCatalog(RootEconomyPlugin, CommandSender, EconomyBaseline.NoteSupplySnapshot)}. */
    public static void sendMintCatalog(RootEconomyPlugin plugin, CommandSender sender) {
        sendMintCatalog(plugin, sender, null);
    }

    /** Player Total bonds / Town bonds â€” shared by /economy, /reserve. */
    public static void sendPlayerBondLines(RootEconomyPlugin plugin, org.bukkit.entity.Player player) {
        double totalBonds = 0;
        var feature = plugin.bondsFeature();
        if (feature != null) {
            try {
                var summary = feature.bondHeartbeatSummary(player.getUniqueId());
                if (summary != null) {
                    totalBonds = summary.principalG();
                }
            } catch (Exception ignored) {
                // leave 0
            }
        }
        if (totalBonds > 0) {
            ChatUi.gold(player, "Total bonds", money(plugin, totalBonds), "G");
        }
    }

    /** @deprecated No Towny tax. Use {@link #sendClaimTaxLine(CommandSender)}. */
    public static void sendTownTaxLine(RootEconomyPlugin plugin, CommandSender sender) {
        sendClaimTaxLine(sender);
    }

    public static void sendClaimTaxLine(CommandSender sender) {
        ChatUi.entry(sender, "Claims", "first plot free · later plots + expansions use reserve tax");
    }

    public static void sendBackingLine(CommandSender sender, EconomyBaseline.NoteSupplySnapshot supply) {
        if (supply == null) {
            return;
        }
        if (supply.backingPct() != null) {
            String body = pct(supply.backingPct()) + "%";
            if (supply.overIssued()) {
                ChatUi.entry(sender, "Backing", body, "alert");
            } else {
                ChatUi.entry(sender, "Backing", body, "ok");
            }
        } else {
            ChatUi.entry(sender, "Backing", "fully backed", "ok");
        }
    }

    public static void sendTaxLine(RootEconomyPlugin plugin, CommandSender sender, TreasuryManager treasury) {
        if (treasury == null || !treasury.transactionTaxEnabled()) {
            return;
        }
        double effective = treasury.effectiveTransactionTaxRate();
        if (treasury.dynamicTaxEnabled()) {
            ChatUi.entry(sender, "Tax", taxPct(effective) + "% -> Reserve");
        } else {
            ChatUi.entry(sender, "Tax", taxPct(treasury.transactionTaxRate()) + "% -> Reserve");
        }
        double power = treasury.powerTaxRate();
        if (power >= 0.0005d) {
            ChatUi.entry(sender, "Env tax", taxPct(power) + "% solar offline", "alert");
        }
    }

    /** Mint panel: reserve + 0.05% min + offline env. No town tax. */
    public static void sendMintTaxLine(RootEconomyPlugin plugin, CommandSender sender, TreasuryManager treasury) {
        if (treasury == null || !treasury.transactionTaxEnabled()) {
            return;
        }
        double rate = treasury.effectiveTransactionTaxRate();
        ChatUi.entry(sender, "Tax", taxPct(rate) + "% -> Reserve");
        double power = treasury.powerTaxRate();
        if (power >= 0.0005d) {
            ChatUi.entry(sender, "Env tax", taxPct(power) + "% solar offline", "alert");
        }
    }

    private static String money(RootEconomyPlugin plugin, double value) {
        return plugin.money(value);
    }

    private static String pct(double value) {
        return String.format(java.util.Locale.US, "%.1f", value);
    }

    private static String taxPct(double rate) {
        double value = rate * 100.0;
        if (Math.abs(value - Math.round(value)) < 0.001) {
            return String.format(java.util.Locale.US, "%.0f", value);
        }
        if (value < 1) {
            return String.format(java.util.Locale.US, "%.2f", value);
        }
        return String.format(java.util.Locale.US, "%.1f", value);
    }

    private static String stripLegacy(String title) {
        if (title == null || title.isBlank()) {
            return "Economy";
        }
        return title.replaceAll("(?i)&[0-9a-fk-or]", "").trim();
    }
}
