package com.rootrecord.minecraft.roottimes.clock;

import com.rootrecord.minecraft.common.McDayClock;
import com.rootrecord.minecraft.roottimes.api.McDaySnapshot;
import com.rootrecord.minecraft.roottimes.config.TimesConfig;

import java.time.ZonedDateTime;

public final class ClockService {

    private ClockService() {}

    public static void apply(TimesConfig config) {
        McDayClock.configure(
                config.dayEnabled(),
                config.dayTimezone(),
                config.lengthMinutes(),
                config.middayMinute(),
                config.midnightMinute(),
                0L);
        // Resolve auto/now after zone+length are live so absoluteDayId() is correct.
        long base = DayIdEpoch.resolve(config.plugin(), config.dayIdBaseRaw());
        McDayClock.setDayIdBase(base);
        if (base > 0L) {
            config.plugin()
                    .getLogger()
                    .info(
                            "MC day id base="
                                    + base
                                    + " → local day "
                                    + McDayClock.currentDayId()
                                    + " (absolute "
                                    + McDayClock.absoluteDayId()
                                    + ")");
        }
    }

    public static McDaySnapshot snapshot() {
        ZonedDateTime now = McDayClock.now();
        long tod = McDayClock.timeOfDayTicks(now);
        return new McDaySnapshot(
                McDayClock.currentDayId(now),
                tod,
                McDayClock.fullTime(now),
                phaseLabel(tod),
                McDayClock.lengthMinutes());
    }

    public static String phaseLabel(long todTicks) {
        long t = Math.floorMod(todTicks, McDayClock.TICKS_PER_DAY);
        if (t >= 23_000 || t < 1_000) {
            return "Dawn";
        }
        if (t < 5_500) {
            return "Day";
        }
        if (t < 6_500) {
            return "Noon";
        }
        if (t < 12_000) {
            return "Day";
        }
        if (t < 13_000) {
            return "Dusk";
        }
        if (t < 17_500) {
            return "Night";
        }
        if (t < 18_500) {
            return "Midnight";
        }
        return "Night";
    }
}
