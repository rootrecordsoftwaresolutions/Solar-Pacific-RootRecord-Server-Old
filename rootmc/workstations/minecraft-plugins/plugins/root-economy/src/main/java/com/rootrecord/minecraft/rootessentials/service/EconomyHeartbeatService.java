package com.rootrecord.minecraft.rootessentials.service;

import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.common.RootMcLoanService;
import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import com.rootrecord.minecraft.rootessentials.web.RootMcEconomyWeb;
import com.rootrecord.minecraft.rootessentials.towny.TownyEconomyAccounts;
import com.rootrecord.minecraft.rootessentials.towny.TownyPlayerAccess;
import com.rootrecord.minecraft.rootstat.RootStatBridge;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/** Personal economy snapshot broadcast on Minecraft day rollover (replaces Towny "new day" ping). */
public final class EconomyHeartbeatService {

    private static final long DEDUPE_MS = 3 * 60 * 1000L;

    private final RootEconomyPlugin plugin;
    private volatile long lastBatchAtMs;

    public EconomyHeartbeatService(RootEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void onNewDay(Collection<? extends Player> players) {
        long now = System.currentTimeMillis();
        if (now - lastBatchAtMs < DEDUPE_MS) {
            return;
        }
        lastBatchAtMs = now;
        for (Player player : players) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            sendTo(player, true);
        }
    }

    /** Wallet + loan + bonds + town bank â€” used by /bal and day heartbeat. */
    public void sendTo(Player player) {
        sendTo(player, false);
    }

    private void sendTo(Player player, boolean heartbeatTitle) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        String townName = TownyPlayerAccess.townName(player).orElse(null);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Snapshot snapshot = collect(uuid, name, townName);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                send(player, snapshot, heartbeatTitle);
            });
        });
    }

    private Snapshot collect(UUID uuid, String name, String townName) {
        double balance = 0;
        try {
            balance = plugin.balance(uuid, name);
        } catch (Exception ignored) {
            // balance stays 0
        }
        Optional<RootMcLoanService.LoanBalanceSummary> loan = plugin.loanSummary(uuid);
        BondSnapshot personalBonds = resolveBonds(uuid);
        String voteLine = fetchVoteLine(uuid);
        TownBondSnapshot townBonds = resolveTownBonds(townName);
        return new Snapshot(balance, voteLine, loan.orElse(null), personalBonds, townBonds);
    }

    private TownBondSnapshot resolveTownBonds(String townName) {
        if (townName == null || townName.isBlank()) {
            return TownBondSnapshot.NONE;
        }
        return TownyEconomyAccounts.townBankByName(townName)
                .map(account -> new TownBondSnapshot(
                        townName,
                        plugin.balance(account.uuid(), account.username())))
                .orElse(TownBondSnapshot.NONE);
    }

    private void send(Player player, Snapshot snapshot, boolean heartbeatTitle) {
        ChatUi.banner(player, heartbeatTitle ? "Economy heartbeat" : "Balances");
        ChatUi.gold(player, "Wallet", plugin.money(snapshot.balance()), "G");
        ChatUi.row(player, "Vote", votePlain(snapshot.voteLine()));
        ChatUi.row(player, "Loan", loanPlain(snapshot.loan()));
        ChatUi.gold(player, "Total bonds", plugin.money(snapshot.personalBonds().principalG()), "G");
        if (snapshot.townBonds().townName() != null && !snapshot.townBonds().townName().isBlank()) {
            ChatUi.gold(player, "Town bonds", plugin.money(snapshot.townBonds().balanceG()), "G");
        }
        if (snapshot.personalBonds().principalG() > 0.01
                || snapshot.personalBonds().activeBonds() > 0
                || snapshot.townBonds().balanceG() > 0.01) {
            ChatUi.links(player, "Bonds", RootMcEconomyWeb.bonds());
        }
    }

    private static String votePlain(String voteLine) {
        return voteLine == null || voteLine.isBlank() ? "unavailable" : voteLine;
    }

    private String loanPlain(RootMcLoanService.LoanBalanceSummary loan) {
        if (loan == null || loan.owed() <= 0.01) {
            return "none";
        }
        return plugin.money(loan.owed()) + " G owed";
    }

    private String fetchVoteLine(UUID uuid) {
        Plugin rootmc = Bukkit.getPluginManager().getPlugin("RootMC");
        if (!(rootmc instanceof RootStatBridge bridge) || !rootmc.isEnabled()) {
            return "unavailable";
        }
        try {
            long votes =
                    new com.rootrecord.minecraft.rootstat.governance.LocalGovernancePowerService(bridge)
                            .voteCount(uuid);
            return votes + " EC shards";
        } catch (Exception ex) {
            if (com.rootrecord.minecraft.common.config.RootMcApiBases.looksLikeThrottleMessage(
                    ex.getMessage())) {
                return "cloud limited";
            }
            return "unavailable";
        }
    }

    private BondSnapshot resolveBonds(UUID uuid) {
        var feature = plugin.bondsFeature();
        if (feature == null) {
            return BondSnapshot.EMPTY;
        }
        try {
            var summary = feature.bondHeartbeatSummary(uuid);
            if (summary == null) {
                return BondSnapshot.EMPTY;
            }
            return new BondSnapshot(
                    summary.activeBonds(),
                    summary.principalG(),
                    summary.uncollectedG(),
                    summary.lifetimeEarnedG());
        } catch (Exception ex) {
            return BondSnapshot.EMPTY;
        }
    }

    private record Snapshot(
            double balance,
            String voteLine,
            RootMcLoanService.LoanBalanceSummary loan,
            BondSnapshot personalBonds,
            TownBondSnapshot townBonds) {}

    private record TownBondSnapshot(String townName, double balanceG) {
        static final TownBondSnapshot NONE = new TownBondSnapshot(null, 0);
    }

    private record BondSnapshot(int activeBonds, double principalG, double uncollectedG, double lifetimeEarnedG) {
        static final BondSnapshot EMPTY = new BondSnapshot(0, 0, 0, 0);
    }
}
