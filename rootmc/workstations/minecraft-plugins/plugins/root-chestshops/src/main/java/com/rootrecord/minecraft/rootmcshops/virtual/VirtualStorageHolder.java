package com.rootrecord.minecraft.rootmcshops.virtual;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;
import java.util.UUID;

public final class VirtualStorageHolder implements InventoryHolder {

    public enum Mode {
        STORAGE,
        MY_LISTINGS
    }

    private final UUID viewerId;
    private final Mode mode;
    private final List<Long> binIds;
    private final List<String> listingIds;
    private Inventory inventory;

    private VirtualStorageHolder(UUID viewerId, Mode mode, List<Long> binIds, List<String> listingIds) {
        this.viewerId = viewerId;
        this.mode = mode;
        this.binIds = List.copyOf(binIds);
        this.listingIds = List.copyOf(listingIds);
    }

    public static VirtualStorageHolder storage(UUID viewerId, List<Long> binIds) {
        return new VirtualStorageHolder(viewerId, Mode.STORAGE, binIds, List.of());
    }

    public static VirtualStorageHolder listings(UUID viewerId, List<String> listingIds) {
        return new VirtualStorageHolder(viewerId, Mode.MY_LISTINGS, List.of(), listingIds);
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID viewerId() {
        return viewerId;
    }

    public Mode mode() {
        return mode;
    }

    public List<Long> binIds() {
        return binIds;
    }

    public List<String> listingIds() {
        return listingIds;
    }
}
