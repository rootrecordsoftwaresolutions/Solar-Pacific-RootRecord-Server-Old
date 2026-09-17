package com.rootrecord.minecraft.rootmcshops;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;
import java.util.UUID;

/** Browse-only GUI of one player's sell shops. */
public final class PlayerShopsMenuHolder implements InventoryHolder {

    private final UUID viewerId;
    private final String ownerUuid;
    private final String ownerDisplayName;
    private final int page;
    private final List<String> shopIds;
    private Inventory inventory;

    public PlayerShopsMenuHolder(
            UUID viewerId,
            String ownerUuid,
            String ownerDisplayName,
            int page,
            List<String> shopIds) {
        this.viewerId = viewerId;
        this.ownerUuid = ownerUuid;
        this.ownerDisplayName = ownerDisplayName;
        this.page = Math.max(0, page);
        this.shopIds = List.copyOf(shopIds);
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    public UUID viewerId() {
        return viewerId;
    }

    public String ownerUuid() {
        return ownerUuid;
    }

    public String ownerDisplayName() {
        return ownerDisplayName;
    }

    public int page() {
        return page;
    }

    public List<String> shopIds() {
        return shopIds;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
