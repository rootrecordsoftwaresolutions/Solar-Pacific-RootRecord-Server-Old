package com.rootrecord.minecraft.rootterritories.model;

/** One Towny townblock in block coordinates (inclusive). */
public record PlotRect(String world, int minX, int minZ, int maxX, int maxZ) {

    public double centerX() {
        return (minX + maxX) / 2.0;
    }

    public double centerZ() {
        return (minZ + maxZ) / 2.0;
    }
}
