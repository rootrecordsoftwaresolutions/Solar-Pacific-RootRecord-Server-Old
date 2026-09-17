package com.rootrecord.minecraft.rootspawn;

import org.bukkit.Location;

/** Basement layer inside the spawn ring footprint (outside chamber = no mobs). */
public final class BasementZone {

    public static final int DEFAULT_TOP = 0;
    public static final int DEFAULT_BOTTOM = -60;

    private final int yTop;
    private final int yBottom;

    public BasementZone(int yTop, int yBottom) {
        this.yTop = yTop;
        this.yBottom = Math.min(yBottom, yTop);
    }

    public int yTop() {
        return yTop;
    }

    public int yBottom() {
        return yBottom;
    }

    public boolean containsY(double y) {
        return y <= yTop && y >= yBottom;
    }

    public boolean contains(Location loc) {
        return loc != null && containsY(loc.getY());
    }
}
