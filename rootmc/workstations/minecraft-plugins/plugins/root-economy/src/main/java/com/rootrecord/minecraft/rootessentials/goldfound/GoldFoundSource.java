package com.rootrecord.minecraft.rootessentials.goldfound;

/** How physical gold entered the player's economy (before /mint). */
public enum GoldFoundSource {
    MINED_ORE,
    MINED_BLOCK,
    /** Furnace/blast furnace produced mint-peg gold that the input did not already fully cover. */
    SMELT,
    /** Piglin bartering output. */
    BARTER,
    /** Merchant / villager trade received gold. */
    TRADE,
    /** Structure / loot-table generation into the world. */
    LOOT_GENERATE,
    LOOT_CHEST,
    /** Entity death loot (credited at death). */
    LOOT_MOB,
    /** Fishing catches containing gold. */
    FISHING,
    /** Ground pickup of gold not already credited at break/death/barter. */
    PICKUP
}
