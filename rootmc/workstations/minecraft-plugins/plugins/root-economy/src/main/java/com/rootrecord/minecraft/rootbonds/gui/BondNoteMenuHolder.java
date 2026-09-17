package com.rootrecord.minecraft.rootbonds.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class BondNoteMenuHolder implements InventoryHolder {

    private final UUID playerId;
    private final UUID bondId;
    private Inventory inventory;

    public BondNoteMenuHolder(UUID playerId, UUID bondId) {
        this.playerId = playerId;
        this.bondId = bondId;
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    public UUID playerId() {
        return playerId;
    }

    public UUID bondId() {
        return bondId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
