package com.rootrecord.minecraft.rootclaims;

import com.rootrecord.minecraft.common.RootMcClaimBankService;
import com.rootrecord.minecraft.common.RootMcClaimTerritoryService;
import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.common.connection.RootMcCoreConnection;
import com.rootrecord.minecraft.rootclaims.bluemap.BlueMapClaimMarkers;
import com.rootrecord.minecraft.rootclaims.command.ClaimCommand;
import com.rootrecord.minecraft.rootclaims.command.ScanChestsCommand;
import com.rootrecord.minecraft.rootclaims.command.TownyRedirectCommand;
import com.rootrecord.minecraft.rootclaims.gui.ClaimChestListener;
import com.rootrecord.minecraft.rootclaims.gui.ClaimsDashboard;
import com.rootrecord.minecraft.rootclaims.gui.ClaimsDashboardListener;
import com.rootrecord.minecraft.rootclaims.gui.ClaimsSettingsGui;
import com.rootrecord.minecraft.rootclaims.listener.ClaimProtectionListener;
import com.rootrecord.minecraft.rootclaims.listener.ClaimRespawnListener;
import com.rootrecord.minecraft.rootclaims.listener.ClaimTerritoryAlertListener;
import com.rootrecord.minecraft.rootroad.RootRoadPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.rootrecord.minecraft.common.bstats.Metrics;
import com.rootrecord.minecraft.common.bstats.RootBStats;

public final class RootClaimsPlugin extends JavaPlugin {

    private Metrics metrics;

    public static final String CONFIG_FILE = "root-claims.yml";

    private RootRecordYamlConfig yaml;
    private ClaimStore store;
    private ClaimService claims;
    private ClaimBankService claimBanks;
    private ClaimTerritoryService claimTerritory;
    private ClaimChestStore chestStore;
    private ClaimChestService claimChests;
    private ClaimOutlineTask outlineTask;
    private BlueMapClaimMarkers blueMapMarkers;
    private ClaimsDashboard dashboard;
    private ClaimsSettingsGui settingsGui;
    private RootRoadPlugin road;
    private boolean enabledFlag = true;
    private int maxAreasPerPlayer = 3;
    private int maxLevelPerArea = 10;
    private int anchorRadiusBlocks = 16;
    private int edgeToleranceBlocks = 3;
    private int territoryBufferBlocks = 48;
    private boolean territoryAlertsEnabled = true;
    private long territoryAlertCooldownMs = 60_000L;
    private boolean allowTrustedExpansion = false;
    private boolean requirePriorAreasAtMax = true;
    private boolean economyEnabled = true;
    private double[] areaPricesGold = new double[] {0, 1000, 10000};
    private double[] expansionPricesGold = new double[0];
    private double firstClaimBankSeedGold = 25;
    private double expansionBasePriceGold = 75;
    private double expansionMultiplier = 1.5;
    private double unclaimRefundPercent = 50;
    private long confirmTtlMillis = 30_000L;
    private long spawnCooldownMillis = 3_000L;
    private String treasuryChannel = "service-fee:claim";
    private boolean protectContainers = true;
    private boolean protectInteractables = true;
    private boolean protectEntities = true;
    private boolean protectFire = true;
    private boolean protectExplosions = true;
    private boolean regenerateCreeperDamage = true;
    private long creeperRegenerationDelayTicks = 600L;
    private boolean protectPistons = true;
    private boolean protectBuckets = true;
    private boolean outlineEnabled = true;
    private long outlineRefreshTicks = 40;
    private double outlineMaxDistanceBlocks = 160;
    private int outlinePointsPerCircle = 64;
    private float outlineParticleSize = 1.15f;
    private int outlineContourStep = 2;
    private double outlineParticleSpacing = 1.25;
    private boolean bluemapEnabled = true;
    private String bluemapSetLabel = "Claims";
    private float bluemapShapeY = 64f;
    private int bluemapContourStep = 3;
    private int bluemapLineWidth = 2;
    private float bluemapFillAlpha = 0.28f;
    private boolean bluemapTerritoryEnabled = true;
    private float bluemapTerritoryFillAlpha = 0.12f;
    private int bluemapTerritoryLineWidth = 1;
    private boolean notificationsEnabled = true;
    private String prefix = "&6[Claims] &r";
    private UUID avaUuid = UUID.fromString("78c3de61-0fd6-4800-9eda-cc178eaae34b");
    private String avaName = "Ava_Ivy";
    private String avaAreaName = "Ava";
    private boolean avaSpawnAutoGrant = true;
    private boolean avaSpawnAutoPublic = true;
    private boolean transferOnClaimsCommand = false;
    private String transferOnClaimsPeer = "claims";

