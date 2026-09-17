package com.rootrecord.minecraft.rootmapper;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public record MapperConfig(
        String prefix,
        int expandMaxDist,
        String started,
        String refineStarted,
        String lavaStarted,
        String waypointSaved,
        String refinePull,
        String refinePush,
        String refineHint,
        String refineSaved,
        String lavaCeiling,
        String lavaFloorHint,
        String lavaCeilingHint,
        String lavaSpotSaved,
        String stopped,
        String areaCreated,
        String areaExists,
        String areaUnknown) {

    public static MapperConfig from(FileConfiguration cfg) {
        return new MapperConfig(
                cfg.getString("prefix", ""),
                cfg.getInt("expand_max_dist", 12),
                msg(cfg, "messages.started", "Mapping &f{area}&7 — use the mapper diamond."),
                msg(cfg, "messages.refine_started", "Refining &f{area}&7."),
                msg(cfg, "messages.lava_started", "Lava mapping &f{area}&7."),
                msg(cfg, "messages.waypoint_saved", "Waypoint &f#{n}&7 at &f{x}, {z}&7."),
                msg(cfg, "messages.refine_pull", "Pulled border at &f{x}, {z}&7."),
                msg(cfg, "messages.refine_push", "Pushed border at &f{x}, {z}&7."),
                msg(cfg, "messages.refine_hint", "Click inside the ring to shrink, or near outside to expand."),
                msg(cfg, "messages.refine_saved", "Boundary saved."),
                msg(cfg, "messages.lava_ceiling", "Ceiling at &f{x}, {y}, {z}&7."),
                msg(cfg, "messages.lava_floor_hint", "Click the floor block."),
                msg(cfg, "messages.lava_ceiling_hint", "Click the ceiling block."),
                msg(cfg, "messages.lava_spot_saved", "Spot &f#{n}&7 saved."),
                msg(cfg, "messages.stopped", "Mapper mode off."),
                msg(cfg, "messages.area_created", "Created area &f{area}&7."),
                msg(cfg, "messages.area_exists", "Area &f{area}&7 already exists."),
                msg(cfg, "messages.area_unknown", "Unknown area &f{area}&7."));
    }

    private static String msg(FileConfiguration cfg, String path, String fallback) {
        String v = cfg.getString(path);
        return v == null || v.isBlank() ? fallback : v;
    }

    public static MappedArea parseArea(String id, ConfigurationSection section) {
        String label = section.getString("label", id);
        String file = section.getString("file", MapperFiles.defaultPolygonFile(id));
        String lavaFile = section.getString("lava_file", MapperFiles.defaultLavaFile(id));
        MapperMode mode = MapperMode.parse(section.getString("mode", "waypoint"));
        return new MappedArea(id, label, file, lavaFile, mode);
    }
}
