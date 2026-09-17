package com.rootrecord.minecraft.rootmcshops;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Root-Claims soft path for shop creation (Claims host). */
public final class ShopClaimsAccess {

    private ShopClaimsAccess() {}

    public static boolean isAvailable() {
        Plugin claims = Bukkit.getPluginManager().getPlugin("Root-Claims");
        return claims != null && claims.isEnabled();
    }

    /** True when the player owns or is trusted on a claim covering this block. */
    public static boolean allowsShopAt(Location location, Player player) {
        if (location == null || player == null || !isAvailable()) {
            return false;
        }
        Plugin claims = Bukkit.getPluginManager().getPlugin("Root-Claims");
        try {
            Object result = claims.getClass()
                    .getMethod("canManageClaimAt", Player.class, Location.class)
                    .invoke(claims, player, location);
            return result instanceof Boolean b && b;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }
}
