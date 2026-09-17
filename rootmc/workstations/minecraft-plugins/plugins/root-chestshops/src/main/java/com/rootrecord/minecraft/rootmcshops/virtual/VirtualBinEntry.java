package com.rootrecord.minecraft.rootmcshops.virtual;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** One stack line in a player's virtual My Storage bin. */
public record VirtualBinEntry(
        long id,
        UUID ownerUuid,
        String itemKey,
        String itemBlob,
        int qty) {

    public ItemStack template() {
        return ItemStackCodec.decode(itemBlob);
    }

    public ItemStack stackSample() {
        ItemStack t = template();
        return t == null ? null : ItemStackCodec.withAmount(t, Math.min(qty, t.getMaxStackSize()));
    }
}
