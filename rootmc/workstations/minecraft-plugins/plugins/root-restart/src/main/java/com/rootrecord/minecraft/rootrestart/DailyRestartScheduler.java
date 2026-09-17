package com.rootrecord.minecraft.rootrestart;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/** Starts the 5-minute daily countdown at 23:55 HST so restart lands on midnight HST. */
public final class DailyRestartScheduler {

    private static final int DAILY_LEAD_SECONDS = 300;

    private final RootRestartPlugin plugin;
    private BukkitTask pollTask;
    private LocalDate lastTriggeredDate;

    public DailyRestartScheduler(RootRestartPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.config().dailyEnabled()) {
            return;
        }
        pollTask = Bukkit.getScheduler().runTaskTimer(plugin, this::poll, 20L, 20L * 15L);
    }

    public void stop() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
    }

    private void poll() {
        if (!plugin.config().dailyEnabled()) {
            return;
        }
        if (plugin.countdown() != null && plugin.countdown().isRunning()) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(plugin.config().dailyZone());
        ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(plugin.config().dailyZone());
        long secondsUntilMidnight = java.time.Duration.between(now, midnight).getSeconds();
        if (secondsUntilMidnight > DAILY_LEAD_SECONDS || secondsUntilMidnight < DAILY_LEAD_SECONDS - 30L) {
            return;
        }
        LocalDate target = midnight.toLocalDate();
        if (target.equals(lastTriggeredDate)) {
            return;
        }
        lastTriggeredDate = target;
        plugin.getLogger().info("Daily restart countdown started (" + DAILY_LEAD_SECONDS + "s to midnight HST).");
        plugin.startCountdown(RestartCountdown.Kind.DAILY, "Daily schedule");
    }
}
