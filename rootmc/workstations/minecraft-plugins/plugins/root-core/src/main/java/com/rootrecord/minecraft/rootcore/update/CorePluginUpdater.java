package com.rootrecord.minecraft.rootcore.update;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.rootcore.RootCorePlugin;
import com.rootrecord.minecraft.rootcore.config.SiteUrls;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Opt-in suite updater: poll rootmc.net/plugins/manifest.json, download newer jars,
 * prune older same-plugin jars. Restart Paper to load them (no hot-swap).
 */
public final class CorePluginUpdater {

    private static final String DEFAULT_MANIFEST = SiteUrls.DEFAULT_MANIFEST;
    /** Spine fallback when force-ensure-all is off. force-ensure-all (default on) installs every manifest jar. */
    private static final List<String> DEFAULT_ENSURE = List.of(
            "root-ava-core",
            "root-times",
            "root-perms",
            "root-economy",
            "root-essentials",
            "root-claims",
            "root-territories",
            "root-play",
            "root-skills",
            "root-ops",
            "root-appreciation",
            "root-chestshops",
            "root-market",
            "root-gamble",
            "root-iteminfo",
            "root-webstat",
            "root-ping",
            "root-try",
            "root-referrals",
            "root-memberships",
            "root-haste",
            "root-heads",
            "root-bluemap-r2-fix",
            "rootmc",
            "rootmc-official");

