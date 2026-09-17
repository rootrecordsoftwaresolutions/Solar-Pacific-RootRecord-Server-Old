package com.rootrecord.minecraft.roottimes.api;

import com.rootrecord.minecraft.common.McDayClock;
import com.rootrecord.minecraft.roottimes.RootTimesPlugin;
import com.rootrecord.minecraft.roottimes.clock.ClockService;

import java.util.UUID;

public final class RootTimesApiImpl implements RootTimesApi {

    private final RootTimesPlugin plugin;

    public RootTimesApiImpl(RootTimesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public long currentDayId() {
        return McDayClock.currentDayId();
    }

    @Override
    public long timeOfDayTicks() {
        return McDayClock.timeOfDayTicks(McDayClock.now());
    }

    @Override
    public long fullTimeTarget() {
        return McDayClock.fullTime();
    }

    @Override
    public McDaySnapshot snapshot() {
        return ClockService.snapshot();
    }

    @Override
    public boolean isAfk(UUID uuid) {
        return plugin.afkService() != null && plugin.afkService().isAfk(uuid);
    }

    @Override
    public long afkSecondsToday(UUID uuid) {
        return plugin.afkService() == null ? 0L : plugin.afkService().afkSecondsToday(uuid);
    }
}
