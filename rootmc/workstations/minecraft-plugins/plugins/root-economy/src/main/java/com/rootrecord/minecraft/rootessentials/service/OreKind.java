package com.rootrecord.minecraft.rootessentials.service;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;

/**
 * Radar ore kinds + compass-bar / sparkle colors.
 * Black coal, light-blue diamond, dark-blue lapis, red redstone, green emerald, yellow gold, white iron.
 */
public enum OreKind {
    COAL("Coal", NamedTextColor.GRAY, Color.fromRGB(20, 20, 20)),
    IRON("Iron", NamedTextColor.WHITE, Color.fromRGB(230, 230, 230)),
    GOLD("Gold", NamedTextColor.YELLOW, Color.fromRGB(255, 210, 50)),
    DIAMOND("Diamond", NamedTextColor.AQUA, Color.fromRGB(80, 220, 255)),
    LAPIS("Lapis", NamedTextColor.DARK_BLUE, Color.fromRGB(30, 50, 190)),
    REDSTONE("Redstone", NamedTextColor.RED, Color.fromRGB(220, 40, 40)),
    EMERALD("Emerald", NamedTextColor.GREEN, Color.fromRGB(40, 200, 80));

    private final String label;
    private final NamedTextColor text;
    private final Particle.DustOptions sparkle;

    OreKind(String label, NamedTextColor text, Color sparkle) {
        this.label = label;
        this.text = text;
        this.sparkle = new Particle.DustOptions(sparkle, 1.15f);
    }

    public String label() {
        return label;
    }

    public NamedTextColor text() {
        return text;
    }

    public Particle.DustOptions sparkle() {
        return sparkle;
    }

    public static OreKind of(Material material) {
        if (material == null) {
            return null;
        }
        return switch (material) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> COAL;
            case IRON_ORE, DEEPSLATE_IRON_ORE -> IRON;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> GOLD;
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> DIAMOND;
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> LAPIS;
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> REDSTONE;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> EMERALD;
            default -> null;
        };
    }

    public static boolean isRadarOre(Material material) {
        return of(material) != null;
    }
}
