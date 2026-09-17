package com.rootrecord.minecraft.rootmarket;

import com.rootrecord.minecraft.rootmcshops.ShopItemKeys;
import org.bukkit.Material;

import java.util.Locale;

/** Simple material buckets for /market category browse. */
public enum MarketCategory {
    ALL("All", Material.CHEST),
    MINERALS("Minerals", Material.DIAMOND),
    BLOCKS("Blocks", Material.GRASS_BLOCK),
    FOOD("Food", Material.COOKED_BEEF),
    FARMING("Farming", Material.WHEAT),
    REDSTONE("Redstone", Material.REDSTONE),
    COMBAT("Combat", Material.IRON_SWORD),
    MISC("Misc", Material.PAPER);

    private final String display;
    private final Material icon;

    MarketCategory(String display, Material icon) {
        this.display = display;
        this.icon = icon;
    }

    public String display() {
        return display;
    }

    public Material icon() {
        return icon;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MarketCategory fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return ALL;
        }
        try {
            return MarketCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ALL;
        }
    }

    public boolean matches(String itemKey) {
        if (this == ALL) {
            return true;
        }
        if (this == MISC) {
            return primary(itemKey) == MISC;
        }
        return matchesStrict(itemKey);
    }

    public static MarketCategory primary(String itemKey) {
        for (MarketCategory c : values()) {
            if (c == ALL || c == MISC) {
                continue;
            }
            if (c.matchesStrict(itemKey)) {
                return c;
            }
        }
        return MISC;
    }

    private boolean matchesStrict(String itemKey) {
        Material mat = ShopItemKeys.baseMaterial(itemKey);
        if (mat == null || mat.isAir()) {
            return false;
        }
        String name = mat.name();
        return switch (this) {
            case ALL, MISC -> false;
            case MINERALS -> isMineral(mat, name) && !isRedstone(mat, name);
            case BLOCKS -> mat.isBlock() && !isMineral(mat, name) && !isFarmCrop(name) && !isRedstone(mat, name);
            case FOOD -> mat.isEdible() || name.contains("COOKED") || name.endsWith("_STEW") || name.equals("BREAD")
                    || name.equals("CAKE") || name.equals("COOKIE") || name.equals("PUMPKIN_PIE");
            case FARMING -> isFarmCrop(name) || name.contains("SEED") || name.equals("BONE_MEAL")
                    || name.equals("COMPOSTER");
            case REDSTONE -> isRedstone(mat, name);
            case COMBAT -> name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_BOW")
                    || name.equals("BOW") || name.equals("CROSSBOW") || name.equals("TRIDENT")
                    || name.equals("SHIELD") || name.equals("ARROW") || name.equals("SPECTRAL_ARROW")
                    || name.equals("TIPPED_ARROW") || name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                    || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS") || name.equals("TOTEM_OF_UNDYING");
        };
    }

    private static boolean isMineral(Material mat, String name) {
        return name.contains("ORE") || name.contains("INGOT") || name.contains("NUGGET")
                || name.equals("COAL") || name.equals("CHARCOAL") || name.equals("DIAMOND")
                || name.equals("EMERALD") || name.equals("LAPIS_LAZULI") || name.equals("QUARTZ")
                || name.equals("AMETHYST_SHARD") || name.equals("RAW_IRON") || name.equals("RAW_GOLD")
                || name.equals("RAW_COPPER") || name.equals("NETHERITE_SCRAP") || name.equals("ANCIENT_DEBRIS")
                || name.endsWith("_BLOCK") && (name.startsWith("IRON") || name.startsWith("GOLD")
                || name.startsWith("COPPER") || name.startsWith("DIAMOND") || name.startsWith("EMERALD")
                || name.startsWith("NETHERITE") || name.startsWith("COAL") || name.startsWith("LAPIS")
                || name.startsWith("RAW_") || name.equals("QUARTZ_BLOCK") || name.equals("AMETHYST_BLOCK"))
                || name.equals("FLINT") || name.equals("REDSTONE");
    }

    private static boolean isFarmCrop(String name) {
        return name.equals("WHEAT") || name.equals("CARROT") || name.equals("POTATO") || name.equals("BEETROOT")
                || name.equals("MELON_SLICE") || name.equals("PUMPKIN") || name.equals("MELON")
                || name.equals("SUGAR_CANE") || name.equals("CACTUS") || name.equals("BAMBOO")
                || name.equals("COCOA_BEANS") || name.equals("NETHER_WART") || name.equals("CHORUS_FRUIT")
                || name.contains("SAPLING") || name.equals("APPLE") || name.equals("SWEET_BERRIES")
                || name.equals("GLOW_BERRIES");
    }

    private static boolean isRedstone(Material mat, String name) {
        return name.contains("REDSTONE") || name.equals("REPEATER") || name.equals("COMPARATOR")
                || name.equals("PISTON") || name.equals("STICKY_PISTON") || name.equals("OBSERVER")
                || name.equals("HOPPER") || name.equals("DROPPER") || name.equals("DISPENSER")
                || name.equals("TARGET") || name.equals("DAYLIGHT_DETECTOR") || name.equals("LEVER")
                || name.equals("TRIPWIRE_HOOK") || name.equals("SLIME_BLOCK") || name.equals("HONEY_BLOCK");
    }
}
