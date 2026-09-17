package com.rootrecord.minecraft.rootmapper;

/** How an area is mapped in-game. */
public enum MapperMode {
    WAYPOINT,
    REFINE,
    LAVA;

    public static MapperMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return WAYPOINT;
        }
        return switch (raw.trim().toLowerCase()) {
            case "refine" -> REFINE;
            case "lava" -> LAVA;
            default -> WAYPOINT;
        };
    }

    public String configKey() {
        return name().toLowerCase();
    }
}
