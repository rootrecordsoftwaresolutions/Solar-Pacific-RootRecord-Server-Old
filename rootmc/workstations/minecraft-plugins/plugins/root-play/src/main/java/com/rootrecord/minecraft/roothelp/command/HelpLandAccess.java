package com.rootrecord.minecraft.roothelp.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/** Soft Root-Claims gate for /feedback — own claim circle or territory band. */
public final class HelpLandAccess {

    private HelpLandAccess() {}

    public static boolean bypass(Player player) {
        return player != null
                && (player.isOp()
                || player.hasPermission("roothelp.feedback.anywhere")
                || player.hasPermission("rootclaims.admin")
                || player.hasPermission("rootclaims.bypass"));
    }

    /** {@code null} when allowed; otherwise message key. */
    public static String requireOwnLand(Player player) {
        if (player == null) {
            return "feedback-players-only";
        }
        if (bypass(player)) {
            return null;
        }
        Plugin claims = Bukkit.getPluginManager().getPlugin("Root-Claims");
        if (claims == null || !claims.isEnabled()) {
            return "feedback-claims-offline";
        }
        if (inOwnLand(player)) {
            return null;
        }
        return "feedback-not-in-land";
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