    private final RootCorePlugin plugin;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(45)).build();
    private final AtomicReference<String> lastResult = new AtomicReference<>("never");
    private final AtomicReference<String> lastError = new AtomicReference<>("");
    private BukkitTask task;
    private volatile boolean enabled;
    private volatile boolean pastStartup;
    private volatile String manifestUrl = DEFAULT_MANIFEST;
    /** Jars that failed to load (bad plugin.yml etc.) — do not restart-loop on them. */
    private final Set<String> unloadableJars = ConcurrentHashMap.newKeySet();

    public CorePluginUpdater(RootCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void startOrReload() {
        stop();
        FileConfiguration cfg = plugin.yamlConfig() != null ? plugin.yamlConfig().config() : null;
        enabled = cfg == null || cfg.getBoolean("updater.enabled", true);
        manifestUrl = SiteUrls.pluginsManifest(cfg);
        if (!enabled) {
            lastResult.set("disabled");
            plugin.getLogger().info("Root-Core updater: disabled (updater.enabled: false)");
            return;
        }
        int hours = cfg != null ? Math.max(1, cfg.getInt("updater.interval-hours", 6)) : 6;
        long intervalTicks = hours * 60L * 60L * 20L;
        pastStartup = false;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> pastStartup = true, 20L * 30);
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, this::checkSafe, 20L * 40);
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, this::checkSafe, intervalTicks, intervalTicks);
        boolean forceAll = cfg == null || cfg.getBoolean("updater.force-ensure-all", true);
        plugin.getLogger().info(
                "Root-Core updater: enabled — manifest=" + manifestUrl + " every " + hours + "h"
                        + (forceAll ? " · force-ensure-all" : ""));
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public String lastResult() {
        return lastResult.get();
    }

    public String lastError() {
        return lastError.get();
    }

    public String manifestUrl() {
        return manifestUrl;
    }

    public boolean forceEnsureAll() {
        FileConfiguration cfg = plugin.yamlConfig() != null ? plugin.yamlConfig().config() : null;
        return cfg == null || cfg.getBoolean("updater.force-ensure-all", true);
    }

    /** Manual check from /rootcore update (async). */
    public void checkNowAsync(Runnable onDone) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            checkSafe();
            if (onDone != null) {
                Bukkit.getScheduler().runTask(plugin, onDone);
            }
        });
    }

    private void checkSafe() {
        try {
            runCheck();
        } catch (Exception ex) {
            lastError.set(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            lastResult.set("fail");
            plugin.getLogger().log(Level.WARNING, "Root-Core updater failed: " + lastError.get(), ex);
        }
    }

    private void runCheck() throws Exception {
        FileConfiguration cfg = plugin.yamlConfig() != null ? plugin.yamlConfig().config() : null;
        if (cfg != null && !cfg.getBoolean("updater.enabled", true)) {
            enabled = false;
            lastResult.set("disabled");
            return;
        }
        enabled = true;
        if (cfg.getBoolean("updater.require-product-key", false)) {
            var connect = plugin.licenseConnect();
            if (connect == null || !connect.productKeyPresent()) {
                lastResult.set("skipped-no-product-key");
                lastError.set("");
                return;
            }
        }

        String body = fetchManifest(manifestUrl);
        Map<String, PluginManifest.Entry> manifest = PluginManifest.parse(body);
        if (manifest.isEmpty()) {
            throw new IOException("Empty or unreadable plugin manifest");
        }

        boolean forceAll = cfg == null || cfg.getBoolean("updater.force-ensure-all", true);
        Set<String> allow = forceAll ? Set.of() : allowlist(cfg);
        Set<String> ensure = ensurelist(cfg);
        boolean onlyInstalled = !forceAll && cfg.getBoolean("updater.only-installed", true);
        int downloaded = 0;
        int upToDate = 0;
        int skipped = 0;
        List<String> notes = new ArrayList<>();

        for (PluginManifest.Entry entry : manifest.values()) {
            if (!forceAll
                    && !allow.isEmpty()
                    && !allow.contains(entry.id().toLowerCase(Locale.ROOT))
                    && !ensure.contains(entry.id().toLowerCase(Locale.ROOT))) {
                skipped++;
                continue;
            }
            if (!forceAll
                    && onlyInstalled
                    && !ensure.contains(entry.id().toLowerCase(Locale.ROOT))
                    && !isInstalledOrPresent(entry.id())) {
                skipped++;
                continue;
            }
            if (pastStartup && alreadyHaveFile(entry) && findLoadedPlugin(entry.id()) == null) {
                Path stale = RootRecordFolders.pluginsDir(plugin).toPath().resolve(entry.filename());
                String unloadKey = entry.filename() + "@" + entry.version();
                if (unloadableJars.contains(unloadKey) || isUnloadablePluginJar(stale)) {
                    unloadableJars.add(unloadKey);
                    plugin.getLogger().severe(
                            "Updater: " + entry.filename()
                                    + " on disk but unloadable (bad plugin.yml?) — skipping restart loop."
                                    + " Replace the jar on rootmc.net/plugins.");
                    skipped++;
                    continue;
                }
                try {
                    Files.deleteIfExists(stale);
                    plugin.getLogger().warning(
                            "Updater: " + entry.filename() + " on disk but not loaded — re-downloading");
                    notes.add(entry.id() + " reload (not loaded)");
                } catch (IOException ex) {
                    plugin.getLogger().warning(
                            "Updater could not replace unloaded " + entry.filename() + ": " + ex.getMessage());
                }
            }
            String local = localVersion(entry.id());
            String diskNewest = newestJarVersion(entry.id());
            // Prefer disk truth when duplicates exist (Paper may load the older jar).
            String effectiveLocal = newerVersion(local, diskNewest);

            if (alreadyHaveFile(entry)) {
                // Always drop sibling jars; never delete the jar Paper currently has open.
                pruneOlderJars(entry.id(), entry.filename());
            }

            if (effectiveLocal != null
                    && PluginManifest.compareVersions(entry.version(), effectiveLocal) <= 0) {
                upToDate++;
                // Still need a restart if Paper is running an older jar than the kept file.
                if (local != null
                        && PluginManifest.compareVersions(entry.version(), local) > 0
                        && alreadyHaveFile(entry)) {
                    notes.add(entry.id() + " " + local + " → " + entry.version() + " (on disk)");
                    downloaded++; // treat as pending apply
                }
                continue;
            }
            if (alreadyHaveFile(entry) && alreadyMarked(entry)) {
                upToDate++;
                if (local != null && PluginManifest.compareVersions(entry.version(), local) > 0) {
                    notes.add(entry.id() + " " + local + " → " + entry.version() + " (on disk)");
                    downloaded++;
                }
                continue;
            }
            if (download(entry)) {
                Path target = RootRecordFolders.pluginsDir(plugin).toPath().resolve(entry.filename());
                if (isUnloadablePluginJar(target)) {
                    unloadableJars.add(entry.filename() + "@" + entry.version());
                    plugin.getLogger().severe(
                            "Updater: downloaded " + entry.filename()
                                    + " but plugin.yml is invalid — not scheduling restart.");
                    skipped++;
                } else {
                    downloaded++;
                    notes.add(entry.id() + " " + (local == null ? "?" : local) + " → " + entry.version());
                }
            }
        }

        lastError.set("");
        boolean restartAfter = cfg == null || cfg.getBoolean("updater.restart-after-update", true);
        boolean restartInMaint = cfg == null || cfg.getBoolean("updater.restart-in-maintenance", true);
        int restartDelay = cfg != null ? Math.max(5, cfg.getInt("updater.restart-delay-seconds", 30)) : 30;

        if (downloaded > 0) {
            markPendingRestart(true, notes);
        }

        boolean pending = isPendingRestart() || downloaded > 0;
        if (pending && restartAfter) {
            boolean inMaint = !restartInMaint || fetchMaintenanceActive();
            if (!inMaint) {
                lastResult.set("downloaded " + Math.max(downloaded, 1)
                        + " — deferred until maintenance window");
                plugin.getLogger().info(
                        "Root-Core updater: jar(s) ready — auto-restart deferred until maintenance window"
                                + (downloaded > 0 ? (" (" + String.join(", ", notes) + ")") : "")
                                + ". up-to-date=" + upToDate + " skipped=" + skipped);
            } else {
                List<String> restartNotes = downloaded > 0 ? notes : pendingNotes();
                clearPendingRestart();
                lastResult.set("downloaded — Root-Restart countdown");
                plugin.getLogger().info(
                        "Root-Core updater: restarting via Root-Restart (midnight path) after: "
                                + String.join(", ", restartNotes)
                                + ". up-to-date=" + upToDate + " skipped=" + skipped);
                SuiteRestart.scheduleAfterUpdates(plugin, restartDelay, restartNotes);
            }
        } else if (downloaded > 0) {
            lastResult.set("downloaded " + downloaded + " — restart Paper to apply");
            plugin.getLogger().info(
                    "Root-Core updater: downloaded "
                            + downloaded
                            + " jar(s): "
                            + String.join(", ", notes)
                            + " — restart Paper to apply (updater.restart-after-update: false). up-to-date="
                            + upToDate
                            + " skipped="
                            + skipped);
        } else {
            lastResult.set("ok up-to-date=" + upToDate + " skipped=" + skipped);
            plugin.getLogger().info(
                    "Root-Core updater: no downloads (up-to-date=" + upToDate + " skipped=" + skipped + ")");
        }
    }

    private Set<String> ensurelist(FileConfiguration cfg) {
        Set<String> out = new LinkedHashSet<>(DEFAULT_ENSURE);
        if (cfg == null) {
            return out;
        }
        List<String> list = cfg.getStringList("updater.ensure");
        if (list != null) {
            for (String s : list) {
                if (s != null && !s.isBlank()) {
                    out.add(s.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return out;
    }

    private Set<String> allowlist(FileConfiguration cfg) {
        Set<String> out = new LinkedHashSet<>();
        List<String> list = cfg.getStringList("updater.plugins");
        if (list != null) {
            for (String s : list) {
                if (s != null && !s.isBlank()) {
                    out.add(s.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return out;
    }

    private String fetchManifest(String url) throws IOException, InterruptedException {
        List<String> urls = new ArrayList<>();
        if (url != null && !url.isBlank()) {
            urls.add(url.trim());
        }
        for (String fallback : List.of(SiteUrls.DEFAULT_MANIFEST, SiteUrls.FALLBACK_MANIFEST)) {
            if (!urls.contains(fallback)) {
                urls.add(fallback);
            }
        }
        IOException last = null;
        for (int i = 0; i < urls.size(); i++) {
            String candidate = urls.get(i);
            try {
                return fetchManifestOnce(candidate);
            } catch (InterruptedException ie) {
                throw ie;
            } catch (IOException ex) {
                last = ex;
                plugin.getLogger().warning(
                        "Root-Core updater: manifest fetch failed (" + candidate + "): " + ex.getMessage()
                                + (i + 1 < urls.size() ? " — trying fallback" : ""));
            }
        }
        throw last != null ? last : new IOException("manifest fetch failed");
    }

    private String fetchManifestOnce(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(90))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("manifest HTTP " + response.statusCode());
        }
        String body = response.body();
        if (body == null || body.isBlank()) {
            throw new IOException("empty manifest body");
        }
        return body;
    }

    private boolean isInstalledOrPresent(String manifestId) {
        if (findLoadedPlugin(manifestId) != null) {
            return true;
        }
        return !listJarsFor(manifestId).isEmpty();
    }

    private String localVersion(String manifestId) {
        Plugin loaded = findLoadedPlugin(manifestId);
        if (loaded != null) {
            return loaded.getDescription().getVersion();
        }
        // Fall back to newest jar filename on disk: name-1.2.3.jar
        String best = null;
        for (Path jar : listJarsFor(manifestId)) {
            String ver = versionFromJarName(manifestId, jar.getFileName().toString());
            if (ver == null) {
                continue;
            }
            if (best == null || PluginManifest.compareVersions(ver, best) > 0) {
                best = ver;
            }
        }
        return best;
    }

    private Plugin findLoadedPlugin(String manifestId) {
        String bukkit = bukkitName(manifestId);
        Plugin p = Bukkit.getPluginManager().getPlugin(bukkit);
        if (p != null) {
            return p;
        }
        // Case-insensitive scan
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (plugin.getName().equalsIgnoreCase(bukkit)
                    || plugin.getName().equalsIgnoreCase(manifestId)) {
                return plugin;
            }
        }
        return null;
    }

    /** Manifest / gradle id → Bukkit plugin.yml name. */
    static String bukkitName(String manifestId) {
        if (manifestId == null) {
            return "";
        }
        return switch (manifestId.toLowerCase(Locale.ROOT)) {
            case "rootmc" -> "RootMC";
            case "rootmc-shops" -> "RootMC-Shops";
            case "roothelp" -> "RootHelp";
            case "root-core" -> "Root-Core";
            case "root-essentials" -> "Root-Essentials";
            case "root-times" -> "Root-Times";
            case "root-perms" -> "Root-Perms";
            case "rootmc-official" -> "RootMC-Official";
            case "root-claims" -> "Root-Claims";
            case "root-play" -> "Root-Play";
            case "root-ops" -> "Root-Ops";
            case "root-territories" -> "Root-Territories";
            case "root-rewards" -> "Root-Rewards";
            case "root-bluemap-r2-fix" -> "Root-BlueMap-R2-Fix";
            default -> {
                // root-foo → Root-Foo
                String[] parts = manifestId.split("-");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < parts.length; i++) {
                    if (i > 0) {
                        sb.append('-');
                    }
                    String p = parts[i];
                    if (p.isEmpty()) {
                        continue;
                    }
                    sb.append(Character.toUpperCase(p.charAt(0)));
                    if (p.length() > 1) {
                        sb.append(p.substring(1));
                    }
                }
                yield sb.toString();
            }
        };
    }

    private List<Path> listJarsFor(String manifestId) {
        List<Path> out = new ArrayList<>();
        Path dir = RootRecordFolders.pluginsDir(plugin).toPath();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        String prefix = manifestId.toLowerCase(Locale.ROOT) + "-";
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.jar")) {
            for (Path path : stream) {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.startsWith(prefix) && name.endsWith(".jar")) {
                    // Avoid rootmc-* matching rootmc-shops-*
                    String rest = name.substring(prefix.length());
                    if (rest.contains("-") && manifestId.equalsIgnoreCase("rootmc")) {
                        // rootmc-1.2.3.jar OK; rootmc-shops-1.jar has shops after first segment of version?
                        // rootmc-1.3.88.jar → rest = 1.3.88.jar — no letter-name prefix
                        // rootmc-shops-1.jar would be prefix rootmc- + shops-... — we use full id rootmc-shops
                        // For rootmc only: reject if rest starts with a non-digit word like "shops-"
                        if (rest.matches("^[a-z].*")) {
                            continue;
                        }
                    }
                    out.add(path);
                }
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not list plugin jars", ex);
        }
        return out;
    }

    private static String versionFromJarName(String manifestId, String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        String prefix = manifestId.toLowerCase(Locale.ROOT) + "-";
        if (!lower.startsWith(prefix) || !lower.endsWith(".jar")) {
            return null;
        }
        return filename.substring(prefix.length(), filename.length() - 4);
    }

    private boolean alreadyHaveFile(PluginManifest.Entry entry) {
        Path target = RootRecordFolders.pluginsDir(plugin).toPath().resolve(entry.filename());
        return Files.isRegularFile(target);
    }

    /** True when the jar cannot be described (YAML colon bugs, missing main, …). */
    private boolean isUnloadablePluginJar(Path jar) {
        if (jar == null || !Files.isRegularFile(jar)) {
            return true;
        }
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("plugin.yml");
            if (entry == null) {
                entry = zip.getEntry("paper-plugin.yml");
            }
            if (entry == null) {
                return true;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                new PluginDescriptionFile(in);
                return false;
            }
        } catch (InvalidDescriptionException | IOException | RuntimeException ex) {
            plugin.getLogger().warning(
                    "Updater: unloadable jar " + jar.getFileName() + " — " + ex.getMessage());
            return true;
        }
    }

    private boolean alreadyMarked(PluginManifest.Entry entry) {
        Path marker = RootRecordFolders.configFile(plugin, RootRecordFolders.DOWNLOADED_PLUGINS_STATE).toPath();
        if (!Files.isRegularFile(marker)) {
            return false;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(marker.toFile());
        return entry.version().equals(cfg.getString(entry.id() + ".version"));
    }

    private boolean download(PluginManifest.Entry entry) {
        Path pluginsDir = RootRecordFolders.pluginsDir(plugin).toPath();
        Path target = pluginsDir.resolve(entry.filename());
        Path temp = pluginsDir.resolve(entry.filename() + ".download");
        try {
            if (!Files.isRegularFile(target)) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(entry.url()))
                        .timeout(Duration.ofMinutes(3))
                        .GET()
                        .build();
                HttpResponse<InputStream> response =
                        http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() >= 400) {
                    plugin.getLogger().warning(
                            "Updater download failed (" + entry.filename() + "): HTTP " + response.statusCode());
                    return false;
                }
                try (InputStream in = response.body()) {
                    Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                }
                try {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException atomicFail) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            pruneOlderJars(entry.id(), entry.filename());
            markDownloaded(entry);
            return true;
        } catch (Exception ex) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Updater failed for " + entry.filename() + ": " + ex.getMessage(),
                    ex);
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // ignore
            }
            return false;
        }
    }

    private void pruneOlderJars(String manifestId, String keepFilename) {
        Plugin loaded = findLoadedPlugin(manifestId);
        String runningVer = loaded != null ? loaded.getDescription().getVersion() : null;
        for (Path jar : listJarsFor(manifestId)) {
            String name = jar.getFileName().toString();
            if (name.equalsIgnoreCase(keepFilename)) {
                continue;
            }
            String jarVer = versionFromJarName(manifestId, name);
            // Never delete the jar Paper has open (zip-closed crashes). Quarantine it so the
            // next boot cannot load the stale file — otherwise ambiguous dual jars loop forever.
            if (runningVer != null && jarVer != null && runningVer.equals(jarVer)) {
                quarantineLoadedJar(jar);
                continue;
            }
            try {
                Files.deleteIfExists(jar);
                plugin.getLogger().info("Updater removed old jar " + name);
            } catch (IOException ex) {
                plugin.getLogger().warning("Updater could not delete " + name + ": " + ex.getMessage());
            }
        }
    }

    /**
     * Move a currently-loaded stale jar aside ({@code .jar} → {@code .jar.disabled}).
     * Linux keeps the open inode for this process; Paper only discovers {@code *.jar} next boot.
     */
    private void quarantineLoadedJar(Path jar) {
        String name = jar.getFileName().toString();
        Path dest = jar.resolveSibling(name + ".disabled");
        try {
            Files.move(jar, dest, StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info(
                    "Updater quarantined loaded jar " + name + " → " + dest.getFileName()
                            + " (next restart will load the newer jar only)");
        } catch (IOException ex) {
            plugin.getLogger().warning(
                    "Updater could not quarantine " + name + ": " + ex.getMessage()
                            + " — delete it manually from plugins/ to stop restart loops");
        }
    }

    private String newestJarVersion(String manifestId) {
        String best = null;
        for (Path jar : listJarsFor(manifestId)) {
            String ver = versionFromJarName(manifestId, jar.getFileName().toString());
            if (ver == null) {
                continue;
            }
            if (best == null || PluginManifest.compareVersions(ver, best) > 0) {
                best = ver;
            }
        }
        return best;
    }

    private static String newerVersion(String a, String b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return PluginManifest.compareVersions(a, b) >= 0 ? a : b;
    }

    private void markDownloaded(PluginManifest.Entry entry) {
        try {
            RootRecordFolders.ensureDir(plugin);
            Path marker = RootRecordFolders.configFile(plugin, RootRecordFolders.DOWNLOADED_PLUGINS_STATE).toPath();
            YamlConfiguration cfg = Files.isRegularFile(marker)
                    ? YamlConfiguration.loadConfiguration(marker.toFile())
                    : new YamlConfiguration();
            cfg.set(entry.id() + ".version", entry.version());
            cfg.set(entry.id() + ".filename", entry.filename());
            cfg.set(entry.id() + ".at", System.currentTimeMillis());
            cfg.save(marker.toFile());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not write download marker: " + ex.getMessage());
        }
    }

    private Path pendingMarker() {
        return RootRecordFolders.configFile(plugin, RootRecordFolders.DOWNLOADED_PLUGINS_STATE).toPath();
    }

    private boolean isPendingRestart() {
        Path marker = pendingMarker();
        if (!Files.isRegularFile(marker)) {
            return false;
        }
        return YamlConfiguration.loadConfiguration(marker.toFile()).getBoolean("pending-restart", false);
    }

    private List<String> pendingNotes() {
        Path marker = pendingMarker();
        if (!Files.isRegularFile(marker)) {
            return List.of("pending plugin update(s)");
        }
        List<?> raw = YamlConfiguration.loadConfiguration(marker.toFile()).getList("pending-notes");
        if (raw == null || raw.isEmpty()) {
            return List.of("pending plugin update(s)");
        }
        List<String> out = new ArrayList<>();
        for (Object o : raw) {
            if (o != null) {
                out.add(String.valueOf(o));
            }
        }
        return out.isEmpty() ? List.of("pending plugin update(s)") : out;
    }

    private void markPendingRestart(boolean pending, List<String> notes) {
        try {
            RootRecordFolders.ensureDir(plugin);
            Path marker = pendingMarker();
            YamlConfiguration cfg = Files.isRegularFile(marker)
                    ? YamlConfiguration.loadConfiguration(marker.toFile())
                    : new YamlConfiguration();
            cfg.set("pending-restart", pending);
            if (notes != null && !notes.isEmpty()) {
                cfg.set("pending-notes", notes);
            }
            cfg.save(marker.toFile());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not set pending-restart: " + ex.getMessage());
        }
    }

    private void clearPendingRestart() {
        markPendingRestart(false, null);
        try {
            Path marker = pendingMarker();
            if (!Files.isRegularFile(marker)) {
                return;
            }
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(marker.toFile());
            cfg.set("pending-notes", null);
            cfg.save(marker.toFile());
        } catch (IOException ignored) {
            // ignore
        }
    }

    /** Same least-active window as heartbeat (HST), via public time/local. */
    private boolean fetchMaintenanceActive() {
        try {
            FileConfiguration cfg = plugin.yamlConfig() != null ? plugin.yamlConfig().config() : null;
            String apiBase = cfg != null
                    ? trimOr(
                            cfg.getString(
                                    "cloud.api-base",
                                    com.rootrecord.minecraft.common.config.RootMcApiBases.PRODUCTION),
                            com.rootrecord.minecraft.common.config.RootMcApiBases.PRODUCTION)
                    : com.rootrecord.minecraft.common.config.RootMcApiBases.PRODUCTION;
            String url = apiBase.replaceAll("/+$", "") + "/api/rootmc/time/local?tz=Pacific/Honolulu";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                plugin.getLogger().warning(
                        "Maintenance check failed HTTP " + response.statusCode() + " — allowing restart");
                return true;
            }
            String body = response.body() == null ? "" : response.body();
            return body.contains("\"maintenance_active\":true")
                    || body.contains("\"maintenance_active\": true");
        } catch (Exception ex) {
            plugin.getLogger().warning(
                    "Maintenance check failed (" + ex.getMessage() + ") — allowing restart");
            return true;
        }
    }

    private static String trimOr(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
