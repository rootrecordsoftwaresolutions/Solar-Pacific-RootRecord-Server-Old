package com.rootrecord.minecraft.common;

import org.bukkit.Material;

import java.util.UUID;

/** Cross-plugin hook for wilderness block changes (shaded — use {@link ShadedServiceBridge}). */
public interface RootMcWildernessBlockNotifier {

    void onWildernessBlockChange(
            UUID playerId,
            String playerName,
            Material material,
            String worldName,
            int blockX,
            int blockY,
            int blockZ,
            boolean placing);
}
