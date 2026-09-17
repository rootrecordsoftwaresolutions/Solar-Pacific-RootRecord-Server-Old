package com.rootrecord.minecraft.rootcore.transfer;

import java.util.List;
import java.util.Locale;

/** One peer from GET /api/rootmc/transfer-mesh. */
public record MeshPeer(
        String slug,
        String label,
        String host,
        int port,
        String kind,
        String serverId,
        Boolean online,
        List<String> aliases) {

    public String displayLabel() {
        return label == null || label.isBlank() ? slug : label;
    }

    public boolean matches(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String k = normalizeAlias(raw.trim());
        if (k.equalsIgnoreCase(slug)) {
            return true;
        }
        if (aliases != null) {
            for (String a : aliases) {
                if (a != null && normalizeAlias(a).equals(k)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String normalizeAlias(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String k = key.trim().toLowerCase(Locale.ROOT);
        return switch (k) {
            case "gen1", "g1", "t" -> "towny";
            case "gen2", "g2", "c" -> "claims";
            case "dev", "devportal", "rootmctest", "portal" -> "test";
            default -> k;
        };
    }
}
