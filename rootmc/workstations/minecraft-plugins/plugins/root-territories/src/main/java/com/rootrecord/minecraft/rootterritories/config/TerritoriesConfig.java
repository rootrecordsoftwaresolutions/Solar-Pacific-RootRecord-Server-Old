package com.rootrecord.minecraft.rootterritories.config;

import org.bukkit.configuration.file.FileConfiguration;

public final class TerritoriesConfig {

    private final boolean enabled;
    private final int refreshMinutes;
    private final double nationCapitalBuffer;
    private final double townBuffer;
    private final double mapOutlineInset;
    private final boolean bluemapEnabled;
    private final String bluemapNationSetId;
    private final String bluemapNationsLabel;
    private final float bluemapShapeY;
    private final int bluemapCircleSegments;
    private final int bluemapContourStep;
    private final int bluemapLineWidth;
    private final boolean bluemapDepthTest;
    private final float[] nationLine;
    private final float[] nationFill;
    private final boolean enforceTownRules;
    private final String bluemapTownSetId;
    private final String bluemapTownsLabel;
    private final float[] townLine;
    private final float[] townFill;
    private final float bluemapSpawnShapeY;
    private final float[] spawnGriefLine;
    private final float[] spawnGriefFill;
    private final float[] spawnWallLine;
    private final String enterNation;
    private final String exitNation;
    private final String enterTown;
    private final String exitTown;
    private final String denyCreateTown;
    private final String denyJoinNation;
    private final boolean particlesEnabled;
    private final int particleIntervalTicks;
    private final int particleBatchSize;
    private final int particleCount;
    private final float particleSize;
    private final double particleSpread;
    private final int particleLayers;
    private final int particleSurfaceYOffset;
    private final int particleContourStep;
    private final int particleEdgeSpacing;
    private final double particleViewRadius;
    private final int[] nationParticleRgb;
    private final int[] townParticleRgb;
    private final boolean wildernessAlertsEnabled;
    private final int wildernessAlertCooldownMinutes;
    private final int wildernessMinYAboveSea;
    private final String wildernessAlertTown;
    private final String wildernessAlertNation;
    private final String muteSuffix;
    private final String notificationsEnabled;
    private final String notificationsDisabled;
    private final String notificationsStatusOn;
    private final String notificationsStatusOff;
    private final int messageCooldownSeconds;

