package com.rootrecord.minecraft.rootmapper;

public record MapperSession(String areaId, MapperMode mode) {

    public String pdcValue() {
        return mode.name() + ":" + areaId;
    }

    public static MapperSession fromPdc(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int sep = raw.indexOf(':');
        if (sep <= 0 || sep >= raw.length() - 1) {
            return null;
        }
        try {
            MapperMode mode = MapperMode.valueOf(raw.substring(0, sep));
            String areaId = raw.substring(sep + 1);
            return new MapperSession(areaId, mode);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
