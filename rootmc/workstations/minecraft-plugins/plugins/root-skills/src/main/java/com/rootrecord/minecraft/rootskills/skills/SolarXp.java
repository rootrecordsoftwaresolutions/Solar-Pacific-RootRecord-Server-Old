package com.rootrecord.minecraft.rootskills.skills;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Soft-depend Root-Economy solar XP (same curve as gold: connected 1% + bank/10% + W/100W).
 */
final class SolarXp {

    private SolarXp() {}

    static double multiplier() {
        try {
            Plugin eco = Bukkit.getPluginManager().getPlugin("Root-Economy");
            if (eco == null || !eco.isEnabled()) {
                return 1.0d;
            }
            Object solar = eco.getClass().getMethod("solarMining").invoke(eco);
            if (solar == null) {
                return 1.0d;
            }
            Object v = solar.getClass().getMethod("xpMultiplier").invoke(solar);
            if (v instanceof Number n) {
                double xp = n.doubleValue();
                return Double.isFinite(xp) && xp > 0 ? xp : 1.0d;
            }
        } catch (Throwable ignored) {
            // Root-Economy missing or older snapshot without xpMultiplier()
        }
        return 1.0d;
    }
}