    @Override
    public void onEnable() {
        metrics = RootBStats.start(this);
        RootRecordFolders.ensureDir(this);
        if (getServer().getPluginManager().getPlugin("Root-Core") == null) {
            var repair = RootMcCoreConnection.ensureAndRepair(this);
            getLogger().warning(
                    "Root-Core not present — used RootMcCoreConnection fallback (databaseOk="
                            + repair.databaseOk()
                            + ", cloudOk="
                            + repair.cloudOk()
                            + ").");
        }
        yaml = new RootRecordYamlConfig(this, CONFIG_FILE, CONFIG_FILE);
        yaml.load();
        migrateAvaWildernessLore();
        store = new ClaimStore(this);
        store.load();
        claimBanks = new ClaimBankService(this, store);
        claimTerritory = new ClaimTerritoryService(this, store);
        claims = new ClaimService(this, store);
        chestStore = new ClaimChestStore(this);
        chestStore.load();
        claimChests = new ClaimChestService(this, store, claims, chestStore);
        outlineTask = new ClaimOutlineTask(this, store);
        reloadLocalConfig();

        ClaimCommand command = new ClaimCommand(this, claims);
        dashboard = new ClaimsDashboard(this, claims);
        settingsGui = new ClaimsSettingsGui(this, claims);
        var claimCommand = getCommand("claim");
        if (claimCommand != null) {
            claimCommand.setExecutor(command);
            claimCommand.setTabCompleter(command);
        }
        ScanChestsCommand scanChests = new ScanChestsCommand(this);
        var scanCommand = getCommand("scanchests");
        if (scanCommand != null) {
            scanCommand.setExecutor(scanChests);
            scanCommand.setTabCompleter(scanChests);
        }
        registerTownyRedirectsIfAbsent();
        getServer().getPluginManager().registerEvents(new ClaimProtectionListener(this, store), this);
        getServer().getPluginManager().registerEvents(new ClaimRespawnListener(this, claims), this);
        getServer().getPluginManager().registerEvents(
                new ClaimTerritoryAlertListener(this, store, claims), this);
        getServer().getPluginManager().registerEvents(
                new ClaimsDashboardListener(dashboard, settingsGui, command), this);
        getServer().getPluginManager().registerEvents(new ClaimChestListener(claimChests), this);
        blueMapMarkers = new BlueMapClaimMarkers(this, store);
        blueMapMarkers.register();
        getServer().getServicesManager().register(
                RootMcClaimBankService.class, claimBanks, this, ServicePriority.Normal);
        getServer().getServicesManager().register(
                RootMcClaimTerritoryService.class, claimTerritory, this, ServicePriority.Normal);
        outlineTask.start();
        getServer().getScheduler().runTask(this, () -> claimBanks.repairBankAccountLabels());
        getServer().getScheduler().runTaskLater(this, this::ensureAvaSpawnClaim, 40L);
        road = new RootRoadPlugin(this);
        road.enable();
        getLogger().info("Root-Claims enabled - " + store.all().size() + " claim(s) loaded.");
    }

    /** Register towny-style redirect commands only when Towny is not installed (Claims host). */
    private void registerTownyRedirectsIfAbsent() {
        Plugin towny = getServer().getPluginManager().getPlugin("Towny");
        if (towny != null) {
            getLogger().info("Towny present — skipping Claims towny-style redirects.");
            return;
        }
        TownyRedirectCommand townyRedirect = new TownyRedirectCommand(this);
        for (String name : List.of("t", "town", "towns", "towny")) {
            try {
                PluginCommand cmd = createDynamicCommand(name);
                if (cmd == null) {
                    continue;
                }
                cmd.setDescription("Towny is not used — redirects to RootClaims help");
                cmd.setUsage("/" + name);
                cmd.setExecutor(townyRedirect);
                cmd.setTabCompleter(townyRedirect);
                getServer().getCommandMap().register("root-claims", cmd);
            } catch (Exception ex) {
                getLogger().warning("Could not register /" + name + " redirect: " + ex.getMessage());
            }
        }
    }

