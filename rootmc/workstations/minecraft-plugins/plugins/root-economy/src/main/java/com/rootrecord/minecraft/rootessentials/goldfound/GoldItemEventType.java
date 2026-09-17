package com.rootrecord.minecraft.rootessentials.goldfound;

/** Lifecycle event for a gold-related item stack. */
public enum GoldItemEventType {
    /** Physical gold entered the player's possession (mine, loot, pickup). */
    ACQUIRED,
    /** Physical gold items converted to wallet G via /mint. */
    MINT_TO_WALLET,
    /** Wallet G converted to physical gold items via /mint gold. */
    MINT_TO_ITEMS
}
