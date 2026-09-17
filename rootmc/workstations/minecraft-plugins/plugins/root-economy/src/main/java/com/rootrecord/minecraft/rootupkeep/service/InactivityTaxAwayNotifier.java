package com.rootrecord.minecraft.rootupkeep.service;

import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.rootupkeep.RootUpkeepPlugin;
import com.rootrecord.minecraft.rootupkeep.config.UpkeepConfig;
import com.rootrecord.minecraft.rootupkeep.data.InactivityTaxPendingStore;
import com.rootrecord.minecraft.rootupkeep.towny.TownyGroups;
import com.rootrecord.minecraft.rootupkeep.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Records while-away inactivity tax and delivers a summary when a player returns. */
public final class InactivityTaxAwayNotifier {

    private static final DecimalFormat MONEY = new DecimalFormat("0.##");

    private InactivityTaxAwayNotifier() {}

    public static void recordApplied(RootUpkeepPlugin plugin, InactivityTaxService.TaxResult result) {
        InactivityTaxPendingStore store = plugin.pendingStore();
        if (store == null || !plugin.config().mysqlConfigured() || result.amount() <= 0) {
            return;
        }
        double amount = GoldMoney.round(result.amount());
        Bukkit.getScheduler().runTaskAsynchronously(plugin.host(), () -> {
            try {
                switch (result.kind()) {
                    case PLAYER -> {
                        if (result.playerUuid() != null) {
                            store.addAccrual(result.playerUuid(), "player", "", amount);
                        }
                    }
                    case TOWN -> accrueGroup(store, TownyGroups.residentsOfTownName(result.displayName()), "town", result.displayName(), amount);
                    case NATION -> accrueGroup(store, TownyGroups.residentsOfNationName(result.displayName()), "nation", result.displayName(), amount);
                }
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Inactivity tax accrual failed: " + ex.getMessage(), ex);
            }
        });
    }

    private static void accrueGroup(
            InactivityTaxPendingStore store,
            Set<UUID> members,
            String kind,
            String groupName,
            double amount) throws Exception {
        if (members.isEmpty()) {
            return;
        }
        for (UUID member : members) {
            store.addAccrual(member, kind, groupName, amount);
        }
    }

    public static void deliverIfAway(RootUpkeepPlugin plugin, Player player, long previousLastPlayedMs) {
        if (player == null || !plugin.config().enabled()) {
            return;
        }
        InactivityTaxPendingStore store = plugin.pendingStore();
        if (store == null || !plugin.config().mysqlConfigured()) {
            return;
        }
        UpkeepConfig config = plugin.config();
        long inactiveDays = inactiveDays(previousLastPlayedMs);
        if (inactiveDays < config.graceDays()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin.host(), () -> {
            List<InactivityTaxPendingStore.PendingRow> rows;
            try {
                rows = store.takePending(uuid);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Inactivity tax summary read failed: " + ex.getMessage(), ex);
                return;
            }
            if (rows.isEmpty()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin.host(), () -> {
                Player online = Bukkit.getPlayer(uuid);
                if (online == null || !online.isOnline()) {
                    return;
                }
                sendSummary(online, config, inactiveDays, rows);
            });
        });
    }

    private static long inactiveDays(long previousLastPlayedMs) {
        if (previousLastPlayedMs <= 0) {
            return 0L;
        }
        return ChronoUnit.DAYS.between(Instant.ofEpochMilli(previousLastPlayedMs), Instant.now());
    }

    private static void sendSummary(
            Player player,
            UpkeepConfig config,
            long inactiveDays,
            List<InactivityTaxPendingStore.PendingRow> rows) {
        double personal = 0;
        double townTotal = 0;
        double nationTotal = 0;
        String townName = "";
        String nationName = "";
        for (InactivityTaxPendingStore.PendingRow row : rows) {
            switch (row.kind()) {
                case "player" -> personal += row.amountG();
                case "town" -> {
                    townTotal += row.amountG();
                    if (townName.isBlank()) {
                        townName = row.groupName();
                    }
                }
                case "nation" -> {
                    nationTotal += row.amountG();
                    if (nationName.isBlank()) {
                        nationName = row.groupName();
                    }
                }
                default -> {
                    // ignore
                }
            }
        }
        double total = personal + townTotal + nationTotal;
        if (total <= 0) {
            return;
        }
        Messages.send(player, config.returnSummaryHeader()
                .replace("{days}", String.valueOf(inactiveDays)));
        if (personal > 0) {
            Messages.send(player, config.returnSummaryPlayer()
                    .replace("{amount}", MONEY.format(GoldMoney.round(personal))));
        }
        if (townTotal > 0) {
            Messages.send(player, config.returnSummaryTown()
                    .replace("{town}", townName)
                    .replace("{amount}", MONEY.format(GoldMoney.round(townTotal))));
        }
        if (nationTotal > 0) {
            Messages.send(player, config.returnSummaryNation()
                    .replace("{nation}", nationName)
                    .replace("{amount}", MONEY.format(GoldMoney.round(nationTotal))));
        }
        Messages.send(player, config.returnSummaryTotal()
                .replace("{amount}", MONEY.format(GoldMoney.round(total))));
    }
}
