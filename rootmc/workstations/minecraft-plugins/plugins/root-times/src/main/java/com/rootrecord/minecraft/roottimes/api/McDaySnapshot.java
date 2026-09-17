package com.rootrecord.minecraft.roottimes.api;

/** Snapshot of the RootMC Minecraft day for APIs and welcome lines. */
public record McDaySnapshot(long dayId, long timeOfDayTicks, long fullTime, String phase, int lengthMinutes) {}
