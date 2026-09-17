package com.rootrecord.minecraft.rootmapper;

import com.rootrecord.minecraft.common.RootRecordFolders;

public final class MapperFiles {

    private MapperFiles() {}

    public static String defaultPolygonFile(String areaId) {
        return RootRecordFolders.ROOT_MAPPER_AREAS_DIR + "/" + sanitize(areaId) + ".txt";
    }

    public static String defaultLavaFile(String areaId) {
        return RootRecordFolders.ROOT_MAPPER_AREAS_DIR + "/" + sanitize(areaId) + "-lava.txt";
    }

    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "area";
        }
        return raw.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "_");
    }
}
