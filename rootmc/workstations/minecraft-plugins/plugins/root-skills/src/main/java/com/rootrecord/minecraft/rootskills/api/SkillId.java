package com.rootrecord.minecraft.rootskills.api;

import java.util.Locale;
import java.util.Optional;

/**
 * Canonical skill identifiers (21 skills).
 */
public enum SkillId {
    MINING,
    WOODCUTTING,
    HERBALISM,
    EXCAVATION,
    FISHING,
    REPAIR,
    SALVAGE,
    SMELTING,
    ALCHEMY,
    TAMING,
    ACROBATICS,
    UNARMED,
    SWORDS,
    AXES,
    ARCHERY,
    CROSSBOWS,
    TRIDENTS,
    MACES,
    SPEARS,
    DEFENSE,
    ELYTRA;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<SkillId> fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (SkillId id : values()) {
            if (id.key().equals(normalized) || id.name().equalsIgnoreCase(normalized)) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }

    public static SkillId require(String raw) {
        return fromString(raw).orElseThrow(() -> new IllegalArgumentException("Unknown skill: " + raw));
    }
}
