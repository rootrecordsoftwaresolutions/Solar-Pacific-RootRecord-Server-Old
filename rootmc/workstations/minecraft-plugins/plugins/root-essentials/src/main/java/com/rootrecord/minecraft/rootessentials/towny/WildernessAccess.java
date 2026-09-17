package com.rootrecord.minecraft.rootessentials.towny;

import com.rootrecord.minecraft.common.RootMcClaimTerritoryService;
import com.rootrecord.minecraft.common.ShadedServiceBridge;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

/** Wilderness = unclaimed land (RootClaims + Towny when present). */
public final class WildernessAccess {

    private WildernessAccess() {}

    public static boolean isWilderness(Plugin plugin, Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        String world = location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        if (y < RootMcClaimTerritoryService.MINING_FLOOR_Y) {
            return true;
        }
        RootMcClaimTerritoryService claims = ShadedServiceBridge.resolveClaimTerritory(plugin);
        if (claims != null && claims.isClaimed(world, x, y, z)) {
            return false;
        }
        if (TownyWildernessAccess.isAvailable()) {
            return TownyWildernessAccess.isTownyWilderness(location);
        }
        return true;
    }
}
