package com.rootrecord.minecraft.rootbonds.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class BondsMenuHolder implements InventoryHolder {

    private final UUID playerId;
    private Inventory inventory;
    private double displayedGold;

    public BondsMenuHolder(UUID playerId) {
        this.playerId = playerId;
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    public UUID playerId() {
        return playerId;
    }

    public double displayedGold() {
        return displayedGold;
    }

    public void setDisplayedGold(double displayedGold) {
        this.displayedGold = displayedGold;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