    private PluginCommand createDynamicCommand(String name) {
        try {
            Constructor<PluginCommand> ctor =
                    PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            ctor.setAccessible(true);
            return ctor.newInstance(name, this);
        } catch (ReflectiveOperationException ex) {
            getLogger().warning("PluginCommand ctor failed for /" + name + ": " + ex.getMessage());
            return null;
        }
    }

    @Override
    public void onDisable() {
        RootBStats.shutdown(metrics);
        if (road != null) {
            road.disable();
            road = null;
        }
        if (claimChests != null) {
            claimChests.saveAllOpen();
        }
        if (blueMapMarkers != null) {
            blueMapMarkers.unregister();
        }
        if (outlineTask != null) {
            outlineTask.stop();
        }
        getServer().getServicesManager().unregister(RootMcClaimBankService.class, claimBanks);
        getServer().getServicesManager().unregister(RootMcClaimTerritoryService.class, claimTerritory);
    }

    /** Ava is the wilderness guardian watch (enter + /c info). */
    private void migrateAvaWildernessLore() {
        var cfg = yaml.config();
        if (cfg.getBoolean("ava-wilderness-122", false)
                && String.valueOf(cfg.getString("messages.wild-enter", "")).contains("Ava")) {
            return;
        }
        cfg.set("messages.wild-enter", "&dEntering Ava's wilderness &8· &eGuardian watch &8- &f1 G/block &7→ reserve");
        cfg.set(
                "messages.info-wild",
                "&bAva's wilderness&7. She is the guardian watch: &f1 G&7/block → Server Reserve (minute recap if you pay). &f/c claim &7or get trusted. Mining rights: Y < 0 is free (no claims).");
        java.util.List<String> help = cfg.getStringList("messages.help");
        if (!help.isEmpty()) {
            java.util.List<String> next = new java.util.ArrayList<>();
            for (String line : help) {
                if (line != null && line.contains("Surface wilderness: 1G/block to the server")) {
                    next.add("&7Mining rights: Y < 0 is free reign (no claims). Ava's wilderness: 1G/block → reserve (minute recap).");
                } else {
                    next.add(line);
                }
            }
            cfg.set("messages.help", next);
        }
        cfg.set("ava-wilderness-122", true);
        yaml.save();
        getLogger().info("Wilderness lore: Ava guardian watch.");
    }

    public void reloadAll() {
        yaml.reload();
        migrateAvaWildernessLore();
        reloadLocalConfig();
        if (road != null) {
            road.reloadAll();
        }
        store.load();
        if (claimChests != null) {
            claimChests.saveAllOpen();
        }
        if (chestStore != null) {
            chestStore.load();
        }
        if (outlineTask != null) {
            outlineTask.start();
        }
        if (blueMapMarkers != null) {
            blueMapMarkers.scheduleSync();
        }
    }

