package com.rootrecord.minecraft.rootavacore;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Soft Root-Claims gate: hire is owner-only; gift may still allow trusted.
 */
public final class AvaLandAccess {

    private AvaLandAccess() {}

    public static boolean claimsOnline() {
        Plugin claims = Bukkit.getPluginManager().getPlugin("Root-Claims");
        return claims != null && claims.isEnabled();
    }

    public static boolean bypass(Player player) {
        return player != null
                && (player.isOp()
                || player.hasPermission("rootavacore.admin")
                || player.hasPermission("rootclaims.admin")
                || player.hasPermission("rootclaims.bypass"));
    }

    /** Gift / general: owner or trusted. {@code null} when allowed. */
    public static String requireOwnLand(Player player) {
        if (player == null) {
            return "players_only";
        }
        if (bypass(player)) {
            return null;
        }
        if (!claimsOnline()) {
            return "claims_offline";
        }
        if (inOwnLand(player)) {
            return null;
        }
        Object land = claimAt(player.getLocation());
        if (land == null) {
            land = territoryAt(player);
        }
        return land == null ? "not_in_claim" : "not_your_claim";
    }

    /** Hire: owner only — not trusted, not others, not wilderness. */
    public static String requireOwnedLand(Player player) {
        if (player == null) {
            return "players_only";
        }
        if (bypass(player)) {
            return null;
        }
        if (!claimsOnline()) {
            return "claims_offline";
        }
        if (inOwnedLand(player)) {
            return null;
        }
        Object land = claimAt(player.getLocation());
        if (land == null) {
            land = territoryAt(player);
        }
        return land == null ? "not_in_claim" : "not_your_claim";
    }

    public static boolean inOwnLand(Player player) {
        if (player == null) {
            return false;
        }
        try {
            Plugin claimsPlug = Bukkit.getPluginManager().getPlugin("Root-Claims");
            if (claimsPlug == null || !claimsPlug.isEnabled()) {
                return false;
            }
            Object service = claimsPlug.getClass().getMethod("claims").invoke(claimsPlug);
            try {
                Object direct = service.getClass().getMethod("inOwnLand", Player.class).invoke(service, player);
                if (direct instanceof Boolean b) {
                    return b;
                }
            } catch (NoSuchMethodException ignored) {
                // Root-Claims 1.8.117 fallback
            }
            Object claim = service.getClass().getMethod("claimAt", Player.class).invoke(service, player);
            if (claim != null && canManage(claim, player.getUniqueId())) {
                return true;
            }
            Object territory = service.getClass().getMethod("territoryAt", Player.class).invoke(service, player);
            return territory != null && canManage(territory, player.getUniqueId());
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean inOwnedLand(Player player) {
        if (player == null) {
            return false;
        }
        Object claim = claimAt(player.getLocation());
        if (isOwner(claim, player.getUniqueId())) {
            return true;
        }
        Object territory = territoryAt(player);
        return isOwner(territory, player.getUniqueId());
    }

    public static Object claimAt(Location loc) {
        try {
            Plugin claims = Bukkit.getPluginManager().getPlugin("Root-Claims");
            if (claims == null || !claims.isEnabled() || loc == null) {
                return null;
            }
            Object service = claims.getClass().getMethod("claims").invoke(claims);
            return service.getClass().getMethod("claimAt", Location.class).invoke(service, loc);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Object territoryAt(Player player) {
        try {
            Plugin claims = Bukkit.getPluginManager().getPlugin("Root-Claims");
            if (claims == null || !claims.isEnabled() || player == null) {
                return null;
            }
            Object service = claims.getClass().getMethod("claims").invoke(claims);
            return service.getClass().getMethod("territoryAt", Player.class).invoke(service, player);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean isOwner(Object claim, UUID playerId) {
        if (claim == null || playerId == null) {
            return false;
        }
        try {
            Object v = claim.getClass().getMethod("ownerId").invoke(claim);
            if (playerId.equals(v)) {
                return true;
            }
        } catch (Throwable ignored) {
            // fall through
        }
        try {
            Plugin claims = Bukkit.getPluginManager().getPlugin("Root-Claims");
            if (claims == null || !claims.isEnabled()) {
                return false;
            }
            Object service = claims.getClass().getMethod("claims").invoke(claims);
            Object root = service.getClass().getMethod("areaRoot", claim.getClass()).invoke(service, claim);
            Object v = root == null ? null : root.getClass().getMethod("ownerId").invoke(root);
            return playerId.equals(v);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean canManage(Object claim, UUID playerId) {
        if (claim == null || playerId == null) {
            return false;
        }
        try {
            Object v = claim.getClass().getMethod("canManage", UUID.class).invoke(claim, playerId);
            return Boolean.TRUE.equals(v);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
