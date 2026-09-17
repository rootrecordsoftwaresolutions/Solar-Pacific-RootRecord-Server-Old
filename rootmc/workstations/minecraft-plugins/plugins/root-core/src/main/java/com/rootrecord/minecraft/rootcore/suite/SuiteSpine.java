package com.rootrecord.minecraft.rootcore.suite;

import java.util.List;

/**
 * Network spine + recommended packs for /rootcore connect and operator docs.
 * Required plugins must be present (enabled) for NETWORK CONNECTED.
 */
public final class SuiteSpine {

    /** Bukkit plugin names required for network membership. */
    public static final List<String> REQUIRED_PLUGINS = List.of(
            "Root-Core",
            "RootMC"
    );

    public static final List<Pack> RECOMMENDED_PACKS = List.of(
            new Pack("Economy", List.of("Root-Economy", "Root-Gamble", "Root-Perms", "Root-Times", "Vault")),
            new Pack("Claims", List.of("Root-Claims", "Root-Territories")),
            new Pack("Towny", List.of("Towny", "Vault")),
            new Pack("Ops/QOL", List.of("Root-Essentials", "Root-Play", "Root-Restart"))
    );

    private SuiteSpine() {
    }

    public record Pack(String label, List<String> plugins) {
    }
}
