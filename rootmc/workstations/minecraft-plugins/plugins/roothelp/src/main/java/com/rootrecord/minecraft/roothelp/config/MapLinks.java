package com.rootrecord.minecraft.roothelp.config;

import com.rootrecord.minecraft.common.RootMcMapUrls;
import com.rootrecord.minecraft.roothelp.RootHelpPlugin;
import org.bukkit.entity.Player;

public final class MapLinks {

    private MapLinks() {}

    public static String resolveBaseUrl(RootHelpPlugin plugin) {
        return RootMcMapUrls.resolveBaseUrl(plugin, plugin.helpConfig().mapBaseUrl());
    }

    public static String withPlayerAnchor(String baseUrl, Player player) {
        return RootMcMapUrls.withPlayerAnchor(baseUrl, player);
    }
}
