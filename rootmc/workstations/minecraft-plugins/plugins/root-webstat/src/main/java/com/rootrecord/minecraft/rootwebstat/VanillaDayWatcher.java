package com.rootrecord.minecraft.rootwebstat;

import com.rootrecord.minecraft.common.McDayClock;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

/**
 * Fires once when the overworld crosses a vanilla MC day boundary
 * ({@code fullTime / 24000}). All RootMC hosts must run on vanilla day ticks
 * so snapshots share a comparable {@code mc_day_id}.
 */
public final class VanillaDayWatcher {

    private final RootWebstatPlugin plugin;
    private long lastDayId = -1L;
    private BukkitTask task;

    public VanillaDayWatcher(RootWebstatPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        lastDayId = currentDayId();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 100L);
        plugin.getLogger().info(
                "Webstat day watcher active (vanilla "
                        + McDayClock.TICKS_PER_DAY
                        + " ticks/day"
                        + (plugin.config().dayWorld().isBlank() ? "" : ", world " + plugin.config().dayWorld())
                        + ").");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        long current = currentDayId();
        if (lastDayId < 0) {
            lastDayId = current;
            return;
        }
        if (current <= lastDayId) {
            return;
        }
        long completedDay = lastDayId;
        lastDayId = current;
        World world = resolveDayWorld();
        long fullTime = world != null ? world.getFullTime() : completedDay * McDayClock.TICKS_PER_DAY;
        plugin.onVanillaDayRollover(completedDay, current, fullTime);
    }

    public long currentDayId() {
        World world = resolveDayWorld();
        if (world == null) {
            return 0L;
        }
        return world.getFullTime() / McDayClock.TICKS_PER_DAY;
    }

    public long currentFullTime() {
        World world = resolveDayWorld();
        return world != null ? world.getFullTime() : 0L;
    }

    private World resolveDayWorld() {
        String named = plugin.config().dayWorld();
        if (named != null && !named.isBlank()) {
            World world = Bukkit.getWorld(named);
            if (world != null) {
                return world;
            }
        }
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                return world;
            }
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
    }
}
