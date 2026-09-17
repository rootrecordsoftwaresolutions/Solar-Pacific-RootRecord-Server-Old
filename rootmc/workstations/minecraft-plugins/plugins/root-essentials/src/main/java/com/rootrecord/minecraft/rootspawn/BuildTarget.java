package com.rootrecord.minecraft.rootspawn;

/** Active /rootspawn build target for the mapper diamond. */
public enum BuildTarget {
    SPAWN_MAP,
    SPAWN_REFINE;

    public static BuildTarget fromArg(String raw) {
        if (raw == null) {
            return SPAWN_REFINE;
        }
        return switch (raw.trim().toLowerCase()) {
            case "map" -> SPAWN_MAP;
            case "ring", "spawn", "refine" -> SPAWN_REFINE;
            default -> null;
        };
    }

    public String fileName() {
        return switch (this) {
            case SPAWN_MAP -> com.rootrecord.minecraft.common.RootRecordFolders.ROOT_SPAWN_AREA_FILE;
            case SPAWN_REFINE -> com.rootrecord.minecraft.common.RootRecordFolders.ROOT_SPAWN_REFINED_FILE;
        };
    }

    public String label() {
        return switch (this) {
            case SPAWN_MAP -> "spawn perimeter";
            case SPAWN_REFINE -> "spawn ring";
        };
    }
}
