package com.rootrecord.minecraft.rootgamble.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class GambleMenuHolder implements InventoryHolder {

    public enum Kind {
        HUB
    }

    private final Kind kind;
    private Inventory inventory;

    public GambleMenuHolder(Kind kind) {
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
