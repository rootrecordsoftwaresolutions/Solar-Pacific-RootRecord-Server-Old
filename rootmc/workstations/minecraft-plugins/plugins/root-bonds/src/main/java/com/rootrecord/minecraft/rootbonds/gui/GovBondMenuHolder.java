package com.rootrecord.minecraft.rootbonds.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class GovBondMenuHolder implements InventoryHolder {

    private final UUID playerId;
    private final String kind;
    private final String displayName;
    private final UUID bankUuid;
    private final String bankName;
    private Inventory inventory;

    public GovBondMenuHolder(UUID playerId, String kind, String displayName, UUID bankUuid, String bankName) {
        this.playerId = playerId;
        this.kind = kind;
        this.displayName = displayName;
        this.bankUuid = bankUuid;
        this.bankName = bankName;
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    public UUID playerId() {
        return playerId;
    }

    public String kind() {
        return kind;
    }

    public String displayName() {
        return displayName;
    }

    public UUID bankUuid() {
        return bankUuid;
    }

    public String bankName() {
        return bankName;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
