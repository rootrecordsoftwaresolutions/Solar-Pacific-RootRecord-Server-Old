package com.rootrecord.minecraft.rootmapper;

public record MappedArea(
        String id,
        String label,
        String polygonFile,
        String lavaFile,
        MapperMode defaultMode) {

    public String displayLabel() {
        return label == null || label.isBlank() ? id : label;
    }
}
