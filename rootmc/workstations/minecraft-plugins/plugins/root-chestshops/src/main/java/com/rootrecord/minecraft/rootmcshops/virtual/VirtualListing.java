package com.rootrecord.minecraft.rootmcshops.virtual;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** Chestless marketplace listing backed by virtual storage. */
public record VirtualListing(
        String id,
        UUID ownerUuid,
        String ownerName,
        String itemKey,
        String itemBlob,
        double price,
        String listingType,
        int qty) {

    public boolean isSell() {
        return !"buy".equalsIgnoreCase(listingType);
    }

    public boolean isBuy() {
        return "buy".equalsIgnoreCase(listingType);
    }

    public ItemStack template() {
        return ItemStackCodec.decode(itemBlob);
    }

    public ItemStack stackSample() {
        ItemStack t = template();
        return t == null ? null : ItemStackCodec.withAmount(t, Math.min(Math.max(1, qty), t.getMaxStackSize()));
    }
}
