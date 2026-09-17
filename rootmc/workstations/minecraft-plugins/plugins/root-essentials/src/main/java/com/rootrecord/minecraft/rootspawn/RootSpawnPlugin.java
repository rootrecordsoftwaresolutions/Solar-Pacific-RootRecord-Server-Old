package com.rootrecord.minecraft.rootspawn;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RootSpawnPlugin {
    private final org.bukkit.plugin.java.JavaPlugin host;

    public RootSpawnPlugin(org.bukkit.plugin.java.JavaPlugin host) {
        this.host = host;
    }

    public org.bukkit.plugin.java.JavaPlugin host() { return host; }
    public org.bukkit.plugin.Plugin getPlugin() { return host; }
    public java.util.logging.Logger getLogger() { return host.getLogger(); }
    public org.bukkit.Server getServer() { return host.getServer(); }
    public java.io.File getDataFolder() { return host.getDataFolder(); }
    public org.bukkit.command.PluginCommand getCommand(String name) { return host.getCommand(name); }
    public org.bukkit.plugin.PluginDescriptionFile getDescription() { return host.getDescription(); }
    public java.io.InputStream getResource(String path) { return host.getResource(path); }
    public void saveResource(String path, boolean replace) { host.saveResource(path, replace); }
    public org.bukkit.scheduler.BukkitScheduler getScheduler() { return host.getServer().getScheduler(); }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private RootRecordYamlConfig yaml;
    private SpawnConfig config;
    private PolygonAreaStore spawnStore;
    private SpawnAreaStore waypointStore;
    private SpawnBoundary boundary;
    private BasementZone basementZone;
    private ParticleRingTask particleTask;
    private SpawnProtectionListener protectionListener;
    private SpawnZoneListener zoneListener;
    private final Map<UUID, BuildTarget> buildMode = new ConcurrentHashMap<>();

    public void enable() {
        RootRecordFolders.ensureDir(host);
        yaml = new RootRecordYamlConfig(host, RootRecordFolders.ROOT_SPAWN_CONFIG, "root-spawn.yml");
        spawnStore = new PolygonAreaStore(host, RootRecordFolders.ROOT_SPAWN_REFINED_FILE, "spawn safe zone");
        waypointStore = new SpawnAreaStore(host);
        reloadAll();

        var cmd = getCommand("rootspawn");
        if (cmd != null) {
            RootSpawnCommand handler = new RootSpawnCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        protectionListener = new SpawnProtectionListener(this);
        getServer().getPluginManager().registerEvents(protectionListener, host);
        getServer().getPluginManager().registerEvents(new SpawnMapListener(this), host);
        zoneListener = new SpawnZoneListener(this);
        getServer().getPluginManager().registerEvents(zoneListener, host);
        getServer().getPluginManager().registerEvents(new PlayerSeedListener(), host);
        LocatorBarDisabler locatorBarDisabler = new LocatorBarDisabler(this);
        getServer().getPluginManager().registerEvents(locatorBarDisabler, host);
        Bukkit.getScheduler().runTask(host, locatorBarDisabler::applyToAllWorlds);

        protectionListener.startMobCleanup();

        SpawnBoundary loaded = boundary;
        if (loaded == null || loaded.isEmpty()) {
            getLogger().warning("Root-Spawn — no spawn boundary loaded (spawnarea-refined.txt empty?).");
        } else {
            int[] b = loaded.horizontalBounds();
            getLogger().info("Root-Spawn " + getDescription().getVersion()
                    + " — safe zone world=" + loaded.worldName()
                    + " verts=" + loaded.vertices().size()
                    + (b == null ? "" : (" X " + b[0] + ".." + b[1] + " Z " + b[2] + ".." + b[3])));
        }
    }

    public void disable() {
        if (particleTask != null) {
            particleTask.stop();
        }
        if (protectionListener != null) {
            protectionListener.stopMobCleanup();
        }
    }

    public void reloadAll() {
        yaml.reload();
        config = SpawnConfig.from(yaml.config());
        basementZone = new BasementZone(config.basementYTop(), config.basementYBottom());
        try {
            boundary = spawnStore.loadOrDefault("spawnarea-refined.txt");
        } catch (IOException ex) {
            boundary = new SpawnBoundary("world", List.of());
            getLogger().warning("Could not load spawn boundary: " + ex.getMessage());
        }
        refreshTerritoryMapMarkers();
    }

    private void refreshTerritoryMapMarkers() {
        var territories = Bukkit.getPluginManager().getPlugin("Root-Territories");
        if (territories == null) {
            return;
        }
        Bukkit.getScheduler().runTask(host, () -> {
            try {
                territories.getClass().getMethod("syncMapMarkers").invoke(territories);
            } catch (ReflectiveOperationException ex) {
                getLogger().fine("Could not refresh territory map markers: " + ex.getMessage());
            }
        });
    }

    public SpawnConfig config() {
        return config;
    }

    public SpawnBoundary boundary() {
        return boundary;
    }

    public BasementZone basementZone() {
        return basementZone;
    }

    public PolygonAreaStore spawnStore() {
        return spawnStore;
    }

    public SpawnAreaStore waypointStore() {
        return waypointStore;
    }

    /** No build/break — inside walls and grief buffer outside walls. */
    public boolean isGriefProtected(Location loc) {
        if (loc == null) {
            return false;
        }
        if (boundary == null || boundary.isEmpty()) {
            return false;
        }
        int buffer = config.griefBufferBlocks();
        if (boundary.contains(loc)) {
            return isInsideSafeZone(loc);
        }
        return buffer > 0 && boundary.containsPadded(loc, buffer);
    }

    /** Spawn ring footprint between basement Y levels. */
    public boolean isBasementSurround(Location loc) {
        return loc != null
                && boundary != null
                && boundary.contains(loc)
                && basementZone.contains(loc);
    }

    public boolean isInsideSafeZone(Player player) {
        return player != null && isInsideSafeZone(player.getLocation());
    }

    /** Surface ring (y >= 0) or basement surround (ring y 0..-60). */
    public boolean isInsideSafeZone(Location loc) {
        if (loc == null || boundary == null || !boundary.contains(loc)) {
            return false;
        }
        if (loc.getY() >= config.protectionYMin()) {
            return true;
        }
        return isBasementSurround(loc);
    }

    public boolean isProtectedBlock(Location loc) {
        return isGriefProtected(loc);
    }

    public boolean shouldProtectPlayerFromDamage(Player victim, EntityDamageEvent event) {
        if (victim == null || event == null) {
            return false;
        }
        return isInsideSafeZone(victim.getLocation());
    }

    public boolean shouldProtectPlayerFromDamage(Player attacker, Player victim) {
        if (attacker == null || victim == null) {
            return false;
        }
        return isInsideSafeZone(attacker.getLocation()) || isInsideSafeZone(victim.getLocation());
    }

    /** Block mob spawn / purge everywhere in the spawn ring (surface + basement). */
    public boolean isMobFreeZone(Location loc) {
        if (loc == null || boundary == null || !boundary.contains(loc)) {
            return false;
        }
        if (loc.getY() >= config.protectionYMin()) {
            return true;
        }
        return basementZone.contains(loc);
    }

    public BuildTarget buildTarget(UUID playerId) {
        return buildMode.get(playerId);
    }

    public boolean isBuildMode(UUID playerId) {
        return buildMode.containsKey(playerId);
    }

    public void enableBuildMode(UUID playerId, BuildTarget target) {
        buildMode.put(playerId, target);
    }

    public void disableBuildMode(UUID playerId) {
        buildMode.remove(playerId);
    }

    public void saveSpawnBoundary(String savedBy) throws IOException {
        spawnStore.saveBoundary(boundary, savedBy);
        if (particleTask != null) {
            particleTask.invalidateCache();
        }
    }

    public void applySpawnBoundary(SpawnBoundary next, String savedBy) throws IOException {
        boundary = next.copy();
        saveSpawnBoundary(savedBy);
    }

    public String colorize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public void msg(Player player, String raw) {
        player.sendMessage(LEGACY.deserialize(colorize(raw)));
    }

    public void actionBar(Player player, String raw) {
        player.sendActionBar(LEGACY.deserialize(colorize(raw)));
    }

    private final class PlayerSeedListener implements Listener {
        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            disableBuildMode(event.getPlayer().getUniqueId());
        }
    }
}