    private void reloadLocalConfig() {
        FileConfiguration cfg = yaml.config();
        enabledFlag = cfg.getBoolean("enabled", true);
        maxAreasPerPlayer = Math.max(1, cfg.getInt(
                "limits.max-areas-per-player",
                cfg.getInt("limits.max-claims-per-player", 3)));
        maxLevelPerArea = Math.max(1, cfg.getInt("limits.max-level-per-area", 10));
        requirePriorAreasAtMax = cfg.getBoolean("limits.require-prior-areas-at-max", true);
        anchorRadiusBlocks = Math.max(1, cfg.getInt("anchors.radius-blocks", 16));
        edgeToleranceBlocks = Math.max(0, cfg.getInt("anchors.edge-tolerance-blocks", 3));
        territoryBufferBlocks = Math.max(0, cfg.getInt("territory.buffer-blocks", 48));
        territoryAlertsEnabled = cfg.getBoolean("territory.satellite-alerts.enabled", true);
        territoryAlertCooldownMs = Math.max(5_000L, cfg.getLong("territory.satellite-alerts.cooldown-seconds", 60)) * 1000L;
        allowTrustedExpansion = cfg.getBoolean("anchors.allow-trusted-expansion", false);
        economyEnabled = cfg.getBoolean("economy.enabled", true);
        areaPricesGold = loadAreaPrices(cfg);
        expansionPricesGold = loadDoubleList(cfg, "economy.expansion-prices-g");
        firstClaimBankSeedGold = Math.max(0, Math.min(
                areaPriceForSlot(0),
                cfg.getDouble(
                        "economy.first-area-bank-seed-g",
                        cfg.getDouble("economy.first-claim-bank-seed-g", 25))));
        expansionBasePriceGold = Math.max(0, cfg.getDouble(
                "economy.expansion-base-price-g",
                cfg.getDouble("economy.base-claim-price-g", 75)));
        expansionMultiplier = Math.max(1.0, cfg.getDouble("economy.expansion-multiplier", 1.5));
        unclaimRefundPercent = Math.max(0, Math.min(100, cfg.getDouble("economy.unclaim-refund-percent", 50)));
        treasuryChannel = cfg.getString("economy.treasury-channel", "service-fee:claim");
        confirmTtlMillis = Math.max(5_000L, cfg.getLong("timings.confirm-ttl-seconds", 30L) * 1000L);
        spawnCooldownMillis = Math.max(0L, cfg.getLong("timings.spawn-cooldown-seconds", 3L) * 1000L);
        protectContainers = cfg.getBoolean("protection.protect-containers", true);
        protectInteractables = cfg.getBoolean("protection.protect-doors-buttons-and-redstone", true);
        protectEntities = cfg.getBoolean("protection.protect-entities", true);
        protectFire = cfg.getBoolean("protection.protect-fire", true);
        protectExplosions = cfg.getBoolean("protection.protect-explosions", true);
        regenerateCreeperDamage = cfg.getBoolean("protection.creeper-regeneration.enabled", true);
        creeperRegenerationDelayTicks = Math.max(
                20L,
                cfg.getLong("protection.creeper-regeneration.delay-seconds", 30L) * 20L);
        protectPistons = cfg.getBoolean("protection.protect-pistons", true);
        protectBuckets = cfg.getBoolean("protection.protect-buckets", true);
        outlineEnabled = cfg.getBoolean("outline.enabled", true);
        outlineRefreshTicks = Math.max(10L, cfg.getLong("outline.refresh-ticks", 40L));
        outlineMaxDistanceBlocks = Math.max(16, cfg.getDouble("outline.max-distance-blocks", 160));
        outlinePointsPerCircle = Math.max(12, cfg.getInt("outline.points-per-circle", 64));
        outlineParticleSize = (float) Math.max(0.25, cfg.getDouble("outline.particle-size", 1.15));
        outlineContourStep = Math.max(2, cfg.getInt("outline.contour-step", 2));
        outlineParticleSpacing = Math.max(0.75, cfg.getDouble("outline.particle-spacing", 1.25));
        bluemapEnabled = cfg.getBoolean("bluemap.enabled", true);
        bluemapSetLabel = cfg.getString("bluemap.set-label", "Claims");
        bluemapShapeY = (float) cfg.getDouble("bluemap.shape-y", 64);
        bluemapContourStep = Math.max(2, cfg.getInt("bluemap.contour-step", 3));
        bluemapLineWidth = Math.max(1, cfg.getInt("bluemap.line-width", 2));
        bluemapFillAlpha = (float) Math.max(0.05, Math.min(0.9, cfg.getDouble("bluemap.fill-alpha", 0.28)));
        bluemapTerritoryEnabled = cfg.getBoolean("bluemap.territory-enabled", true);
        bluemapTerritoryFillAlpha = (float) Math.max(0.02, Math.min(0.9,
                cfg.getDouble("bluemap.territory-fill-alpha", 0.12)));
        bluemapTerritoryLineWidth = Math.max(1, cfg.getInt("bluemap.territory-line-width", 1));
        notificationsEnabled = cfg.getBoolean("notifications.enabled", true);
        prefix = cfg.getString("messages.prefix", "");
        String avaRaw = cfg.getString("spawn.ava-uuid", "78c3de61-0fd6-4800-9eda-cc178eaae34b");
        try {
            avaUuid = UUID.fromString(avaRaw == null ? "" : avaRaw.trim());
        } catch (IllegalArgumentException ex) {
            avaUuid = UUID.fromString("78c3de61-0fd6-4800-9eda-cc178eaae34b");
            getLogger().warning("Invalid spawn.ava-uuid — using Ava_Ivy default.");
        }
        avaName = cfg.getString("spawn.ava-name", "Ava_Ivy");
        if (avaName == null || avaName.isBlank()) {
            avaName = "Ava_Ivy";
        }
        avaAreaName = cfg.getString("spawn.area-name", "Ava");
        if (avaAreaName == null || avaAreaName.isBlank()) {
            avaAreaName = "Ava";
        }
        avaSpawnAutoGrant = cfg.getBoolean("spawn.auto-grant", true);
        avaSpawnAutoPublic = cfg.getBoolean("spawn.auto-public", true);
        transferOnClaimsCommand = cfg.getBoolean("mesh.transfer-on-claims-command", false);
        String peer = cfg.getString("mesh.transfer-to", "claims");
        transferOnClaimsPeer = peer == null || peer.isBlank() ? "claims" : peer.trim();
    }

