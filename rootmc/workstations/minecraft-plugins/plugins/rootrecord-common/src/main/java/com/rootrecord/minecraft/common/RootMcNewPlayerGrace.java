package com.rootrecord.minecraft.common;

import java.util.UUID;

/** Whether a player is still in the configured new-player grace window (shaded bridge). */
public interface RootMcNewPlayerGrace {

    boolean inGracePeriod(UUID playerId);

    long graceRemainingMs(UUID playerId);

    /** When true, death fees are skipped during the grace window (see root-essentials new-player.skip-death-tax). */
    boolean exemptFromDeathTax(UUID playerId);
}
