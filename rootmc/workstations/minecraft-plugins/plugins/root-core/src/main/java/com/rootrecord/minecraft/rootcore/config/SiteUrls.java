package com.rootrecord.minecraft.rootcore.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Public site / catalog URLs from {@code root-core.yml} {@code site.*}
 * (Cloudflare Pages by default).
 */
public final class SiteUrls {

    public static final String DEFAULT_BASE = "https://rootmc.net";
    public static final String DEFAULT_KEYS_PATH = "/developer/keys/";
    public static final String DEFAULT_MANIFEST = "https://rootmc.net/plugins/manifest.json";
    /** Pages.dev alias — use when apex rootmc.net times out (20–90s). */
    public static final String FALLBACK_MANIFEST = "https://rootmc.info/plugins/manifest.json";

    private SiteUrls() {}

    public static String siteBase(FileConfiguration cfg) {
        String base = cfg != null ? trim(cfg.getString("site.base")) : "";
        return base.isBlank() ? DEFAULT_BASE : stripSlash(base);
    }

    public static String keysPath(FileConfiguration cfg) {
        String path = cfg != null ? trim(cfg.getString("site.keys-path")) : "";
        if (path.isBlank()) {
            path = DEFAULT_KEYS_PATH;
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

    public static String keysUrl(FileConfiguration cfg) {
        return siteBase(cfg) + keysPath(cfg);
    }

    public static String pluginsManifest(FileConfiguration cfg) {
        String fromUpdater = cfg != null ? trim(cfg.getString("updater.manifest-url")) : "";
        if (!fromUpdater.isBlank()) {
            return fromUpdater;
        }
        String fromSite = cfg != null ? trim(cfg.getString("site.plugins-manifest")) : "";
        if (!fromSite.isBlank()) {
            return fromSite;
        }
        return siteBase(cfg) + "/plugins/manifest.json";
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }

    private static String stripSlash(String base) {
        return base.replaceAll("/+$", "");
    }
}