    public boolean isAvaOwner(UUID id) {
        return id != null && avaUuid != null && avaUuid.equals(id);
    }

    public boolean isAvaName(String name) {
        return name != null && !name.isBlank() && avaName != null && avaName.equalsIgnoreCase(name.trim());
    }

    public UUID avaUuid() {
        return avaUuid;
    }

    public String avaName() {
        return avaName;
    }

    public String avaAreaName() {
        return avaAreaName;
    }

    public boolean avaSpawnAutoPublic() {
        return avaSpawnAutoPublic;
    }

    /**
     * Fresh-world handoff: if Ava has no area yet, found a public spawn claim at world spawn
     * so {@code /c spawn Ava_Ivy} works immediately.
     */
    public void ensureAvaSpawnClaim() {
        if (!avaSpawnAutoGrant || avaUuid == null || claims == null || store == null) {
            return;
        }
        if (!store.rootsOwnedBy(avaUuid).isEmpty()) {
            return;
        }
        if (Bukkit.getWorlds().isEmpty()) {
            getLogger().warning("Ava spawn auto-grant skipped — no worlds loaded.");
            return;
        }
        org.bukkit.World world = Bukkit.getWorlds().get(0);
        Location at = world.getSpawnLocation();
        ClaimService.Result result = claims.grantArea(
                avaUuid,
                avaName,
                at,
                avaAreaName,
                avaSpawnAutoPublic,
                true);
        if (result.status() == ClaimService.Status.OK && result.claim() != null) {
            ClaimKey key = result.claim().key();
            getLogger().info(
                    "Granted Ava public spawn claim '"
                            + avaAreaName
                            + "' at "
                            + key.world()
                            + " "
                            + key.x()
                            + ","
                            + key.y()
                            + ","
                            + key.z()
                            + " — /c spawn Ava_Ivy");
            return;
        }
        getLogger().warning("Ava spawn auto-grant failed: " + result.status());
    }

    public boolean transferOnClaimsCommand() {
        return transferOnClaimsCommand;
    }

    public String transferOnClaimsPeer() {
        return transferOnClaimsPeer;
    }

    public boolean enabledFlag() {
        return enabledFlag;
    }

    public int maxClaimsPerPlayer() {
        return maxAreasPerPlayer * maxLevelPerArea;
    }

    public int maxAreasPerPlayer() {
        return maxAreasPerPlayer;
    }

    public int maxLevelPerArea() {
        return maxLevelPerArea;
    }

    public int anchorRadiusBlocks() {
        return anchorRadiusBlocks;
    }

    public int edgeToleranceBlocks() {
        return edgeToleranceBlocks;
    }

    public int territoryBufferBlocks() {
        return territoryBufferBlocks;
    }

    public boolean territoryAlertsEnabled() {
        return territoryAlertsEnabled;
    }

    public long territoryAlertCooldownMs() {
        return territoryAlertCooldownMs;
    }

    public ClaimTerritoryService claimTerritory() {
        return claimTerritory;
    }

    public boolean canManageClaimAt(org.bukkit.entity.Player player, org.bukkit.Location location) {
        return player != null && claims != null && claims.canManageAt(player.getUniqueId(), location);
    }

    public boolean allowTrustedExpansion() {
        return allowTrustedExpansion;
    }

    public boolean requirePriorAreasAtMax() {
        return requirePriorAreasAtMax;
    }

    public long confirmTtlMillis() {
        return confirmTtlMillis;
    }

    public long spawnCooldownMillis() {
        return spawnCooldownMillis;
    }

