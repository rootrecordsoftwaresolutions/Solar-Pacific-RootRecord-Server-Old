package com.rootrecord.minecraft.rootgamble.task;

import com.rootrecord.minecraft.common.McDayClock;
import com.rootrecord.minecraft.rootgamble.RootGamblePlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

public final class LottoMcDayTask {

    private final RootGamblePlugin plugin;
    private BukkitTask task;

    public LottoMcDayTask(RootGamblePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        long ticks = plugin.config().lottoPollSeconds() * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, ticks);
        Bukkit.getScheduler().runTaskLater(plugin, this::tick, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (!plugin.config().enabled() || !plugin.store().ready()) {
            return;
        }
        long day = currentMcDayId();
        if (day < 0) {
            return;
        }
        long last = plugin.store().lastDrawnMcDayId();
        if (last < 0) {
            // First boot: seed cursor without drawing backlog
            plugin.store().setLastDrawnMcDayId(day);
            return;
        }
        if (day > last) {
            plugin.lotto().drawForDay(day);
        }
    }

    private long currentMcDayId() {
        if (McDayClock.enabled()) {
            return McDayClock.currentDayId();
        }
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        if (world == null) {
            return -1;
        }
        return world.getFullTime() / 24_000L;
    }
}
