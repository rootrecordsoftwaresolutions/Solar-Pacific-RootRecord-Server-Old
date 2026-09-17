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
        String buildSaved) {

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
                cfg.getString("messages.build_saved", "&aUpdated &fspawnarea-refined.txt&a."));
    }
}
