package com.rootrecord.minecraft.rootping;

import org.bukkit.configuration.file.FileConfiguration;

/** Settings from plugins/RootMC/root-ping.yml. */
public final class PingConfig {

    private final boolean enabled;
    private final int intervalMinutes;
    private final String serverIdOverride;
    private final String hostLabelOverride;
    private final String prefix;
    private final String pingLine;
    private final String serverLine;
    private final String disabled;
    private final String noPermission;

    public PingConfig(FileConfiguration cfg) {
        this.enabled = cfg.getBoolean("enabled", true);
        this.intervalMinutes = Math.max(1, cfg.getInt("interval-minutes", 5));
        this.serverIdOverride = trim(cfg.getString("server-id", ""));
        this.hostLabelOverride = trim(cfg.getString("host-label", ""));
        this.prefix = cfg.getString("messages.prefix", "");
        this.pingLine = cfg.getString("messages.ping-line", "&7Ping: &f{ping}ms");
        this.serverLine = cfg.getString(
                "messages.server-line",
                "&7Server: &f{tps} TPS &8· &f{mspt} MSPT &8· &f{online}/{max} &8· &7heap &f{heap}%");
        this.disabled = cfg.getString("messages.disabled", "&cRoot-Ping is disabled.");
        this.noPermission = cfg.getString("messages.no-permission", "&cNo permission.");
    }

    public boolean enabled() {
        return enabled;
    }

    public int intervalMinutes() {
        return intervalMinutes;
    }

    public String serverIdOverride() {
        return serverIdOverride;
    }

    public String hostLabelOverride() {
        return hostLabelOverride;
    }

    public String prefix() {
        return prefix;
    }

    public String pingLine() {
        return pingLine;
    }

    public String serverLine() {
        return serverLine;
    }

    public String disabled() {
        return disabled;
    }

    public String noPermission() {
        return noPermission;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
