package com.rootrecord.minecraft.rootclaims.gui;

import com.rootrecord.minecraft.rootclaims.ClaimKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ClaimChestHolder implements InventoryHolder {

    private final ClaimKey claimKey;
    private Inventory inventory;

    public ClaimChestHolder(ClaimKey claimKey) {
        this.claimKey = claimKey;
    }

    public ClaimKey claimKey() {
        return claimKey;
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
