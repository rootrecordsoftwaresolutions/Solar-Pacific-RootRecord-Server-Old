package com.rootrecord.minecraft.rootupkeep.schedule;

import com.rootrecord.minecraft.rootupkeep.RootUpkeepPlugin;
import com.rootrecord.minecraft.rootupkeep.config.UpkeepConfig;
import com.rootrecord.minecraft.rootupkeep.data.UpkeepStateStore;
import com.rootrecord.minecraft.rootupkeep.service.InactivityTaxService;
import com.rootrecord.minecraft.rootupkeep.util.Messages;
import org.bukkit.Bukkit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class InactivityTaxScheduler {

    /** Cap real-day catch-up after downtime so wallets are not drained in one burst. */
    private static final int MAX_CATCH_UP_DAYS = 7;

    private final RootUpkeepPlugin plugin;
    private final UpkeepStateStore state;
    private final AtomicBoolean running = new AtomicBoolean();
    private int taskId = -1;

    public InactivityTaxScheduler(RootUpkeepPlugin plugin, UpkeepStateStore state) {
        this.plugin = plugin;
        this.state = state;
    }

    public void start() {
        stop();
        if (!plugin.config().enabled()) {
            return;
        }
        long pollTicks = Math.max(15L, plugin.config().pollSeconds()) * 20L;
        taskId = Bukkit.getScheduler()
                .runTaskTimer(plugin.host(), () -> tryRunDueRealDays(null), 20L * 60L, pollTicks)
                .getTaskId();
        plugin.getLogger().info(
                "Inactivity tax once per real day (HST hour " + plugin.config().scheduleHourHst() + ").");
    }

    public void stop() {
        if (taskId >= 0) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    public void runNow(boolean manual) {
        runTaxCycle(manual, -1L, null);
    }

    /**
     * Minecraft-day rollover is only a wakeup. Tax applies at most once per real HST calendar day
     * (not once per ~20-minute MC day).
     */
    public void processMcDayRollover(long firstCompletedDay, long currentMcDayId, Runnable onComplete) {
        tryRunDueRealDays(onComplete);
    }

    private void tryRunDueRealDays(Runnable onComplete) {
        if (!plugin.config().enabled()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        LocalDateTime now = LocalDateTime.now(UpkeepConfig.HST);
        long today = now.toLocalDate().toEpochDay();
        boolean pastHour = now.getHour() >= plugin.config().scheduleHourHst();
        long lastAllowed = pastHour ? today : today - 1L;
        long last = state.lastRunEpochDay();

        long firstDue;
        if (last < 0L) {
            if (!pastHour) {
                if (onComplete != null) {
                    onComplete.run();
                }
                return;
            }
            firstDue = today;
        } else if (last >= lastAllowed) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        } else {
            firstDue = last + 1L;
            long span = lastAllowed - firstDue + 1L;
            if (span > MAX_CATCH_UP_DAYS) {
                firstDue = lastAllowed - MAX_CATCH_UP_DAYS + 1L;
            }
        }
        processRealDay(firstDue, lastAllowed, onComplete);
    }

    private void processRealDay(long epochDay, long lastAllowed, Runnable onComplete) {
        if (epochDay > lastAllowed) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        runTaxCycle(false, epochDay, () -> processRealDay(epochDay + 1L, lastAllowed, onComplete));
    }

    private void runTaxCycle(boolean manual, long epochDay, Runnable onComplete) {
        if (!running.compareAndSet(false, true)) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        InactivityTaxService service = plugin.taxService();
        if (service == null) {
            running.set(false);
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        // Collect + Vault/MySQL withdraws stay off the server thread — remote DB latency
        // previously watchdog-stalled TPS when apply() ran on the main thread.
        Bukkit.getScheduler().runTaskAsynchronously(plugin.host(), () -> {
            int applied = 0;
            try {
                List<InactivityTaxService.TaxResult> due = service.collectDueTaxes();
                for (InactivityTaxService.TaxResult result : due) {
                    if (service.apply(result)) {
                        applied++;
                    }
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Inactivity tax failed: " + ex.getMessage());
            }
            final int appliedFinal = applied;
            Bukkit.getScheduler().runTask(plugin.host(), () -> {
                try {
                    if (manual) {
                        Messages.broadcast(service.formatRunComplete(appliedFinal));
                    } else if (appliedFinal > 0) {
                        plugin.getLogger().info(
                                "Real day "
                                        + LocalDate.ofEpochDay(epochDay)
                                        + " (HST) inactivity tax applied "
                                        + appliedFinal
                                        + " debit(s).");
                    }
                    if (!manual && epochDay >= 0L) {
                        state.markRun(epochDay);
                    }
                } finally {
                    running.set(false);
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            });
        });
    }
}
