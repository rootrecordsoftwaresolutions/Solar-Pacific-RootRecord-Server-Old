package com.rootrecord.minecraft.rootspawn;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class RootSpawnPlugin extends JavaPlugin {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private RootRecordYamlConfig yaml;
    private SpawnConfig config;
    private PolygonAreaStore spawnStore;
    private SpawnAreaStore waypointStore;
    private PolygonAreaStore chamberStore;
    private PolygonAreaStore wellStore;
    private LavaSpotStore lavaSpotStore;
    private SpawnBoundary boundary;
    private SpawnBoundary chamberBoundary;
    private SpawnBoundary wellBoundary;
    private BasementZone basementZone;
    private ParticleRingTask particleTask;
    private SpawnProtectionListener protectionListener;
    private SpawnZoneListener zoneListener;
    private ChamberSpawnerService chamberSpawners;
    private Predicate<UUID> chamberRunState = id -> false;
    private final Map<UUID, BuildTarget> buildMode = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        RootRecordFolders.ensureDir(this);
        yaml = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_SPAWN_CONFIG, "root-spawn.yml");
        spawnStore = new PolygonAreaStore(this, RootRecordFolders.ROOT_SPAWN_REFINED_FILE, "spawn safe zone");
        waypointStore = new SpawnAreaStore(this);
        chamberStore = new PolygonAreaStore(this, RootRecordFolders.ROOT_SPAWN_CHAMBER_FILE, "basement chamber");
        wellStore = new PolygonAreaStore(this, RootRecordFolders.ROOT_SPAWN_WELL_FILE, "basement well");
        lavaSpotStore = new LavaSpotStore(this);
        chamberSpawners = new ChamberSpawnerService(this);
        reloadAll();

        var cmd = getCommand("rootspawn");
        if (cmd != null) {
            RootSpawnCommand handler = new RootSpawnCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        protectionListener = new SpawnProtectionListener(this);
        getServer().getPluginManager().registerEvents(protectionListener, this);
        getServer().getPluginManager().registerEvents(new SpawnMapListener(this), this);
        zoneListener = new SpawnZoneListener(this);
        getServer().getPluginManager().registerEvents(zoneListener, this);
        getServer().getPluginManager().registerEvents(chamberSpawners, this);
        getServer().getPluginManager().registerEvents(new PlayerSeedListener(), this);
        LocatorBarDisabler locatorBarDisabler = new LocatorBarDisabler(this);
        getServer().getPluginManager().registerEvents(locatorBarDisabler, this);
        Bukkit.getScheduler().runTask(this, locatorBarDisabler::applyToAllWorlds);

        protectionListener.startMobCleanup();
        Bukkit.getScheduler().runTaskLater(this, chamberSpawners::refreshLoadedChunks, 40L);

        getLogger().info("Root-Spawn " + getDescription().getVersion()
                + " — spawn walls and mapping tools.");
    }

    @Override
    public void onDisable() {
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
        chamberBoundary = chamberStore.loadOptional();
        wellBoundary = wellStore.loadOptional();
        try {
            if (chamberBoundary.isEmpty()) {
                chamberBoundary = chamberStore.loadOrDefault("chamber.txt");
            }
        } catch (IOException ignored) {
            // optional
        }
        try {
            if (wellBoundary.isEmpty()) {
                wellBoundary = wellStore.loadOrDefault("well.txt");
            }
        } catch (IOException ignored) {
            // optional
        }
        if (chamberSpawners != null) {
            Bukkit.getScheduler().runTask(this, chamberSpawners::refreshLoadedChunks);
        }
        refreshTerritoryMapMarkers();
    }

    private void refreshTerritoryMapMarkers() {
        var territories = Bukkit.getPluginManager().getPlugin("Root-Territories");
        if (territories == null) {
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> {
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

    public SpawnBoundary chamberBoundary() {
        return chamberBoundary;
    }

    public SpawnBoundary wellBoundary() {
        return wellBoundary;
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

    public PolygonAreaStore chamberStore() {
        return chamberStore;
    }

    public PolygonAreaStore wellStore() {
        return wellStore;
    }

    public LavaSpotStore lavaSpotStore() {
        return lavaSpotStore;
    }

    public boolean isInsideWellFootprint(Location loc) {
        return loc != null
                && wellBoundary != null
                && !wellBoundary.isEmpty()
                && wellBoundary.contains(loc.getWorld(), loc.getX(), loc.getZ());
    }

    public boolean isInsideWell(Location loc) {
        return isInsideWellFootprint(loc) && basementZone.contains(loc);
    }

    public boolean isInsideChamber(Location loc) {
        return chamberBoundary != null
                && !chamberBoundary.isEmpty()
                && basementZone.contains(loc)
                && chamberBoundary.contains(loc);
    }

    /** Chamber footprint + spawner spawn-radius padding (basement Y only). */
    public boolean isInsideChamberSpawnVolume(Location loc) {
        return loc != null
                && chamberBoundary != null
                && !chamberBoundary.isEmpty()
                && basementZone.contains(loc)
                && chamberBoundary.containsPadded(loc, 8);
    }

    public ChamberSpawnerService chamberSpawners() {
        return chamberSpawners;
    }

    public boolean isChamberMinigameRunning(Player player) {
        return player != null && isChamberMinigameRunning(player.getUniqueId());
    }

    public boolean isChamberMinigameRunning(UUID playerId) {
        return playerId != null && chamberRunState != null && chamberRunState.test(playerId);
    }

    public void setChamberRunStateProvider(Predicate<UUID> provider) {
        chamberRunState = provider == null ? (id -> false) : provider;
    }

    /** No build/break — inside walls, basement footprints, and grief buffer outside walls. */
    public boolean isGriefProtected(Location loc) {
        if (loc == null) {
            return false;
        }
        if (isInsideChamber(loc) || isInsideWellFootprint(loc)) {
            return true;
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

    /** Spawn ring footprint between basement Y levels, excluding chamber and well. */
    public boolean isBasementSurround(Location loc) {
        if (loc == null || boundary == null || !boundary.contains(loc) || !basementZone.contains(loc)) {
            return false;
        }
        return !isInsideChamber(loc) && !isInsideWell(loc);
    }

    public boolean isInsideSafeZone(Player player) {
        return player != null && isInsideSafeZone(player.getLocation());
    }

    /** Surface ring (y >= 0) or basement surround (ring y 0..-60, not chamber/well). */
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
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (isInsideChamber(victim.getLocation()) || isInsideWellFootprint(victim.getLocation())) {
                return true;
            }
        }
        if (isChamberMinigameRunning(victim) && isInsideChamber(victim.getLocation())) {
            return false;
        }
        return isInsideSafeZone(victim.getLocation());
    }

    public boolean shouldProtectPlayerFromDamage(Player attacker, Player victim) {
        if (attacker == null || victim == null) {
            return false;
        }
        if (isChamberMinigameRunning(attacker) && isInsideChamber(attacker.getLocation())) {
            return false;
        }
        if (isChamberMinigameRunning(victim) && isInsideChamber(victim.getLocation())) {
            return false;
        }
        return isInsideSafeZone(attacker.getLocation()) || isInsideSafeZone(victim.getLocation());
    }

    /** Block mob spawn / purge everywhere in ring except inside the chamber basement. */
    public boolean isMobFreeZone(Location loc) {
        if (loc == null || boundary == null || !boundary.contains(loc)) {
            return false;
        }
        if (isInsideChamber(loc)) {
            return false;
        }
        if (loc.getY() >= config.protectionYMin()) {
            return true;
        }
        if (basementZone.contains(loc)) {
            return true;
        }
        return false;
    }

    public boolean isChamberMobArea(Location loc) {
        return chamberSpawners != null && chamberSpawners.isChamberMobArea(loc);
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

    public void reloadChamberBoundary() {
        chamberBoundary = chamberStore.loadOptional();
    }

    public void reloadWellBoundary() {
        wellBoundary = wellStore.loadOptional();
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

    public void chamberMsg(Player player, String raw) {
        msg(player, config().chamberPrefix() + raw);
    }

    public void showChamberTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        player.showTitle(Title.title(
                LEGACY.deserialize(colorize(title)),
                LEGACY.deserialize(colorize(subtitle)),
                Title.Times.times(
                        Duration.ofMillis(fadeIn * 50L),
                        Duration.ofMillis(stay * 50L),
                        Duration.ofMillis(fadeOut * 50L))));
    }

    public void actionBar(Player player, String raw) {
        player.sendActionBar(LEGACY.deserialize(colorize(raw)));
    }

    private final class PlayerSeedListener implements Listener {
        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            disableBuildMode(event.getPlayer().getUniqueId());
            lavaSpotStore.clearPending(event.getPlayer().getUniqueId());
        }
    }
}
