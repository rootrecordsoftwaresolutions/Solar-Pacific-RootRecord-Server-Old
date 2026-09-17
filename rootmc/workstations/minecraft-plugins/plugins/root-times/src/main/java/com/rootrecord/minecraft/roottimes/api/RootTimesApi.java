package com.rootrecord.minecraft.roottimes.api;

import java.util.UUID;

/** Bukkit ServicesManager contract for Root-Times. */
public interface RootTimesApi {

    long currentDayId();

    long timeOfDayTicks();

    long fullTimeTarget();

    McDaySnapshot snapshot();

    boolean isAfk(UUID uuid);

    long afkSecondsToday(UUID uuid);
}
