package com.rootrecord.minecraft.rootbonds.service;

import com.rootrecord.minecraft.common.McDayClock;
import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.common.RootMcBondIncomeService;
import com.rootrecord.minecraft.common.RootMcTreasuryIncomeListener;
import com.rootrecord.minecraft.common.TreasuryIncomeHub;
import com.rootrecord.minecraft.common.TreasuryLedgerType;
import com.rootrecord.minecraft.rootbonds.RootBondsPlugin;
import com.rootrecord.minecraft.rootbonds.config.BondsConfig;
import com.rootrecord.minecraft.rootbonds.data.BondsStore;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class BondIncomeService implements RootMcBondIncomeService, AutoCloseable {

    /** Outflows / non-income / closed-loop returns — do not feed the 25% bond coupon pool. */
    private static final Set<TreasuryLedgerType> EXCLUDED_TYPES = EnumSet.of(
            TreasuryLedgerType.GRANT,
            TreasuryLedgerType.DIVIDEND,
            TreasuryLedgerType.LOAN_DISBURSE,
            /** Returning lent capital — closed-loop; does not feed coupons. Interest is eligible. */
            TreasuryLedgerType.LOAN_PRINCIPAL,
            TreasuryLedgerType.BOND_COUPON,
            TreasuryLedgerType.BOND_REDEEM,
            TreasuryLedgerType.NOTE_BURN,
            TreasuryLedgerType.OPENING,
            TreasuryLedgerType.DONATION);

    private final RootBondsPlugin plugin;
    private final RootMcTreasuryIncomeListener listener = this::onIncome;
    private volatile BondsConfig config;
    private volatile BondsStore store;
    private volatile BondCloudSync cloudSync;
    private volatile BondPrincipalResolver principals;
    private volatile BondService bonds;
    private final Set<UUID> suspendedGovernments = ConcurrentHashMap.newKeySet();

    public BondIncomeService(RootBondsPlugin plugin) {
        this.plugin = plugin;
        TreasuryIncomeHub.register(listener);
    }

    public void reload(
            BondsConfig config,
            BondsStore store,
            BondCloudSync cloudSync,
            BondPrincipalResolver principals,
            BondService bonds) {
        this.config = config;
        this.store = store;
        this.cloudSync = cloudSync;
        this.principals = principals;
        this.bonds = bonds;
    }

    public World resolveDayWorld() {
        BondsConfig active = config;
        if (active == null) {
            return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        }
        if (!active.dayWorld().isBlank()) {
            World named = Bukkit.getWorld(active.dayWorld());
            if (named != null) {
                return named;
            }
        }
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                return world;
            }
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
    }

    public long currentMcDayId() {
        if (McDayClock.enabled()) {
            return McDayClock.currentDayId();
        }
        BondsConfig active = config;
        World world = resolveDayWorld();
        if (world == null || active == null) {
            return 0L;
        }
        return world.getFullTime() / active.mcDayTicks();
    }

    private void onIncome(double amount, TreasuryLedgerType type, UUID sourceUuid, String details) {
        BondsConfig activeConfig = config;
        if (activeConfig == null || !activeConfig.enabled() || amount <= 0 || type == null || EXCLUDED_TYPES.contains(type)) {
            return;
        }
        long mcDayId = currentMcDayId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> accumulate(mcDayId, amount));
    }

    @Override
    public void recordTreasuryIncome(double amount, String type, UUID sourceUuid, String details) {
        TreasuryLedgerType ledgerType;
        try {
            ledgerType = TreasuryLedgerType.valueOf(type == null ? "" : type.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return;
        }
        onIncome(amount, ledgerType, sourceUuid, details);
    }

    @Override
    public void suspendGovernmentBondCoupons(UUID accountUuid) {
        if (accountUuid != null) {
            suspendedGovernments.add(accountUuid);
        }
    }

    public boolean isGovernmentSuspended(UUID accountUuid) {
        return accountUuid != null && suspendedGovernments.contains(accountUuid);
    }

    public void clearGovernmentSuspensions() {
        suspendedGovernments.clear();
    }

    private void accumulate(long mcDayId, double amount) {
        BondsStore activeStore = store;
        if (activeStore == null || mcDayId < 0) {
            return;
        }
        try {
            activeStore.addDayInflow(mcDayId, GoldMoney.round(amount));
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Bond day inflow failed: " + ex.getMessage(), ex);
        }
    }

    public void catchUpMissedSettlements() {
        catchUpMissedSettlements(null);
    }

    public void catchUpMissedSettlements(Runnable onComplete) {
        BondsStore activeStore = store;
        if (activeStore == null) {
            finish(onComplete);
            return;
        }
        long current = currentMcDayId();
        if (current <= 0) {
            plugin.getLogger().warning("Bond catch-up skipped — no day world.");
            finish(onComplete);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long lastSettled = activeStore.maxSettledMcDayId();
                int maxCatchUp = config != null ? Math.max(1, config.catchUpMaxMcDays()) : 96;
                long earliestPay = Math.max(lastSettled + 1, current - maxCatchUp);
                if (lastSettled + 1 < earliestPay) {
                    int skipped = activeStore.markDaysSkipped(lastSettled + 1, earliestPay - 1);
                    plugin.getLogger().warning(
                            "Bond catch-up: skipped "
                                    + (earliestPay - lastSettled - 1)
                                    + " MC day(s) "
                                    + (lastSettled + 1)
                                    + ".."
                                    + (earliestPay - 1)
                                    + " (cap "
                                    + maxCatchUp
                                    + "; inserted "
                                    + skipped
                                    + " skip rows). Paying last "
                                    + maxCatchUp
                                    + " only.");
                }
                List<Long> pending = new ArrayList<>();
                for (long day = earliestPay; day < current; day++) {
                    if (!activeStore.isDaySettled(day)) {
                        pending.add(day);
                    }
                }
                if (pending.isEmpty()) {
                    plugin.getLogger().info("Bond catch-up: nothing to settle (current MC day " + current
                            + ", last settled " + lastSettled + ").");
                    syncSnapshot(true);
                    finish(onComplete);
                    return;
                }
                plugin.getLogger().info(
                        "Bond catch-up: settling "
                                + pending.size()
                                + " MC day(s) "
                                + pending.get(0)
                                + ".."
                                + pending.get(pending.size() - 1)
                                + " (current day "
                                + current
                                + ").");
                settlePendingDays(pending, 0, onComplete);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Bond catch-up failed: " + ex.getMessage(), ex);
                finish(onComplete);
            }
        });
    }

    private void settlePendingDays(List<Long> pending, int index, Runnable onComplete) {
        if (index >= pending.size()) {
            syncSnapshot(true);
            finish(onComplete);
            return;
        }
        long day = pending.get(index);
        settleCompletedDay(day, () -> settlePendingDays(pending, index + 1, onComplete));
    }

    public void settleCompletedDay(long mcDayId) {
        settleCompletedDay(mcDayId, null);
    }

    public void settleCompletedDay(long mcDayId, Runnable onComplete) {
        BondsStore activeStore = store;
        BondsConfig activeConfig = config;
        BondPrincipalResolver resolver = principals;
        BondService bondService = bonds;
        if (activeStore == null || activeConfig == null || !activeConfig.enabled() || resolver == null) {
            finish(onComplete);
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            java.util.List<BondPrincipalResolver.Holder> holders;
            try {
                holders = resolver.resolveActivePrincipals();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Bond principal resolve failed: " + ex.getMessage(), ex);
                finish(onComplete);
                return;
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    if (activeStore.isDaySettled(mcDayId)) {
                        plugin.getLogger().info("Bond MC day " + mcDayId + " already settled.");
                        finish(onComplete);
                        return;
                    }
                    double gross = activeStore.dayGrossInflow(mcDayId);
                    boolean payoutsPaused = BondReserveGate.payoutsPaused(plugin);
                    Optional<BondsStore.SettlementResult> result = activeStore.settleMcDay(
                            mcDayId,
                            activeConfig.incomeShare(),
                            activeConfig.claimExpiryHours(),
                            activeConfig.autoPayEarningsWallet(),
                            holders,
                            !payoutsPaused);
                    if (result.isEmpty()) {
                        plugin.getLogger().warning("Bond MC day " + mcDayId + " settlement returned empty.");
                        finish(onComplete);
                        return;
                    }
                    BondsStore.SettlementResult settled = result.get();
                    double pool = settled.settlement().bondPoolG();
                    String pauseNote = payoutsPaused ? " (reserve ledger below 0 — payouts paused)" : "";
                    plugin.getLogger().info("Bond MC day " + mcDayId + " settled — gross inflow "
                            + GoldMoney.round(gross) + " G, pool " + GoldMoney.round(pool) + " G, holders "
                            + holders.size() + pauseNote + ".");
                    List<BondsStore.GovernmentPayoutRow> gov = settled.governmentPayouts();
                    List<BondsStore.PlayerWalletPayoutRow> players = settled.playerWalletPayouts();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (bondService != null) {
                            for (BondsStore.PlayerWalletPayoutRow payout : players) {
                                bondService.payPlayerEarning(
                                        payout.ownerUuid(),
                                        payout.ownerName(),
                                        payout.amountG(),
                                        "mc-day:" + mcDayId);
                            }
                            for (BondsStore.GovernmentPayoutRow payout : gov) {
                                if (isGovernmentSuspended(payout.accountUuid())) {
                                    continue;
                                }
                                bondService.payGovernmentCoupon(
                                        payout.accountUuid(),
                                        payout.accountName(),
                                        payout.amountG(),
                                        payout.kind().name().toLowerCase() + ":" + payout.displayName());
                            }
                        }
                        plugin.menuRegistry().refreshOpenMenus();
                        finish(onComplete);
                    });
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "Bond day settlement failed for day " + mcDayId + ": " + ex.getMessage(), ex);
                    finish(onComplete);
                }
            });
        });
    }

    private void syncSnapshot(boolean logResult) {
        BondCloudSync sync = cloudSync;
        if (sync != null) {
            sync.syncSnapshot(logResult);
        }
    }

    private void finish(Runnable onComplete) {
        if (onComplete == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, onComplete);
    }

    @Override
    public void close() {
        TreasuryIncomeHub.unregister(listener);
    }
}
