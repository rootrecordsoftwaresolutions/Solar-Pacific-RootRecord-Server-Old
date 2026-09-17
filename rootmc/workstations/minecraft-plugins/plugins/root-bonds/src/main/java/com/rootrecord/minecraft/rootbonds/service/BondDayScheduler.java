package com.rootrecord.minecraft.rootbonds.service;

import com.rootrecord.minecraft.rootbonds.RootBondsPlugin;
import com.rootrecord.minecraft.rootbonds.config.BondsConfig;
import org.bukkit.scheduler.BukkitTask;

public final class BondDayScheduler {

    private final RootBondsPlugin plugin;
    private final BondIncomeService income;
    private final BondExpiryService expiry;
    private volatile boolean syncOnMcDay = true;
    private BukkitTask syncTask;
    private BukkitTask expiryTask;

    public BondDayScheduler(RootBondsPlugin plugin, BondIncomeService income, BondExpiryService expiry) {
        this.plugin = plugin;
        this.income = income;
        this.expiry = expiry;
    }

    public void start(BondsConfig config) {
        stop();
        syncOnMcDay = config.syncOnMcDay();
        income.catchUpMissedSettlements(() -> beginTracking(config));
    }

    private void beginTracking(BondsConfig config) {
        expiryTask = plugin.getServer().getScheduler().runTaskTimer(plugin, expiry::sweepExpiredCoupons, 200L, 1200L);
        if (!syncOnMcDay) {
            long syncTicks = Math.max(20L * 60L, config.syncIntervalMinutes() * 60L * 20L);
            syncTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                    plugin,
                    () -> plugin.cloudSync().syncSnapshot(false),
                    syncTicks,
                    syncTicks);
        }
    }

    public void stop() {
        if (expiryTask != null) {
            expiryTask.cancel();
            expiryTask = null;
        }
        if (syncTask != null) {
            syncTask.cancel();
            syncTask = null;
        }
    }

    public void processMcDayRollover(long firstCompletedDay, long currentMcDayId, Runnable onComplete) {
        if (firstCompletedDay >= currentMcDayId) {
            onComplete.run();
            return;
        }
        settleDayAt(firstCompletedDay, () -> {
            income.clearGovernmentSuspensions();
            if (firstCompletedDay + 1 < currentMcDayId) {
                processMcDayRollover(firstCompletedDay + 1, currentMcDayId, onComplete);
            } else {
                onComplete.run();
            }
        });
    }

    private void settleDayAt(long mcDayId, Runnable onComplete) {
        income.settleCompletedDay(mcDayId, onComplete);
    }
}
