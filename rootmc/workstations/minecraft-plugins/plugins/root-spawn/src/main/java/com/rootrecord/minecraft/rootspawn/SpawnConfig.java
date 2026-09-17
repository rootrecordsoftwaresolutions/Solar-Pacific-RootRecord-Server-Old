package com.rootrecord.minecraft.rootspawn;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;

public record SpawnConfig(
        boolean particlesEnabled,
        RingParticleStyle particleStyle,
        int particleIntervalTicks,
        int particleBatchSize,
        int particleCount,
        float particleSize,
        double particleSpread,
        int particleLayers,
        int surfaceYOffset,
        int protectionYMin,
        int griefBufferBlocks,
        int basementYTop,
        int basementYBottom,
        int mobCleanupTicks,
        String prefix,
        String enterChat,
        String leaveChat,
        String enterTitle,
        String enterSubtitle,
        String enterActionBar,
        String leaveTitle,
        String leaveSubtitle,
        String leaveActionBar,
        String buildStarted,
        String buildPull,
        String buildPush,
        String buildHint,
        int buildExpandMaxDist,
        String buildSaved,
        String buildChamberStarted,
        String buildChamberPoint,
        String buildWellStarted,
        String buildWellPoint,
        String buildLavaStarted,
        String buildLavaCeilingHint,
        String buildLavaCeiling,
        String buildLavaFloorHint,
        String buildLavaSpotSaved,
        int chamberDurationSeconds,
        int chamberCooldownHours,
        double chamberPrizeGold,
        String wellWarning,
        String chamberStarted,
        String chamberStartTitle,
        String chamberStartSubtitle,
        String chamberBossTitle,
        String chamberActionBar,
        String chamberDisqualified,
        String chamberDeath,
        String chamberCooldownDeny,
        String chamberWin,
        String chamberWinTitle,
        String chamberWinSubtitle,
        boolean chamberStatsMysqlEnabled,
        String chamberLavaBreachTitle,
        String chamberLavaCeilingBreach,
        String chamberLavaCeilingBreachSubtitle,
        String chamberLavaFloorBreach,
        String chamberLavaBreachSound,
        String chamberLavaFloorSound,
        String chamberPrefix,
        String chamberLavaArmed,
        String chamberLavaNoSpots,
        String chamberLavaSpotOpen,
        String chamberLavaSpotOpenSubtitle) {

    public long chamberCooldownMs() {
        return chamberCooldownHours * 3_600_000L;
    }

    public enum RingParticleStyle {
        YELLOW_DUST,
        BLUE_DUST,
        GREEN_VILLAGER;

        static RingParticleStyle from(String raw) {
            if (raw == null) {
                return YELLOW_DUST;
            }
            return switch (raw.trim().toLowerCase()) {
                case "blue" -> BLUE_DUST;
                case "green", "villager", "happy_villager" -> GREEN_VILLAGER;
                default -> YELLOW_DUST;
            };
        }
    }

    public Particle.DustOptions ringDust() {
        Color color = switch (particleStyle()) {
            case BLUE_DUST -> Color.fromRGB(30, 144, 255);
            case YELLOW_DUST -> Color.fromRGB(255, 230, 0);
            default -> Color.fromRGB(255, 230, 0);
        };
        return new Particle.DustOptions(color, particleSize());
    }

    public static SpawnConfig from(FileConfiguration cfg) {
        return new SpawnConfig(
                cfg.getBoolean("particles.enabled", false),
                RingParticleStyle.from(cfg.getString("particles.color", "yellow")),
                Math.max(5, cfg.getInt("particles.interval_ticks", 16)),
                Math.max(16, cfg.getInt("particles.batch_size", 100)),
                Math.max(1, cfg.getInt("particles.count", 8)),
                (float) Math.max(1.0, Math.min(4.0, cfg.getDouble("particles.size", 3.0))),
                Math.max(0.05, cfg.getDouble("particles.spread", 0.22)),
                Math.max(1, Math.min(3, cfg.getInt("particles.layers", 2))),
                cfg.getInt("particles.surface_y_offset", 1),
                cfg.getInt("protection.y_min", 0),
                Math.max(0, cfg.getInt("protection.grief_buffer_blocks", 5)),
                cfg.getInt("basement.y_top", BasementZone.DEFAULT_TOP),
                cfg.getInt("basement.y_bottom", BasementZone.DEFAULT_BOTTOM),
                Math.max(40, cfg.getInt("protection.mob_cleanup_ticks", 80)),
                cfg.getString("messages.prefix", ""),
                cfg.getString("messages.enter_chat",
                        "&a&lSpawn Safe Zone&r &7— &fNo PVP, no mobs, no griefing inside the ring."),
                cfg.getString("messages.leave_chat",
                        "&7You left the &fSpawn Safe Zone&7. &cPVP and mobs are active again."),
                cfg.getString("messages.enter_title", "&9&lSpawn Safe Zone"),
                cfg.getString("messages.enter_subtitle", "&7Protected ring · No PVP · No mobs"),
                cfg.getString("messages.enter_actionbar",
                        "&aSpawn safe zone &7— no PvP, mobs, or griefing inside the walls"),
                cfg.getString("messages.leave_title", "&7Wilderness"),
                cfg.getString("messages.leave_subtitle", "&cPVP enabled · Normal mob spawns"),
                cfg.getString("messages.leave_actionbar", "&7Outside spawn walls &8— &cPvP and mobs active"),
                cfg.getString("messages.build_started",
                        "&aSpawn refine — &7click &finside&7 to shrink, &fjust outside&7 the walls to expand."),
                cfg.getString("messages.build_pull", "&7Border pulled inward at &f{x}, {z}&7."),
                cfg.getString("messages.build_push", "&7Border pushed outward at &f{x}, {z}&7."),
                cfg.getString("messages.build_hint",
                        "&7Click &finside&7 the ring to shrink, or &fwithin 12 blocks outside&7 to expand."),
                Math.max(4, cfg.getInt("build.expand_max_dist", 12)),
                cfg.getString("messages.build_saved", "&aUpdated &fspawnarea-refined.txt&a."),
                cfg.getString("messages.build_chamber_started",
                        "&6Chamber mapper — &7right-click blocks along the &fchamber&7 perimeter."),
                cfg.getString("messages.build_chamber_point",
                        "&6Chamber point &f#{n}&6: &f{x}, {z}"),
                cfg.getString("messages.build_well_started",
                        "&bWell mapper — &7right-click blocks around the &fwell&7 opening."),
                cfg.getString("messages.build_well_point",
                        "&bWell point &f#{n}&b: &f{x}, {z}"),
                cfg.getString("messages.build_lava_started",
                        "&cLava mapper — &7click each &fceiling&7 block, then its &ffloor&7."),
                cfg.getString("messages.build_lava_ceiling_hint",
                        "&7Click the &fceiling&7 block for the next lava spot."),
                cfg.getString("messages.build_lava_ceiling",
                        "&cCeiling marked at &f{x}, {y}, {z}&c."),
                cfg.getString("messages.build_lava_floor_hint",
                        "&7Now click the &ffloor&7 block below this spot."),
                cfg.getString("messages.build_lava_spot_saved",
                        "&aLava spot &f#{n}&a saved — ceiling &f{cx}, {cy}, {cz}&a · floor &f{fx}, {fy}, {fz}&a."),
                Math.max(60, cfg.getInt("chamber.duration_seconds", 300)),
                Math.max(1, cfg.getInt("chamber.cooldown_hours", 24)),
                Math.max(0, cfg.getDouble("chamber.prize_gold", 100.0)),
                cfg.getString("messages.well_warning",
                        "&c&lWarning! &7Falling down the well starts the &fchamber survival&7 minigame."),
                cfg.getString("messages.chamber_started",
                        "&c&lChamber run started! &7Survive &f5 minutes&7 — leaving disqualifies you."),
                cfg.getString("messages.chamber_start_title", "&c&lChamber Survival"),
                cfg.getString("messages.chamber_start_subtitle", "&7PVP & mobs enabled · No fall damage"),
                cfg.getString("messages.chamber_boss_title", "&cSurvive: &f{time} &7· Lava &f{lava_open}/{lava_total}"),
                cfg.getString("messages.chamber_actionbar",
                        "&7Time &f{time} &7· Lava breaches &f{lava_open}/{lava_total} &7(&f{lava_remaining}&7 left)"),
                cfg.getString("messages.chamber_disqualified",
                        "&cDisqualified! &7You left the chamber early. Try again in 24 hours."),
                cfg.getString("messages.chamber_death",
                        "&cYou died in the chamber. &7Try again in 24 hours."),
                cfg.getString("messages.chamber_cooldown_deny",
                        "&7Chamber minigame on cooldown (&f{time}&7 remaining). Mines access only."),
                cfg.getString("messages.chamber_win",
                        "&a&lYou survived! &7+&f{gold} G &7from the treasury."),
                cfg.getString("messages.chamber_win_title", "&a&lVictory!"),
                cfg.getString("messages.chamber_win_subtitle", "&7+{gold} G from treasury"),
                cfg.getBoolean("chamber.stats.mysql_enabled", true),
                cfg.getString("messages.chamber_lava_breach_title", "&c&lLAVA BREACH!"),
                cfg.getString("messages.chamber_lava_ceiling_breach",
                        "&c&lLava breach! &7Spot &f#{n}&7/&f{total} &7— &fceiling&7 collapsed!"),
                cfg.getString("messages.chamber_lava_ceiling_breach_subtitle",
                        "&4Ceiling gave way at spot &f#{n}"),
                cfg.getString("messages.chamber_lava_floor_breach",
                        "&4Floor collapsed! &7Spot &f#{n}"),
                cfg.getString("sounds.chamber_lava_breach", "ENTITY_GHAST_SCREAM"),
                cfg.getString("sounds.chamber_lava_floor", "BLOCK_LAVA_POP"),
                cfg.getString("messages.chamber_prefix", "&c[Chamber] &r"),
                cfg.getString("messages.chamber_lava_armed",
                        "&e&l{lava_total} lava vents armed&7 — they will open randomly! Watch the ceiling."),
                cfg.getString("messages.chamber_lava_no_spots",
                        "&c&lNo lava vents loaded! &7Ask staff to map spots with &f/rootspawn lava&7."),
                cfg.getString("messages.chamber_lava_spot_open",
                        "&c&lLAVA BREACH! &7Vent &f#{n}&7/&f{total} &7opened &7(&f{blocks}&7 blocks)"),
                cfg.getString("messages.chamber_lava_spot_open_subtitle",
                        "&4Ceiling collapsed — spot &f#{n}&4/&f{total}"));
    }
}
