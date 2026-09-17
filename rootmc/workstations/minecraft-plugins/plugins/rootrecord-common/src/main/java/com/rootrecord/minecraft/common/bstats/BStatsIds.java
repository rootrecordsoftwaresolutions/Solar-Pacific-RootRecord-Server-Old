package com.rootrecord.minecraft.common.bstats;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * bStats Bukkit service IDs for active RootMC jars.
 * Dashboard URLs: {@code https://bstats.org/plugin/bukkit/<name>/<id>}
 * (name is URL-encoded registration name — see {@link #urlFor}).
 */
public final class BStatsIds {

    public static final int ROOT_HASTE = 32894;
    /** @deprecated Same service id as {@link #ROOT_HASTE} (formerly Root-Joint). */
    public static final int ROOT_JOINT = ROOT_HASTE;
    public static final int ROOT_TORCH = 32914;
    public static final int ROOT_CORE = 32895;
    public static final int ROOT_TIMES = 32897;
    public static final int ROOT_PERMS = 32898;
    public static final int ROOT_ESSENTIALS = 32899;
    public static final int ROOT_CLAIMS = 32900;
    public static final int ROOT_TERRITORIES = 32901;
    public static final int ROOTMC = 32902;
    public static final int ROOT_PLAY = 32903;
    public static final int ROOT_OPS = 32904;
    public static final int ROOT_BLUEMAP_R2_FIX = 32905;
    public static final int ROOT_ITEMINFO = 32906;
    public static final int ROOT_WEBSTAT = 32907;
    public static final int ROOT_TRY = 32908;
    public static final int ROOTMC_OFFICIAL = 32909;
    public static final int ROOT_PING = 32911;

    /**
     * bStats registration display name → id.
     * Root Joint was registered with a space; others match {@code plugin.yml} {@code name}.
     */
    private static final Map<String, Integer> BY_PLUGIN_YML_NAME = Map.ofEntries(
            Map.entry("Root-Core", ROOT_CORE),
            Map.entry("Root-Times", ROOT_TIMES),
            Map.entry("Root-Perms", ROOT_PERMS),
            Map.entry("Root-Essentials", ROOT_ESSENTIALS),
            Map.entry("Root-Claims", ROOT_CLAIMS),
            Map.entry("Root-Territories", ROOT_TERRITORIES),
            Map.entry("RootMC", ROOTMC),
            Map.entry("Root-Play", ROOT_PLAY),
            Map.entry("Root-Ops", ROOT_OPS),
            Map.entry("Root-BlueMap-R2-Fix", ROOT_BLUEMAP_R2_FIX),
            Map.entry("Root-ItemInfo", ROOT_ITEMINFO),
            Map.entry("Root-Webstat", ROOT_WEBSTAT),
            Map.entry("Root-Try", ROOT_TRY),
            Map.entry("Root-Haste", ROOT_HASTE),
            Map.entry("Root-Joint", ROOT_HASTE),
            Map.entry("Root-Torch", ROOT_TORCH),
            Map.entry("RootMC-Official", ROOTMC_OFFICIAL),
            Map.entry("Root-Ping", ROOT_PING));

    /** Path segment used on bstats.org (before URL-encoding). */
    private static final Map<String, String> BSTATS_PATH_NAME = Map.ofEntries(
            Map.entry("Root-Haste", "Root Haste"),
            Map.entry("Root-Joint", "Root Joint"),
            Map.entry("Root-Torch", "Root-Torch"),
            Map.entry("Root-Core", "Root-Core"),
            Map.entry("Root-Times", "Root-Times"),
            Map.entry("Root-Perms", "Root-Perms"),
            Map.entry("Root-Essentials", "Root-Essentials"),
            Map.entry("Root-Claims", "Root-Claims"),
            Map.entry("Root-Territories", "Root-Territories"),
            Map.entry("RootMC", "RootMC"),
            Map.entry("Root-Play", "Root-Play"),
            Map.entry("Root-Ops", "Root-Ops"),
            Map.entry("Root-BlueMap-R2-Fix", "Root-BlueMap-R2-Fix"),
            Map.entry("Root-ItemInfo", "Root-ItemInfo"),
            Map.entry("Root-Webstat", "Root-Webstat"),
            Map.entry("Root-Try", "Root-Try"),
            Map.entry("RootMC-Official", "RootMC-Official"),
            Map.entry("Root-Ping", "Root-Ping"));

    private BStatsIds() {}

    /** @return bStats service id, or 0 if unknown */
    public static int forPlugin(JavaPlugin plugin) {
        if (plugin == null) {
            return 0;
        }
        String name = plugin.getDescription().getName();
        if (name == null || name.isBlank()) {
            return 0;
        }
        return BY_PLUGIN_YML_NAME.getOrDefault(name.trim(), 0);
    }

    public static boolean isRegistered(int pluginId) {
        return pluginId > 0;
    }

    /** Public dashboard URL for a {@code plugin.yml} name, or null. */
    public static String urlFor(String pluginYmlName) {
        if (pluginYmlName == null || pluginYmlName.isBlank()) {
            return null;
        }
        Integer id = BY_PLUGIN_YML_NAME.get(pluginYmlName.trim());
        if (id == null || id <= 0) {
            return null;
        }
        String pathName = BSTATS_PATH_NAME.getOrDefault(pluginYmlName.trim(), pluginYmlName.trim());
        return "https://bstats.org/plugin/bukkit/" + encodePath(pathName) + "/" + id;
    }

    private static String encodePath(String name) {
        return name.replace(" ", "%20");
    }
}