    public boolean economyEnabled() {
        return economyEnabled;
    }

    public double baseClaimPriceGold() {
        return expansionBasePriceGold;
    }

    public double firstClaimPriceGold() {
        return areaPriceForSlot(0);
    }

    public double firstClaimBankSeedGold() {
        return firstClaimBankSeedGold;
    }

    public double firstClaimReserveFlowGold() {
        return Math.max(0, areaPriceForSlot(0) - firstClaimBankSeedGold);
    }

    public double expansionBasePriceGold() {
        return expansionBasePriceGold;
    }

    public double expansionMultiplier() {
        return expansionMultiplier;
    }

    public double areaPriceForSlot(int slotIndex) {
        if (!economyEnabled) {
            return 0;
        }
        if (areaPricesGold == null || areaPricesGold.length == 0) {
            return 0;
        }
        int idx = Math.max(0, Math.min(slotIndex, areaPricesGold.length - 1));
        return Math.round(Math.max(0, areaPricesGold[idx]) * 1000.0) / 1000.0;
    }

    /** Price to expand from {@code currentLevel} to currentLevel+1 (level is 1-based circle count). */
    public double expansionPriceForLevel(int currentLevel) {
        if (!economyEnabled) {
            return 0;
        }
        int level = Math.max(1, currentLevel);
        int idx = level - 1;
        if (expansionPricesGold != null && expansionPricesGold.length > 0) {
            int use = Math.min(idx, expansionPricesGold.length - 1);
            return Math.round(Math.max(0, expansionPricesGold[use]) * 1000.0) / 1000.0;
        }
        double price = expansionBasePriceGold * Math.pow(expansionMultiplier, level - 1);
        return Math.round(price * 1000.0) / 1000.0;
    }

    /** @deprecated use {@link #areaPriceForSlot(int)} / {@link #expansionPriceForLevel(int)} */
    public double claimPriceForExistingCount(int existingClaims) {
        return existingClaims <= 0 ? areaPriceForSlot(0) : expansionPriceForLevel(existingClaims);
    }

    private static double[] loadAreaPrices(FileConfiguration cfg) {
        double[] listed = loadDoubleList(cfg, "economy.area-prices-g");
        if (listed.length > 0) {
            return listed;
        }
        return new double[] {
                Math.max(0, cfg.getDouble("economy.first-claim-price-g", 75)),
                Math.max(0, cfg.getDouble("economy.second-area-price-g", 1000)),
                Math.max(0, cfg.getDouble("economy.third-area-price-g", 10000))
        };
    }

    private static double[] loadDoubleList(FileConfiguration cfg, String path) {
        List<?> raw = cfg.getList(path);
        if (raw == null || raw.isEmpty()) {
            return new double[0];
        }
        double[] prices = new double[raw.size()];
        for (int i = 0; i < raw.size(); i++) {
            Object value = raw.get(i);
            prices[i] = value instanceof Number number ? Math.max(0, number.doubleValue()) : 0;
        }
        return prices;
    }

    public double unclaimRefundPercent() {
        return unclaimRefundPercent;
    }

    public String treasuryChannel() {
        return treasuryChannel;
    }

    public boolean protectContainers() {
        return protectContainers;
    }

    public boolean protectInteractables() {
        return protectInteractables;
    }

    public boolean protectEntities() {
        return protectEntities;
    }

    public boolean protectFire() {
        return protectFire;
    }

    public boolean protectExplosions() {
        return protectExplosions;
    }

    public boolean regenerateCreeperDamage() {
        return regenerateCreeperDamage;
    }

    public long creeperRegenerationDelayTicks() {
        return creeperRegenerationDelayTicks;
    }

    public boolean protectPistons() {
        return protectPistons;
    }

    public boolean protectBuckets() {
        return protectBuckets;
    }

    public boolean outlineEnabled() {
        return outlineEnabled;
    }

    public long outlineRefreshTicks() {
        return outlineRefreshTicks;
    }

    public double outlineMaxDistanceBlocks() {
        return outlineMaxDistanceBlocks;
    }

    public int outlinePointsPerCircle() {
        return outlinePointsPerCircle;
    }

    public float outlineParticleSize() {
        return outlineParticleSize;
    }

    public int outlineContourStep() {
        return outlineContourStep;
    }

    public double outlineParticleSpacing() {
        return outlineParticleSpacing;
    }

