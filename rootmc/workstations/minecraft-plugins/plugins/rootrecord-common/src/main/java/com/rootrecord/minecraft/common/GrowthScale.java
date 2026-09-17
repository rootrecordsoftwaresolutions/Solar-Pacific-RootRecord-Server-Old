package com.rootrecord.minecraft.common;

import java.util.concurrent.ThreadLocalRandom;

/** Probabilistic scaling when the MC day is longer than vanilla. */
public final class GrowthScale {

    private GrowthScale() {}

    /** Scale an integer growth delta down (expected value = delta / scale). */
    public static int scaledDelta(int delta, double scale) {
        if (delta <= 0 || scale <= 1.0) {
            return delta;
        }
        int scaled = 0;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < delta; i++) {
            if (random.nextDouble() < 1.0 / scale) {
                scaled++;
            }
        }
        return scaled;
    }

    /** Whether a growth action should proceed (probability 1 / scale). */
    public static boolean passes(double scale) {
        if (scale <= 1.0) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble() < 1.0 / scale;
    }
}
