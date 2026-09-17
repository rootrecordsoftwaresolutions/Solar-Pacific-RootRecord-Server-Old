package com.rootrecord.minecraft.rootcore.license;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootMcApiBases;
import com.rootrecord.minecraft.common.config.RootRecordCloudConfig;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.rootcore.RootCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * Reads product-key + server-name from root-core.yml (preferred) or legacy license.yml,
 * binds to the license API, fills blank identity into cloud.yml (server-id mirrored in root-core.yml), schedules presence.
 */
public final class LicenseConnectService {

    private final RootCorePlugin plugin;
    private final LicenseConnectClient client = new LicenseConnectClient();
    private final AtomicReference<String> boundServerId = new AtomicReference<>("");
    private final AtomicReference<String> lastPresenceOk = new AtomicReference<>("");
    private final AtomicReference<String> lastPresenceError = new AtomicReference<>("");
    private volatile boolean productKeyPresent;
    private volatile String serverName = "";
    private BukkitTask presenceTask;

    public LicenseConnectService(RootCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void startOrReload() {
        stop();
        applyLicenseApiBaseFromConfig();
        FileConfiguration rootCore = plugin.yamlConfig() != null ? plugin.yamlConfig().config() : null;
        int minutes = rootCore != null ? Math.max(1, rootCore.getInt("license.presence-interval-minutes", 5)) : 5;
        long intervalTicks = minutes * 60L * 20L;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::bindAndPresenceSafe);
        presenceTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, this::presenceSafe, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (presenceTask != null) {
            presenceTask.cancel();
            presenceTask = null;
        }
    }

    public boolean productKeyPresent() {
        return productKeyPresent;
    }

    public String boundServerId() {
        return boundServerId.get();
    }

    public String serverName() {
        return serverName;
    }

    public String lastPresenceOk() {
        return lastPresenceOk.get();
    }

    public String lastPresenceError() {
        return lastPresenceError.get();
    }

    public String licenseApiBase() {
        return client.licenseApiBase();
    }

    private void applyLicenseApiBaseFromConfig() {
        FileConfiguration rootCore = plugin.yamlConfig() != null ? plugin.yamlConfig().config() : null;
        String override = rootCore != null ? rootCore.getString("license.api-base", "") : "";
        if (override == null || override.isBlank()) {
            RootRecordCloudConfig.CloudSettings shared = RootRecordCloudConfig.resolve(plugin, rootCore);
            override = shared != null ? shared.apiBase() : "";
        }
        if (override == null || override.isBlank()) {
            override = RootMcApiBases.defaultBase();
        }
        // Opt-in only: operator PCs that intentionally use the local tunnel edge.
        boolean forceLocal = rootCore != null && rootCore.getBoolean("license.force-local-edge", false);
        if (forceLocal
                && RootMcApiBases.PRODUCTION.equalsIgnoreCase(RootMcApiBases.normalize(override))) {
            override = RootMcApiBases.LOCAL_EDGE;
        }
        client.setLicenseApiBase(override);
    }

