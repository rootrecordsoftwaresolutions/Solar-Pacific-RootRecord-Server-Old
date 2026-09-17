package com.rootrecord.minecraft.rootcore.api;

import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import com.rootrecord.minecraft.common.config.RootMcDiscordConfig;
import com.rootrecord.minecraft.rootcore.license.LicenseStatus;

/**
 * Bukkit ServicesManager contract for RootMC feature plugins.
 * Resolve via {@code Bukkit.getServicesManager().getRegistration(RootCoreApi.class)}.
 */
public interface RootCoreApi {

    /** True after successful on-enable ensure (folder + shared YAMLs usable). */
    boolean isReady();

    /** Resolved MySQL settings (plugin overrides → database.yml → legacy plugin yml). */
    RootMcDatabaseConfig.DatabaseSettings databaseSettings();

    /** Cloud API base URL (no trailing slash), default {@code https://api.rootmc.net}. */
    String apiBase();

    /** {@code cloud.server-id} or empty. */
    String serverId();

    /**
     * Display name from {@code root-core.yml} {@code server-name} (license bind name).
     * Never blank — falls back to {@code "Server"}.
     */
    String serverName();

    /** True when both server-id and server-secret are non-blank. */
    boolean hasCloudCredentials();

    /** Discord bot / named channels from {@code cloud.yml} ({@code discord.*}). */
    RootMcDiscordConfig.DiscordSettings discordSettings();

    /** License snapshot for a plugin id (e.g. {@code Root-Times}). */
    LicenseStatus licenseStatus(String pluginId);

    /**
     * Whether the named plugin may enable commercial features.
     * Stub: true in {@code license.mode: operator}; enforce mode still allows this pass (grace).
     */
    boolean isLicensed(String pluginId);

    /** Re-run connection self-repair (missing keys only; secrets preserved). */
    void ensureCoreFiles();
}
