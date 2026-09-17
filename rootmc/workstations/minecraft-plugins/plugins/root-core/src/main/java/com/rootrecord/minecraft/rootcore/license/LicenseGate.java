package com.rootrecord.minecraft.rootcore.license;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Commercial product-key gate scaffold.
 * This pass: {@code operator} always allows; {@code enforce} still allows (UNVERIFIED/GRACE) so Gen1/Gen2 stay up.
 */
public final class LicenseGate {

    public enum Mode {
        OPERATOR,
        ENFORCE
    }

    private final JavaPlugin plugin;
    private final AtomicBoolean operatorLogged = new AtomicBoolean(false);
    private final AtomicBoolean enforceGraceLogged = new AtomicBoolean(false);
    private volatile Mode mode = Mode.OPERATOR;
    private volatile boolean verifyRemote;

    public LicenseGate(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload(FileConfiguration rootCoreConfig) {
        String raw = rootCoreConfig != null
                ? rootCoreConfig.getString("license.mode", "operator")
                : "operator";
        mode = parseMode(raw);
        verifyRemote = rootCoreConfig != null && rootCoreConfig.getBoolean("license.verify-remote", false);
        if (mode == Mode.OPERATOR && operatorLogged.compareAndSet(false, true)) {
            plugin.getLogger().info("License gate: operator mode — commercial enforce disabled");
        }
        if (mode == Mode.ENFORCE && enforceGraceLogged.compareAndSet(false, true)) {
            plugin.getLogger().warning(
                    "License gate: enforce mode active but product-key verify is stubbed — "
                            + "plugins remain allowed (UNVERIFIED/GRACE). Set license.mode: operator for production.");
        }
    }

    public Mode mode() {
        return mode;
    }

    public boolean verifyRemoteEnabled() {
        return verifyRemote;
    }

    public LicenseStatus status(String pluginId) {
        if (mode == Mode.OPERATOR) {
            return LicenseStatus.OPERATOR;
        }
        // Enforce path: no remote verify this pass → grace/unverified, still allow.
        return LicenseStatus.UNVERIFIED;
    }

    public boolean isLicensed(String pluginId) {
        LicenseStatus status = status(pluginId);
        return status == LicenseStatus.OPERATOR
                || status == LicenseStatus.LICENSED
                || status == LicenseStatus.UNVERIFIED
                || status == LicenseStatus.GRACE;
    }

    /**
     * Optional async verify hook for a future {@code /api/rootmc/license/verify}.
     * No-ops unless {@code license.verify-remote: true} (default off). Never blocks the main thread.
     */
    public void verifyAsync(String pluginId, Runnable onComplete) {
        Logger log = plugin.getLogger();
        if (!verifyRemote) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            log.info("License verifyAsync stub for " + pluginId + " — remote endpoint not wired yet");
            if (onComplete != null) {
                plugin.getServer().getScheduler().runTask(plugin, onComplete);
            }
        });
    }

    private static Mode parseMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return Mode.OPERATOR;
        }
        String n = raw.trim().toLowerCase(Locale.ROOT);
        if ("enforce".equals(n) || "enforced".equals(n) || "commercial".equals(n)) {
            return Mode.ENFORCE;
        }
        return Mode.OPERATOR;
    }
}
