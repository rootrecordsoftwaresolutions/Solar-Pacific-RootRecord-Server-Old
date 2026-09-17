package com.rootrecord.minecraft.rootbonds.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class BondCreateMenuHolder implements InventoryHolder {

    private final UUID playerId;
    private Inventory inventory;

    public BondCreateMenuHolder(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID playerId() {
        return playerId;
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