    public boolean bluemapEnabled() {
        return bluemapEnabled;
    }

    public String bluemapSetLabel() {
        return bluemapSetLabel;
    }

    public float bluemapShapeY() {
        return bluemapShapeY;
    }

    public int bluemapContourStep() {
        return bluemapContourStep;
    }

    public int bluemapLineWidth() {
        return bluemapLineWidth;
    }

    public float bluemapFillAlpha() {
        return bluemapFillAlpha;
    }

    public boolean bluemapTerritoryEnabled() {
        return bluemapTerritoryEnabled;
    }

    public float bluemapTerritoryFillAlpha() {
        return bluemapTerritoryFillAlpha;
    }

    public int bluemapTerritoryLineWidth() {
        return bluemapTerritoryLineWidth;
    }

    public void syncBlueMap() {
        if (blueMapMarkers != null) {
            blueMapMarkers.scheduleSync();
        }
    }

    /** Notify Root-Road + Root-Play (or legacy Root-Ranks) when owned claim count changes. */
    public void notifyOwnedClaimCountChanged(Player player) {
        if (player == null || store == null) {
            return;
        }
        if (road != null && road.guide() != null) {
            road.guide().checkClaimProgress(player);
        }
        int owned = store.countOwnedBy(player.getUniqueId());
        for (String pluginName : List.of("Root-Play", "Root-Ranks")) {
            var ranks = getServer().getPluginManager().getPlugin(pluginName);
            if (ranks == null || !ranks.isEnabled()) {
                continue;
            }
            try {
                ranks.getClass()
                        .getMethod("onOwnedClaimCountChanged", Player.class, int.class)
                        .invoke(ranks, player, owned);
                return;
            } catch (ReflectiveOperationException ex) {
                getLogger().warning("Claim-rank notify via " + pluginName + " failed: " + ex.getMessage());
            }
        }
    }

    public RootRoadPlugin road() {
        return road;
    }

    public boolean notificationsEnabled() {
        return notificationsEnabled;
    }

    public ClaimService claims() {
        return claims;
    }

