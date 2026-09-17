package com.rootrecord.minecraft.rootspawn;

import org.bukkit.Location;
import org.bukkit.World;

/** One lava-rain column: ceiling block (lava above) and floor block below. */
public record LavaSpot(
        String world,
        int ceilingX,
        int ceilingY,
        int ceilingZ,
        int floorX,
        int floorY,
        int floorZ) {

    Location ceiling(World worldObj) {
        return new Location(worldObj, ceilingX, ceilingY, ceilingZ);
    }

    Location floor(World worldObj) {
        return new Location(worldObj, floorX, floorY, floorZ);
    }
}
