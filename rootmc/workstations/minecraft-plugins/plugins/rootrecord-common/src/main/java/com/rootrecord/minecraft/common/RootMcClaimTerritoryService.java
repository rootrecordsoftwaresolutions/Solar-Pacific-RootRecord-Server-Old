package com.rootrecord.minecraft.common;

import java.util.UUID;

/**
 * Cross-plugin claim territory queries for RootClaims.
 * Territory is an unclaimed wilderness band outside the claim radius.
 */
public interface RootMcClaimTerritoryService {

    /** Claims and territories apply at this Y and above. Below is free-reign mining. */
    int MINING_FLOOR_Y = 0;

    default boolean isMiningFreeReign(int blockY) {
        return blockY < MINING_FLOOR_Y;
    }

    /** True when the block is inside a protected claim circle. */
    boolean isClaimed(String worldName, int blockX, int blockZ);

    default boolean isClaimed(String worldName, int blockX, int blockY, int blockZ) {
        return !isMiningFreeReign(blockY) && isClaimed(worldName, blockX, blockZ);
    }

    /**
     * True when the player is claim owner or trusted on a claim whose territory band
     * (claim radius -> radius + buffer) covers this wilderness block.
     */
    boolean isWildernessFeeExempt(UUID playerId, String worldName, int blockX, int blockZ);

    default boolean isWildernessFeeExempt(UUID playerId, String worldName, int blockX, int blockY, int blockZ) {
        return isMiningFreeReign(blockY) || isWildernessFeeExempt(playerId, worldName, blockX, blockZ);
    }

    /**
     * Credit a wilderness destroy fee to the claim bank whose territory covers this block.
     *
     * @return claim owner name when credited, otherwise {@code null} (caller should send to reserve)
     */
    String creditWildernessDestroyFee(
            String worldName, int blockX, int blockZ, double amountG, String payerName);

    default String creditWildernessDestroyFee(
            String worldName, int blockX, int blockY, int blockZ, double amountG, String payerName) {
        if (isMiningFreeReign(blockY)) {
            return null;
        }
        return creditWildernessDestroyFee(worldName, blockX, blockZ, amountG, payerName);
    }

    /** Configured outward territory buffer in blocks (from claim edge). */
    int territoryBufferBlocks();
}
