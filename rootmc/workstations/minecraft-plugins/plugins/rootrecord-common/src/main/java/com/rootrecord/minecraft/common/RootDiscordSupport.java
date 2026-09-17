package com.rootrecord.minecraft.common;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Soft-depend helpers for Paper Discord/Slack comms (owned by Root-Core).
 * Legacy Bukkit name {@code Root-Discord} still accepted during cutover.
 */
public final class RootDiscordSupport {

    /** Preferred host — Root-Core registers {@link RootDiscordApi}. */
    public static final String PLUGIN_NAME = "Root-Core";

    /** Retired jar name; remove from hosts after Core 1.7.6+. */
    public static final String LEGACY_PLUGIN_NAME = "Root-Discord";

    private RootDiscordSupport() {
    }

    /** True when Core (or legacy Root-Discord) can provide Discord/Slack posts. */
    public static boolean isPluginEnabled() {
        Plugin core = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (core != null && core.isEnabled()) {
            return true;
        }
        Plugin legacy = Bukkit.getPluginManager().getPlugin(LEGACY_PLUGIN_NAME);
        return legacy != null && legacy.isEnabled();
    }

    /**
     * Log once-style boot warning when Discord features will not post.
     * Safe to call every enable; does nothing when Root-Core (or legacy Discord) is loaded.
     */
    public static void warnIfMissing(Plugin consumer, String features) {
        if (consumer == null || isPluginEnabled()) {
            return;
        }
        String featureText = features == null || features.isBlank() ? "Discord features" : features;
        consumer.getLogger().warning(
                "Root-Core (comms) not available — " + featureText
                        + " will not post. Ensure Root-Core is installed and set cloud.yml discord.* / slack.*.");
    }
}
