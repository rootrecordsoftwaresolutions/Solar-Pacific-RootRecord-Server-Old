package com.rootrecord.minecraft.rootclaims;

import org.bukkit.Location;
import org.bukkit.block.Block;

public record ClaimKey(String world, int x, int y, int z) {

    public static ClaimKey of(Location location) {
        return new ClaimKey(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }

    public static ClaimKey of(Block block) {
        return new ClaimKey(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ());
    }

    public String label() {
        return world + " " + x + ", " + y + ", " + z;
    }

    public String storageId() {
        return world + ":" + x + ":" + y + ":" + z;
    }
}
