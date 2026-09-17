package com.rootrecord.minecraft.rootclaims.gui;

import com.rootrecord.minecraft.rootclaims.ClaimKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class ClaimsSettingsHolder implements InventoryHolder {

    private final UUID playerId;
    private final ClaimKey claimKey;
    private Inventory inventory;

    public ClaimsSettingsHolder(UUID playerId, ClaimKey claimKey) {
        this.playerId = playerId;
        this.claimKey = claimKey;
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    public UUID playerId() {
        return playerId;
    }

    public ClaimKey claimKey() {
        return claimKey;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