    public TerritoriesConfig(FileConfiguration cfg) {
        this.enabled = cfg.getBoolean("enabled", true);
        this.refreshMinutes = Math.max(1, cfg.getInt("refresh-minutes", 5));
        this.nationCapitalBuffer = Math.max(0, cfg.getDouble("nation-capital-buffer", 500));
        if (cfg.contains("nation-extension-buffer")) {
            this.townBuffer = Math.max(0, cfg.getDouble("nation-extension-buffer", 250));
        } else {
            this.townBuffer = Math.max(0, cfg.getDouble("town-buffer", 250));
        }
        this.mapOutlineInset = Math.max(0, cfg.getDouble("map-outline-inset", 8));
        this.bluemapEnabled = cfg.getBoolean("bluemap.enabled", true);
        this.bluemapNationSetId = cfg.getString("bluemap.marker-set-nations", "root-territories-nations");
        this.bluemapNationsLabel = cfg.getString("bluemap.nations-label", "Nation territories");
        this.bluemapShapeY = (float) cfg.getDouble("bluemap.shape-y", 64);
        this.bluemapCircleSegments = Math.max(12, cfg.getInt("bluemap.circle-segments", 48));
        this.bluemapContourStep = Math.max(8, cfg.getInt("bluemap.contour-step", 16));
        this.bluemapLineWidth = Math.max(1, cfg.getInt("bluemap.line-width", 2));
        this.bluemapDepthTest = cfg.getBoolean("bluemap.depth-test", false);
        this.nationLine = rgba(cfg, "bluemap.nation-line", 255, 200, 64, 0.95f);
        this.nationFill = rgba(cfg, "bluemap.nation-fill", 255, 200, 64, 0.10f);
        this.enforceTownRules = cfg.getBoolean("enforce-town-rules", true);
        this.bluemapTownSetId = cfg.getString("bluemap.marker-set-towns", "root-territories-towns");
        this.bluemapTownsLabel = cfg.getString("bluemap.towns-label", "Independent town territories");
        this.townLine = rgba(cfg, "bluemap.town-line", 96, 160, 255, 0.95f);
        this.townFill = rgba(cfg, "bluemap.town-fill", 96, 160, 255, 0.10f);
        this.bluemapSpawnShapeY = (float) cfg.getDouble("bluemap.spawn.shape-y", 90);
        this.spawnGriefLine = rgba(cfg, "bluemap.spawn.grief-line", 80, 220, 80, 0.95f);
        this.spawnGriefFill = rgba(cfg, "bluemap.spawn.grief-fill", 80, 220, 80, 0.16f);
        this.spawnWallLine = rgba(cfg, "bluemap.spawn.wall-line", 255, 230, 0, 0.9f);
        this.enterNation = cfg.getString("messages.enter-nation", "&7Entering nation &b{nation}");
        this.exitNation = cfg.getString("messages.exit-nation", "&7Leaving nation &b{nation}");
        this.enterTown = cfg.getString("messages.enter-town", "&7Entering town &b{town}");
        this.exitTown = cfg.getString("messages.exit-town", "&7Leaving town &b{town}");
        this.denyCreateTown = cfg.getString(
                "messages.deny-create-town",
                "&cYou cannot found a town inside another town or nation's influence.");
        this.denyJoinNation = cfg.getString(
                "messages.deny-join-nation",
                "&cYour town's influence must touch or overlap the nation's territory before joining.");
        this.particlesEnabled = cfg.getBoolean("particles.enabled", false);
        this.particleIntervalTicks = Math.max(5, cfg.getInt("particles.interval_ticks", 16));
        this.particleBatchSize = Math.max(16, cfg.getInt("particles.batch_size", 120));
        this.particleCount = Math.max(1, cfg.getInt("particles.count", 8));
        this.particleSize = (float) Math.max(1.0, Math.min(4.0, cfg.getDouble("particles.size", 3.0)));
        this.particleSpread = Math.max(0.05, cfg.getDouble("particles.spread", 0.22));
        this.particleLayers = Math.max(1, Math.min(3, cfg.getInt("particles.layers", 2)));
        this.particleSurfaceYOffset = cfg.getInt("particles.surface_y_offset", 1);
        int legacyGridStep = cfg.getInt("particles.grid_step", 4);
        this.particleContourStep = Math.max(8, cfg.getInt("particles.contour-step", 16));
        this.particleEdgeSpacing = Math.max(1, cfg.getInt("particles.edge-spacing", legacyGridStep));
        this.particleViewRadius = Math.max(32, cfg.getDouble("particles.view_radius", 96));
        this.nationParticleRgb = rgb(cfg, "particles.nation-color", 255, 40, 40);
        this.townParticleRgb = rgb(cfg, "particles.town-color", 30, 144, 255);
        this.wildernessAlertsEnabled = cfg.getBoolean("wilderness-alerts.enabled", true);
        this.wildernessAlertCooldownMinutes = Math.max(1, cfg.getInt("wilderness-alerts.cooldown-minutes", 3));
        this.wildernessMinYAboveSea = cfg.getInt("wilderness-alerts.min-y-above-sea-level", 0);
        this.wildernessAlertTown = cfg.getString(
                "messages.wilderness-intrusion-town",
                "&7Satellite imagery shows &f{player} &7is stealing resources in your territory.");
        this.wildernessAlertNation = cfg.getString(
                "messages.wilderness-intrusion-nation",
                "&7Satellite imagery shows &f{player} &7is stealing resources in your nation's wilderness territory.");
        this.muteSuffix = cfg.getString("messages.mute-suffix", "");
        this.notificationsEnabled = cfg.getString(
                "messages.notifications-enabled",
                "&aTerritory border messages enabled.");
        this.notificationsDisabled = cfg.getString(
                "messages.notifications-disabled",
                "&7Territory border messages muted. &f/territories notifications on &7to restore.");
        this.notificationsStatusOn = cfg.getString(
                "messages.notifications-status-on",
                "&7Border messages: &aenabled");
        this.notificationsStatusOff = cfg.getString(
                "messages.notifications-status-off",
                "&7Border messages: &7muted");
        this.messageCooldownSeconds = Math.max(0, cfg.getInt("message-cooldown-seconds", 8));
    }

