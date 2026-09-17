package com.rootrecord.minecraft.common;

import com.rootrecord.minecraft.common.ChatLinks;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;

/** RootMC BlueMap public URL - proxied at {@link #DEFAULT_MAP_URL}. */
public final class RootMcMapUrls {

    public static final String DEFAULT_MAP_URL = "https://map.rootmc.net";

    private RootMcMapUrls() {}

    public static String resolveBaseUrl(Plugin plugin) {
        return resolveBaseUrl(plugin, null);
    }

    /** {@code override} wins when non-blank (e.g. roothelp.yml {@code map.base-url}). */
    public static String resolveBaseUrl(Plugin plugin, String override) {
        if (override != null && !override.isBlank()) {
            return trimTrailingSlash(override.trim());
        }
        File rootmc = RootRecordFolders.configFile(plugin, RootRecordFolders.ROOTMC_CONFIG);
        if (rootmc.isFile()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(rootmc);
            String fromRootMc = yaml.getString("server.map-url", "");
            if (fromRootMc != null && !fromRootMc.isBlank()) {
                return trimTrailingSlash(fromRootMc.trim());
            }
        }
        return DEFAULT_MAP_URL;
    }

    /** BlueMap webapp anchor: {@code mapId:x:y:z}. */
    public static String withPlayerAnchor(String baseUrl, Player player) {
        return withPlayerAnchor(baseUrl, player, null);
    }

    public static String withPlayerAnchor(String baseUrl, Player player, Plugin plugin) {
        var loc = player.getLocation();
        return withCoordsAnchor(
                baseUrl,
                bluemapMapId(plugin, player.getWorld()),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ());
    }

    /** BlueMap webapp anchor at fixed coords: {@code mapId:x:y:z}. */
    public static String withCoordsAnchor(String baseUrl, String mapId, int x, int y, int z) {
        String id = (mapId == null || mapId.isBlank()) ? "world" : mapId.trim();
        return trimTrailingSlash(baseUrl) + "#" + id + ":" + x + ":" + y + ":" + z;
    }

    public static String bluemapMapId(Player player) {
        return bluemapMapId(null, player != null ? player.getWorld() : null);
    }

    /**
     * Overworld map id comes from {@code server.map-id} in rootmc.yml when set
     * (Gen2 public map uses {@code gen2}; Gen1 leaves it blank -> {@code world}).
     */
    public static String bluemapMapId(Plugin plugin, World world) {
        if (world == null) {
            return overworldMapId(plugin);
        }
        return switch (world.getEnvironment()) {
            case NETHER -> "the_nether";
            case THE_END -> "the_end";
            case NORMAL -> overworldMapId(plugin);
            default -> {
                var key = world.getKey();
                String dim = key != null ? key.getKey() : "";
                if (!dim.isBlank() && !"overworld".equals(dim)) {
                    yield dim;
                }
                yield overworldMapId(plugin);
            }
        };
    }

    public static String overworldMapId(Plugin plugin) {
        if (plugin != null) {
            File rootmc = RootRecordFolders.configFile(plugin, RootRecordFolders.ROOTMC_CONFIG);
            if (rootmc.isFile()) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(rootmc);
                String configured = yaml.getString("server.map-id", "");
                if (configured != null && !configured.isBlank()) {
                    return configured.trim();
                }
            }
        }
        return "world";
    }

    public static void sendOpenMapMessage(
            CommandSender sender,
            Plugin plugin,
            String overrideBaseUrl,
            String headerLegacy,
            java.util.function.Function<String, String> colorize) {
        String base = resolveBaseUrl(plugin, overrideBaseUrl);
        String url = sender instanceof Player player ? withPlayerAnchor(base, player, plugin) : base;

        if (headerLegacy != null && !headerLegacy.isBlank()) {
            sender.sendMessage(colorize.apply(headerLegacy));
        }
        Component link = ChatLinks.labelDashUrl("[Open BlueMap]", url);
        sender.sendMessage(link);
    }

    private static String trimTrailingSlash(String url) {
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
