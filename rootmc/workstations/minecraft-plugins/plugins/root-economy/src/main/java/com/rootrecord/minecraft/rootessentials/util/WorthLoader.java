package com.rootrecord.minecraft.rootessentials.util;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class WorthLoader {

    private WorthLoader() {}

    public static Map<Material, Double> load(ConfigurationSection inline, String externalPath) {
        Map<Material, Double> worth = new HashMap<>();
        if (inline != null) {
            mergeSection(worth, inline);
        }
        if (externalPath != null && !externalPath.isBlank()) {
            File file = new File(externalPath);
            if (file.isFile()) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                ConfigurationSection sec = yaml.getConfigurationSection("worth");
                if (sec != null) {
                    mergeSection(worth, sec);
                }
            }
        }
        return worth;
    }

    private static void mergeSection(Map<Material, Double> worth, ConfigurationSection sec) {
        for (String key : sec.getKeys(false)) {
            if (sec.isConfigurationSection(key)) {
                ConfigurationSection sub = sec.getConfigurationSection(key);
                if (sub == null) {
                    continue;
                }
                for (String dataKey : sub.getKeys(false)) {
                    put(worth, key, sub.getDouble(dataKey, 0));
                }
            } else {
                put(worth, key, sec.getDouble(key, 0));
            }
        }
    }

    private static void put(Map<Material, Double> worth, String key, double value) {
        if (value <= 0) {
            return;
        }
        Material mat = resolveMaterial(key);
        if (mat != null && !mat.isAir()) {
            worth.put(mat, value);
        }
    }

    private static Material resolveMaterial(String key) {
        String normalized = key.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        Material mat = Material.matchMaterial(normalized);
        if (mat != null) {
            return mat;
        }
        return switch (normalized) {
            case "GOLDINGOT" -> Material.GOLD_INGOT;
            case "GOLDNUGGET" -> Material.GOLD_NUGGET;
            case "IRONINGOT" -> Material.IRON_INGOT;
            case "WOOD" -> Material.OAK_PLANKS;
            case "LOG" -> Material.OAK_LOG;
            case "LEAVES" -> Material.OAK_LEAVES;
            case "SAPLING" -> Material.OAK_SAPLING;
            case "WOOL" -> Material.WHITE_WOOL;
            case "STONE" -> Material.STONE;
            case "DIRT" -> Material.DIRT;
            case "COBBLESTONE" -> Material.COBBLESTONE;
            default -> null;
        };
    }
}
