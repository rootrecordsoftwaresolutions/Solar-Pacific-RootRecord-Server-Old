package com.rootrecord.minecraft.rootclaims.gui;

import com.rootrecord.minecraft.rootclaims.ClaimKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClaimsDashboardHolder implements InventoryHolder {

    private final UUID playerId;
    private final Map<Integer, ClaimKey> claimSlots = new HashMap<>();
    private Inventory inventory;

    public ClaimsDashboardHolder(UUID playerId) {
        this.playerId = playerId;
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    public UUID playerId() {
        return playerId;
    }

    public void putClaimSlot(int slot, ClaimKey key) {
        claimSlots.put(slot, key);
    }

    public ClaimKey claimAtSlot(int slot) {
        return claimSlots.get(slot);
    }

    public Map<Integer, ClaimKey> claimSlots() {
        return Collections.unmodifiableMap(claimSlots);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
