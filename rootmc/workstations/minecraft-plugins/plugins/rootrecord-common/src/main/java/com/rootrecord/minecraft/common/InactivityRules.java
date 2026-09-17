package com.rootrecord.minecraft.common;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Shared grace window for Root-Upkeep tax and Root-Bonds coupon eligibility. */
public final class InactivityRules {

    private InactivityRules() {}

    /** True when {@code lastActive} is absent or idle at least {@code graceDays} full days. */
    public static boolean isPastGrace(Instant lastActive, int graceDays) {
        if (lastActive == null) {
            return true;
        }
        return ChronoUnit.DAYS.between(lastActive, Instant.now()) >= graceDays;
    }

    /** Mirrors Root-Upkeep: a tier exists once grace has elapsed. */
    public static boolean isSubjectToInactivityTax(Instant lastActive, int graceDays) {
        return isPastGrace(lastActive, graceDays);
    }
}
