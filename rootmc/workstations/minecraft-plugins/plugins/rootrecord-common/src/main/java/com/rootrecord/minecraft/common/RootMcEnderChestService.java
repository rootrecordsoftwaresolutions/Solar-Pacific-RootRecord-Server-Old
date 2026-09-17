package com.rootrecord.minecraft.common;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Per-server 54-slot (double-chest) ender inventory. Isolated per host / server_id.
 */
public interface RootMcEnderChestService {

    int SLOT_COUNT = 54;

    /** Open the custom double ender chest UI for {@code viewer} showing {@code owner}'s contents. */
    void open(Player viewer, Player owner);

    /** Live Bukkit inventory for an online player (creates/migrates if needed). */
    Inventory inventory(Player player);

    /** Snapshot of contents (may be offline via UUID load). Never null; length {@link #SLOT_COUNT}. */
    ItemStack[] contents(UUID playerId);

    /** Persist contents for an online player's open/cached inventory. */
    void save(Player player);

    /** Persist a raw snapshot (e.g. after offline edits). */
    void save(UUID playerId, String playerName, ItemStack[] contents);

    /** Try to add items into the double EC; returns leftovers that did not fit. */
    ItemStack[] addItems(Player player, ItemStack... items);
}