    private static float[] rgba(FileConfiguration cfg, String path, int r, int g, int b, float a) {
        var list = cfg.getList(path);
        if (list != null && list.size() >= 4) {
            return new float[] {
                    toFloat(list.get(0), r),
                    toFloat(list.get(1), g),
                    toFloat(list.get(2), b),
                    toFloat(list.get(3), a),
            };
        }
        return new float[] {r, g, b, a};
    }

    private static float toFloat(Object value, float fallback) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        try {
            return Float.parseFloat(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static int[] rgb(FileConfiguration cfg, String path, int r, int g, int b) {
        var list = cfg.getList(path);
        if (list != null && list.size() >= 3) {
            return new int[] {
                    (int) toFloat(list.get(0), r),
                    (int) toFloat(list.get(1), g),
                    (int) toFloat(list.get(2), b),
            };
        }
        return new int[] {r, g, b};
    }

    public boolean enabled() { return enabled; }
    public int refreshMinutes() { return refreshMinutes; }
    public double nationCapitalBuffer() { return nationCapitalBuffer; }
    public double townBuffer() { return townBuffer; }
    public double mapOutlineInset() { return mapOutlineInset; }
    public boolean bluemapEnabled() { return bluemapEnabled; }
    public String bluemapNationSetId() { return bluemapNationSetId; }
    public String bluemapNationsLabel() { return bluemapNationsLabel; }
    public float bluemapShapeY() { return bluemapShapeY; }
    public int bluemapCircleSegments() { return bluemapCircleSegments; }
    public int bluemapContourStep() { return bluemapContourStep; }
    public int bluemapLineWidth() { return bluemapLineWidth; }
    public boolean bluemapDepthTest() { return bluemapDepthTest; }
    public float[] nationLine() { return nationLine; }
    public float[] nationFill() { return nationFill; }
    public boolean enforceTownRules() { return enforceTownRules; }
    public String bluemapTownSetId() { return bluemapTownSetId; }
    public String bluemapTownsLabel() { return bluemapTownsLabel; }
    public float[] townLine() { return townLine; }
    public float[] townFill() { return townFill; }
    public float bluemapSpawnShapeY() { return bluemapSpawnShapeY; }
    public float[] spawnGriefLine() { return spawnGriefLine; }
    public float[] spawnGriefFill() { return spawnGriefFill; }
    public float[] spawnWallLine() { return spawnWallLine; }
    public String enterNation() { return enterNation; }
    public String exitNation() { return exitNation; }
    public String enterTown() { return enterTown; }
    public String exitTown() { return exitTown; }
    public String denyCreateTown() { return denyCreateTown; }
    public String denyJoinNation() { return denyJoinNation; }
    public boolean particlesEnabled() { return particlesEnabled; }
    public int particleIntervalTicks() { return particleIntervalTicks; }
    public int particleBatchSize() { return particleBatchSize; }
    public int particleCount() { return particleCount; }
    public float particleSize() { return particleSize; }
    public double particleSpread() { return particleSpread; }
    public int particleLayers() { return particleLayers; }
    public int particleSurfaceYOffset() { return particleSurfaceYOffset; }
    public int particleContourStep() { return particleContourStep; }
    public int particleEdgeSpacing() { return particleEdgeSpacing; }
    public double particleViewRadius() { return particleViewRadius; }
    public double particleViewRadiusSq() { return particleViewRadius * particleViewRadius; }
    public int[] nationParticleRgb() { return nationParticleRgb; }
    public int[] townParticleRgb() { return townParticleRgb; }
    public boolean wildernessAlertsEnabled() { return wildernessAlertsEnabled; }
    public long wildernessAlertCooldownMs() { return wildernessAlertCooldownMinutes * 60_000L; }
    public int wildernessMinYAboveSea() { return wildernessMinYAboveSea; }
    public String wildernessAlertTown() { return wildernessAlertTown; }
    public String wildernessAlertNation() { return wildernessAlertNation; }
    public String muteSuffix() { return muteSuffix; }
    public String notificationsEnabled() { return notificationsEnabled; }
    public String notificationsDisabled() { return notificationsDisabled; }
    public String notificationsStatusOn() { return notificationsStatusOn; }
    public String notificationsStatusOff() { return notificationsStatusOff; }
    public int messageCooldownSeconds() { return messageCooldownSeconds; }
}