    /**
     * Tab suggestions for {@code /spawn [player|area] [area]}.
     */
    public List<String> tabCompleteAreaSpawn(Player visitor, String[] args) {
        if (visitor == null || claims == null || store == null || args == null || args.length == 0) {
            return List.of();
        }
        String needle = args[args.length - 1] == null ? "" : args[args.length - 1].toLowerCase(java.util.Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(java.util.Locale.ROOT).startsWith(needle)) {
                    out.add(online.getName());
                }
            }
            for (ClaimRecord root : store.all()) {
                if (!root.isAreaRoot()) {
                    continue;
                }
                String name = root.areaNameOrOwner();
                if (name != null && name.toLowerCase(java.util.Locale.ROOT).startsWith(needle) && !out.contains(name)) {
                    out.add(name);
                }
            }
            out.sort(String.CASE_INSENSITIVE_ORDER);
            return out;
        }
        if (args.length == 2) {
            java.util.UUID ownerId = resolveOwnerUuidForSpawn(args[0]);
            if (ownerId == null) {
                return List.of();
            }
            for (ClaimRecord root : store.rootsOwnedBy(ownerId)) {
                String name = root.areaNameOrOwner();
                if (name != null && name.toLowerCase(java.util.Locale.ROOT).startsWith(needle)) {
                    out.add(name);
                }
            }
            out.sort(String.CASE_INSENSITIVE_ORDER);
            return out;
        }
        return List.of();
    }

    private java.util.UUID resolveOwnerUuidForSpawn(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(raw);
        if (online != null) {
            return online.getUniqueId();
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(raw)) {
                return p.getUniqueId();
            }
        }
        org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(raw);
        if (offline.hasPlayedBefore() || offline.isOnline()) {
            return offline.getUniqueId();
        }
        ClaimRecord named = claims.findOwnerByName(raw);
        return named == null ? null : named.ownerId();
    }

    /**
     * Used by Root-Essentials {@code /spawn <player|area> [area]}.
     * @return true when the request was handled as an area visit (success or player-facing failure).
     */
    public boolean tryTeleportToAreaSpawn(Player visitor, String targetRaw, String areaName) {
        if (visitor == null || claims == null || targetRaw == null || targetRaw.isBlank()) {
            return false;
        }
        // Prefer exact area display-name match when present.
        ClaimRecord areaByName = claims.resolveSpawnByAreaName(targetRaw, visitor.getUniqueId());
        if (areaByName != null && (areaName == null || areaName.isBlank())) {
            Location loc = claims.spawnLocation(areaByName);
            if (loc == null) {
                visitor.sendMessage(msg("claim-spawn-missing-world"));
                return true;
            }
            visitor.teleport(loc);
            visitor.sendMessage(msg("claim-spawn-success-other").replace("{owner}", areaByName.areaNameOrOwner()));
            return true;
        }
        // Area exists by name but visitor cannot use it.
        if (store != null) {
            ClaimRecord locked = store.findAreaByName(targetRaw);
            if (locked != null && (areaName == null || areaName.isBlank())) {
                visitor.sendMessage(msg("claim-spawn-private").replace("{owner}", locked.areaNameOrOwner()));
                return true;
            }
        }
        org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(targetRaw);
        java.util.UUID ownerId = null;
        if (offline.hasPlayedBefore() || offline.isOnline()) {
            ownerId = offline.getUniqueId();
        } else {
            Player online = Bukkit.getPlayerExact(targetRaw);
            if (online != null) {
                ownerId = online.getUniqueId();
            } else {
                ClaimRecord namedOwner = claims.findOwnerByName(targetRaw);
                if (namedOwner != null) {
                    ownerId = namedOwner.ownerId();
                }
            }
        }
        if (ownerId == null) {
            visitor.sendMessage(msg("claim-spawn-player-unknown").replace("{player}", targetRaw));
            return true;
        }
        String ownerLabel = offline.getName() != null ? offline.getName() : targetRaw;
        ClaimRecord target;
        if (ownerId.equals(visitor.getUniqueId())) {
            target = claims.ownedSpawnClaim(visitor);
            if (areaName != null && !areaName.isBlank()) {
                target = claims.resolveSpawnClaim(ownerId, visitor.getUniqueId(), areaName);
            }
        } else {
            target = claims.resolveSpawnClaim(ownerId, visitor.getUniqueId(), areaName);
        }
        if (target == null) {
            if (claims.rootsOwnedBy(ownerId).isEmpty()) {
                visitor.sendMessage(msg("claim-spawn-player-none").replace("{player}", ownerLabel));
            } else if (areaName != null && !areaName.isBlank()) {
                ClaimRecord named = store.findAreaByName(ownerId, areaName);
                if (named == null) {
                    visitor.sendMessage(msg("claim-spawn-area-unknown")
                            .replace("{area}", areaName)
                            .replace("{player}", ownerLabel));
                } else {
                    visitor.sendMessage(msg("claim-spawn-private").replace("{owner}", named.areaNameOrOwner()));
                }
            } else {
                visitor.sendMessage(msg("claim-spawn-private").replace("{owner}", ownerLabel));
            }
            return true;
        }
        Location loc = claims.spawnLocation(target);
        if (loc == null) {
            visitor.sendMessage(msg("claim-spawn-missing-world"));
            return true;
        }
        visitor.teleport(loc);
        if (ownerId.equals(visitor.getUniqueId())) {
            visitor.sendMessage(msg("claim-spawn-success"));
        } else {
            visitor.sendMessage(msg("claim-spawn-success-other").replace("{owner}", target.areaNameOrOwner()));
        }
        return true;
    }

    public ClaimBankService claimBanks() {
        return claimBanks;
    }

    public ClaimChestService claimChests() {
        return claimChests;
    }

    public ClaimsDashboard dashboard() {
        return dashboard;
    }

    public ClaimsSettingsGui settingsGui() {
        return settingsGui;
    }

    public int ownedClaimCount(java.util.UUID ownerId) {
        return store == null || ownerId == null ? 0 : store.countOwnedBy(ownerId);
    }

    public boolean toggleOutline(Player player) {
        if (outlineTask == null || !outlineEnabled) {
            return false;
        }
        return outlineTask.toggle(player);
    }

    public String msg(String key) {
        return colorize(prefix + rawMsg(key));
    }

    public String rawMsg(String key) {
        FileConfiguration cfg = yaml.config();
        return cfg.getString("messages." + key, key);
    }

    public List<String> rawList(String key) {
        return yaml.config().getStringList("messages." + key);
    }

    public String colorize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }
}
