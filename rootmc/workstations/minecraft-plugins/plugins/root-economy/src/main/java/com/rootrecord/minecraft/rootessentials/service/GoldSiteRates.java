package com.rootrecord.minecraft.rootessentials.service;

import com.rootrecord.minecraft.rootessentials.util.GoldItemValue;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

import java.util.Locale;

/**
 * Location gold rules: Y-band env tax, mesa/badlands tax, nether tax, deepslate valuation.
 * Drop yield = solarMult × deepslateValue × (1 − locationTax).
 */
public final class GoldSiteRates {

    public static final int DETECT_RADIUS = 4;
    public static final int ENV_Y_MIN = -20;
    public static final int ENV_Y_MAX = 20;
    public static final double ENV_TAX = 0.01d;
    public static final double MESA_TAX = 0.50d;
    public static final double NETHER_TAX = 0.0d;
    public static final double DEEPSLATE_VALUE = 1.5d;

    private GoldSiteRates() {}

    public record Site(
            double solarMult,
            double envTax,
            double mesaTax,
            double netherTax,
            double locationTax,
            double deepslateValue,
            double dropMultiplier,
            Material ore,
            int y,
            boolean nether,
            boolean mesa) {

        public boolean hasLocationTax() {
            return locationTax >= 0.0005d;
        }

        public boolean deepslate() {
            return deepslateValue > 1.0001d;
        }
    }

    public static Site at(Location loc, Material ore, double solarMult) {
        if (loc == null || loc.getWorld() == null) {
            return new Site(clampMult(solarMult), 0, 0, 0, 0, 1.0d, clampMult(solarMult), ore, 0, false, false);
        }
        int y = loc.getBlockY();
        World world = loc.getWorld();
        boolean netherWorld = world.getEnvironment() == World.Environment.NETHER;
        boolean netherOre = ore == Material.NETHER_GOLD_ORE;
        boolean mesa = isMesa(loc);
        double env = (y >= ENV_Y_MIN && y <= ENV_Y_MAX) ? ENV_TAX : 0.0d;
        double mesaTax = mesa ? MESA_TAX : 0.0d;
        double netherTax = (netherWorld || netherOre) ? NETHER_TAX : 0.0d;
        double locTax = Math.min(0.95d, Math.max(0.0d, env + mesaTax + netherTax));
        double deep = ore == Material.DEEPSLATE_GOLD_ORE ? DEEPSLATE_VALUE : 1.0d;
        double solar = clampMult(solarMult);
        double drop = solar * deep * (1.0d - locTax);
        return new Site(solar, env, mesaTax, netherTax, locTax, deep, drop, ore, y, netherWorld || netherOre, mesa);
    }

    public static Site at(Block block, double solarMult) {
        if (block == null) {
            return at((Location) null, null, solarMult);
        }
        return at(block.getLocation(), block.getType(), solarMult);
    }

    public static double foundValueMultiplier(Material sourceBlock) {
        return sourceBlock == Material.DEEPSLATE_GOLD_ORE ? DEEPSLATE_VALUE : 1.0d;
    }

    public static boolean isGoldOre(Material material) {
        return GoldItemValue.isGoldOre(material);
    }

    public static boolean isMesa(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        return isMesa(loc.getWorld().getBiome(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
    }

    public static boolean isMesa(Biome biome) {
        if (biome == null) {
            return false;
        }
        String key = biome.getKey().getKey().toLowerCase(Locale.ROOT);
        return key.contains("badlands") || key.contains("mesa");
    }

    public static String oreLabel(Material ore) {
        if (ore == Material.DEEPSLATE_GOLD_ORE) {
            return "deepslate gold";
        }
        if (ore == Material.NETHER_GOLD_ORE) {
            return "nether gold";
        }
        if (ore == Material.GOLD_ORE) {
            return "gold ore";
        }
        return "gold";
    }

    public static String taxSummary(Site site) {
        if (site == null || !site.hasLocationTax()) {
            return "no site tax";
        }
        StringBuilder sb = new StringBuilder();
        if (site.mesaTax() >= 0.0005d) {
            sb.append("mesa ").append(pct(site.mesaTax()));
        }
        if (site.netherTax() >= 0.0005d) {
            if (!sb.isEmpty()) {
                sb.append(" + ");
            }
            sb.append("nether ").append(pct(site.netherTax()));
        }
        if (site.envTax() >= 0.0005d) {
            if (!sb.isEmpty()) {
                sb.append(" + ");
            }
            sb.append("env ").append(pct(site.envTax()));
        }
        return sb + " = " + pct(site.locationTax());
    }

    private static String pct(double rate) {
        return String.format(Locale.US, "%.0f%%", rate * 100.0d);
    }

    private static double clampMult(double solarMult) {
        if (!Double.isFinite(solarMult) || solarMult <= 0) {
            return 1.0d;
        }
        return solarMult;
    }
}
