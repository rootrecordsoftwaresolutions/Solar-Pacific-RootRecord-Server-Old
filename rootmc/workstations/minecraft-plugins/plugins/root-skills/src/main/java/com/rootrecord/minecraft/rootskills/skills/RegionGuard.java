package com.rootrecord.minecraft.rootskills.skills;

import com.rootrecord.minecraft.rootskills.RootSkillsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Best-effort Towny / Root-Claims denial checks via reflection — never hard-fails.
 */
public final class RegionGuard {

    private RegionGuard() {}

    public static boolean isDenied(RootSkillsPlugin plugin, Player player) {
        if (player == null) {
            return false;
        }
        try {
            if (isTownyDenied(player)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (isClaimsDenied(plugin, player)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isTownyDenied(Player player) throws Exception {
        Plugin towny = Bukkit.getPluginManager().getPlugin("Towny");
        if (towny == null || !towny.isEnabled()) {
            return false;
        }
        Class<?> api = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
        Object instance = api.getMethod("getInstance").invoke(null);
        Boolean wilderness = (Boolean) api.getMethod("isWilderness", org.bukkit.Location.class)
                .invoke(instance, player.getLocation());
        if (Boolean.TRUE.equals(wilderness)) {
            return false;
        }
        // If in a town and player is not a resident of that town, skip XP (soft PvP/plot guard)
        Object town = api.getMethod("getTown", org.bukkit.Location.class).invoke(instance, player.getLocation());
        if (town == null) {
            return false;
        }
        Object resident = api.getMethod("getResident", Player.class).invoke(instance, player);
        if (resident == null) {
            return true;
        }
        Object resTown = resident.getClass().getMethod("getTownOrNull").invoke(resident);
        return resTown == null || !resTown.equals(town);
    }

    private static boolean isClaimsDenied(RootSkillsPlugin plugin, Player player) throws Exception {
        Plugin claims = Bukkit.getPluginManager().getPlugin("Root-Claims");
        if (claims == null) {
            claims = Bukkit.getPluginManager().getPlugin("RootClaims");
        }
        if (claims == null || !claims.isEnabled()) {
            return false;
        }
        // Soft: look for a static canBuild(Player, Location) or similar
        for (String className : new String[] {
                "com.rootrecord.minecraft.rootclaims.ClaimsAPI",
                "com.rootrecord.minecraft.claims.ClaimsAPI"
        }) {
            try {
                Class<?> api = Class.forName(className);
                Object inst = null;
                try {
                    inst = api.getMethod("get").invoke(null);
                } catch (NoSuchMethodException ignored) {
                    try {
                        inst = api.getMethod("getInstance").invoke(null);
                    } catch (NoSuchMethodException ignored2) {
                    }
                }
                Object target = inst != null ? inst : api;
                try {
                    Object allowed = (inst != null ? api : Class.forName(className))
                            .getMethod("canBuild", Player.class, org.bukkit.Location.class)
                            .invoke(inst != null ? inst : null, player, player.getLocation());
                    if (allowed instanceof Boolean b && !b) {
                        return true;
                    }
                } catch (NoSuchMethodException ignored) {
                }
            } catch (ClassNotFoundException ignored) {
            }
        }
        return false;
    }
}