    private void bindAndPresenceSafe() {
        try {
            bindAndPresence();
        } catch (Exception ex) {
            lastPresenceError.set(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            plugin.getLogger().log(Level.WARNING, "Root-Core license bind/presence failed: " + lastPresenceError.get());
        }
    }

    private void presenceSafe() {
        try {
            presenceOnly();
        } catch (Exception ex) {
            lastPresenceError.set(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            plugin.getLogger().log(Level.WARNING, "Root-Core license presence failed: " + lastPresenceError.get());
        }
    }

    private void bindAndPresence() throws Exception {
        LicenseFile lic = readLicense();
        productKeyPresent = lic.productKey != null && !lic.productKey.isBlank();
        serverName = lic.serverName;
        if (!productKeyPresent) {
            plugin.getLogger().info(
                    "Root-Core license: no product-key in root-core.yml — skipping bind/presence (operator mode OK).");
            return;
        }
        if (serverName == null || serverName.isBlank()) {
            serverName = "Minecraft server";
        }

        RootRecordCloudConfig.CloudSettings cloud = resolveCloudSettings();
        String existingId = cloud != null ? cloud.serverId() : "";
        boolean needSecret = isBlankSecret(cloud != null ? cloud.serverSecret() : null);
        LicenseConnectClient.BindResult bind =
                client.bind(lic.productKey, serverName, existingId, needSecret);
        if (bind.serverId() != null && !bind.serverId().isBlank()) {
            boundServerId.set(bind.serverId());
        }
        maybeWriteCloudIdentity(cloud, bind, needSecret);
        if (plugin.api() instanceof com.rootrecord.minecraft.rootcore.api.RootCoreApiImpl impl) {
            Bukkit.getScheduler().runTask(plugin, impl::refresh);
        }
        presenceOnly();
        String secretNote = !isBlankSecret(bind.serverSecret()) ? " secret=filled" : " secret=unchanged";
        plugin.getLogger().info(
                "Root-Core license bound server-id="
                        + boundServerId.get()
                        + (bind.created() ? " (created)" : " (existing)")
                        + secretNote
                        + " — presence scheduled");
    }

    private void presenceOnly() throws Exception {
        LicenseFile lic = readLicense();
        productKeyPresent = lic.productKey != null && !lic.productKey.isBlank();
        if (!productKeyPresent) {
            return;
        }
        if (lic.serverName != null && !lic.serverName.isBlank()) {
            serverName = lic.serverName;
        }
        String sid = boundServerId.get();
        if (sid == null || sid.isBlank()) {
            RootRecordCloudConfig.CloudSettings cloud = resolveCloudSettings();
            sid = cloud != null ? cloud.serverId() : "";
        }
        String address = resolveServerAddress();
        LicenseConnectClient.PresenceResult result = client.presence(
                lic.productKey,
                serverName == null || serverName.isBlank() ? "Minecraft server" : serverName,
                sid,
                address,
                plugin.getDescription().getVersion(),
                Bukkit.getOnlinePlayers().size());
        if (result.serverId() != null && !result.serverId().isBlank()) {
            boundServerId.set(result.serverId());
        }
        lastPresenceOk.set(result.seenAt() != null && !result.seenAt().isBlank()
                ? result.seenAt()
                : java.time.Instant.now().toString());
        lastPresenceError.set("");
    }

    /**
     * API base: root-core.yml → cloud.yml → production.
     * Identity secrets: cloud.yml first (root-core.yml secret is migration fallback only).
     */
    private RootRecordCloudConfig.CloudSettings resolveCloudSettings() {
        FileConfiguration rootCore = plugin.yamlConfig() != null ? plugin.yamlConfig().config() : null;
        String fromCoreBase = rootCore != null ? trim(rootCore.getString("cloud.api-base")) : "";
        String fromCoreId = rootCore != null ? trim(rootCore.getString("cloud.server-id")) : "";
        String fromCoreSecret = rootCore != null ? trim(rootCore.getString("cloud.server-secret")) : "";
        RootRecordCloudConfig.CloudSettings shared = RootRecordCloudConfig.resolve(plugin, null);
        return new RootRecordCloudConfig.CloudSettings(
                firstNonBlank(fromCoreBase, shared != null ? shared.apiBase() : "", RootMcApiBases.defaultBase()),
                firstNonBlank(shared != null ? shared.serverId() : "", fromCoreId),
                firstNonBlank(shared != null ? shared.serverSecret() : "", fromCoreSecret));
    }

    private void maybeWriteCloudIdentity(
            RootRecordCloudConfig.CloudSettings current,
            LicenseConnectClient.BindResult bind,
            boolean requestedSecret) {
        if (bind.serverId() == null || bind.serverId().isBlank()) {
            return;
        }
        boolean haveSecret = !isBlankSecret(bind.serverSecret());
        boolean needSecret = requestedSecret || current == null || isBlankSecret(current.serverSecret());
        String bindApiBase = trim(bind.apiBase());

        File file = RootRecordFolders.configFile(plugin, RootRecordCloudConfig.FILE_NAME);
        RootRecordCloudConfig.ensureDefaults(plugin);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        boolean needId = isBlankSecret(yaml.getString("cloud.server-id"))
                || current == null
                || isBlankSecret(current.serverId());
        boolean fileApiBlank = isBlankSecret(yaml.getString("cloud.api-base"));
        boolean writeCloud = needId || (needSecret && haveSecret) || fileApiBlank;

        // Operator visibility: server-id only in root-core.yml (never server-secret).
        stripSecretAndMirrorServerId(bind.serverId());

        if (!writeCloud) {
            if (requestedSecret && !haveSecret) {
                plugin.getLogger().warning(
                        "Root-Core bind did not return server_secret (issue_secret requested) — cloud identity incomplete");
            }
            return;
        }

        if (needId || haveSecret) {
            yaml.set("cloud.server-id", bind.serverId());
        }
        if (needSecret && haveSecret) {
            yaml.set("cloud.server-secret", bind.serverSecret());
        }
        if (fileApiBlank) {
            if (!bindApiBase.isBlank()) {
                yaml.set("cloud.api-base", RootMcApiBases.normalize(bindApiBase));
            } else {
                yaml.set("cloud.api-base", resolveConfiguredDataPlaneBase());
            }
        }
        try {
            yaml.save(file);
            plugin.getLogger().info(
                    "Root-Core wrote cloud identity into cloud.yml"
                            + " server-id="
                            + bind.serverId()
                            + (haveSecret ? " (secret filled)" : " (secret unchanged)"));
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Could not write cloud.yml identity from bind", ex);
        }
    }

    private void stripSecretAndMirrorServerId(String serverId) {
        if (plugin.yamlConfig() == null) {
            return;
        }
        FileConfiguration rootCore = plugin.yamlConfig().config();
        if (serverId != null && !serverId.isBlank()) {
            rootCore.set("cloud.server-id", serverId);
        }
        String existingCoreSecret = trim(rootCore.getString("cloud.server-secret"));
        if (!existingCoreSecret.isBlank()) {
            rootCore.set("cloud.server-secret", "");
        }
        plugin.yamlConfig().save();
    }

    private String resolveConfiguredDataPlaneBase() {
        FileConfiguration rootCore = plugin.yamlConfig() != null ? plugin.yamlConfig().config() : null;
        String fromCore = rootCore != null ? trim(rootCore.getString("cloud.api-base")) : "";
        if (!fromCore.isBlank()) {
            return RootMcApiBases.normalize(fromCore);
        }
        return RootMcApiBases.defaultBase();
    }

    /** Prefer root-core.yml; fall back to legacy license.yml. */
    private LicenseFile readLicense() {
        FileConfiguration rootCore = plugin.yamlConfig() != null ? plugin.yamlConfig().config() : null;
        String key = rootCore != null ? trim(rootCore.getString("product-key")) : "";
        String name = rootCore != null ? trim(rootCore.getString("server-name")) : "";
        if (key.isBlank() || name.isBlank()) {
            RootRecordYamlConfig legacy =
                    new RootRecordYamlConfig(plugin, RootRecordFolders.LICENSE_CONFIG, "license.yml");
            legacy.load();
            FileConfiguration cfg = legacy.config();
            if (key.isBlank()) {
                key = trim(cfg.getString("product-key"));
            }
            if (name.isBlank()) {
                name = trim(cfg.getString("server-name"));
            }
        }
        return new LicenseFile(key, name);
    }

    private String resolveServerAddress() {
        FileConfiguration rootCore = plugin.yamlConfig() != null ? plugin.yamlConfig().config() : null;
        if (rootCore != null) {
            String fromTransfer = trim(rootCore.getString("transfer.advertise"));
            if (!fromTransfer.isBlank()) {
                return fromTransfer;
            }
            String fromCfg = trim(rootCore.getString("license.server-address"));
            if (!fromCfg.isBlank()) {
                return fromCfg;
            }
        }
        // Do not use MOTD — Shockbyte/color codes break JSON. Prefer empty until configured.
        return "";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static boolean isBlankSecret(String v) {
        if (v == null) {
            return true;
        }
        String t = v.trim();
        return t.isEmpty() || "''".equals(t) || "\"\"".equals(t) || "null".equalsIgnoreCase(t);
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }

    private record LicenseFile(String productKey, String serverName) {}
}
