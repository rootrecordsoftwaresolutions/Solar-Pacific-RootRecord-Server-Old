package com.rootrecord.minecraft.common;

/** Shop listing row shared between rootmc-shops and RootMC economy sync. */
public record RootMcShopListingDto(
        String shopId,
        String ownerUuid,
        String ownerUsername,
        String worldName,
        int x,
        int y,
        int z,
        String itemKey,
        double price,
        String listingType,
        int stockQuantity) {}
