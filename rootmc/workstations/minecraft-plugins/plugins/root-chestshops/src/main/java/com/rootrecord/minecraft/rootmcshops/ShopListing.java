package com.rootrecord.minecraft.rootmcshops;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;

public record ShopListing(
        String id,
        String ownerUuid,
        String ownerName,
        String world,
        int x,
        int y,
        int z,
        String itemKey,
        double price,
        String listingType,
        int saleQty,
        int signX,
        int signY,
        int signZ) {

    public ShopListing(
            String id,
            String ownerUuid,
            String ownerName,
            String world,
            int x,
            int y,
            int z,
            String itemKey,
            double price) {
        this(id, ownerUuid, ownerName, world, x, y, z, itemKey, price, "sell", 1, x, y + 1, z);
    }

    public static String normalizeListingType(String raw) {
        return "buy".equalsIgnoreCase(raw) ? "buy" : "sell";
    }

    public boolean isBuyShop() {
        return "buy".equalsIgnoreCase(normalizedType());
    }

    public boolean isSellShop() {
        return !isBuyShop();
    }

    public String normalizedType() {
        return normalizeListingType(listingType);
    }

    public Block block() {
        var w = Bukkit.getWorld(world);
        return w == null ? null : w.getBlockAt(x, y, z);
    }

    public Block signBlock() {
        var w = Bukkit.getWorld(world);
        return w == null ? null : w.getBlockAt(signX, signY, signZ);
    }

    public Location location() {
        var w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z);
    }

    public static String locationKey(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    public boolean isSignAt(String worldName, int bx, int by, int bz) {
        return world.equals(worldName) && signX == bx && signY == by && signZ == bz;
    }

    public ShopListing withSignBlock(int sx, int sy, int sz) {
        return new ShopListing(id, ownerUuid, ownerName, world, x, y, z, itemKey, price, normalizedType(), saleQty, sx, sy, sz);
    }

    public ShopListing withListingType(String type) {
        return new ShopListing(id, ownerUuid, ownerName, world, x, y, z, itemKey, price, normalizeListingType(type), saleQty, signX, signY, signZ);
    }

    public ShopListing withItemKey(String key) {
        return new ShopListing(id, ownerUuid, ownerName, world, x, y, z, key, price, normalizedType(), saleQty, signX, signY, signZ);
    }
}

