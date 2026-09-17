package com.rootrecord.minecraft.rootavacore.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class AvaMenuHolder implements InventoryHolder {

    public static final int SLOT_HELLO = 4;
    public static final int SLOT_BREAD = 11;
    public static final int SLOT_JOBS = 13;
    public static final int SLOT_QUOTE = 15;
    public static final int SLOT_HELP = 22;
    public static final int SLOT_CLOSE = 26;

    private final UUID playerId;
    private Inventory inventory;

    public AvaMenuHolder(UUID playerId) {
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
