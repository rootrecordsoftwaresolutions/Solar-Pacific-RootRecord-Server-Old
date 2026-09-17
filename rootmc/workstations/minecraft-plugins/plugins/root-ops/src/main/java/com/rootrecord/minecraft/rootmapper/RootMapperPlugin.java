package com.rootrecord.minecraft.rootmapper;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RootMapperPlugin {
    private final org.bukkit.plugin.java.JavaPlugin host;

    public RootMapperPlugin(org.bukkit.plugin.java.JavaPlugin host) {
        this.host = host;
    }

    public org.bukkit.plugin.java.JavaPlugin host() { return host; }
    public org.bukkit.plugin.Plugin getPlugin() { return host; }
    public java.util.logging.Logger getLogger() { return host.getLogger(); }
    public org.bukkit.Server getServer() { return host.getServer(); }
    public java.io.File getDataFolder() { return host.getDataFolder(); }
    public org.bukkit.command.PluginCommand getCommand(String name) { return host.getCommand(name); }
    public org.bukkit.plugin.PluginDescriptionFile getDescription() { return host.getDescription(); }
    public java.io.InputStream getResource(String path) { return host.getResource(path); }
    public void saveResource(String path, boolean replace) { host.saveResource(path, replace); }
    public org.bukkit.scheduler.BukkitScheduler getScheduler() { return host.getServer().getScheduler(); }

    private RootRecordYamlConfig yaml;
    private AreaRegistry registry;
    private final Map<UUID, MapperSession> sessions = new ConcurrentHashMap<>();

    public void enable() {
        RootRecordFolders.ensureDir(host);
        yaml = new RootRecordYamlConfig(host, RootRecordFolders.ROOT_MAPPER_CONFIG, "root-mapper.yml");
        registry = new AreaRegistry(host, yaml);
        reloadAll();

        var cmd = getCommand("mapper");
        if (cmd != null) {
            MapperCommand handler = new MapperCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        getServer().getPluginManager().registerEvents(new MapperListener(this), host);
        getServer().getPluginManager().registerEvents(new QuitListener(), host);

        getLogger().info("Root-Mapper " + getDescription().getVersion()
                + " — /mapper for waypoint, refine, and lava area mapping.");
    }

    public void reloadAll() {
        registry.reload();
    }

    public AreaRegistry registry() {
        return registry;
    }

    public MapperSession session(UUID playerId) {
        return sessions.get(playerId);
    }

    public void setSession(UUID playerId, MapperSession session) {
        sessions.put(playerId, session);
    }

    public void clearSession(UUID playerId) {
        sessions.remove(playerId);
        for (MappedArea area : registry.areas()) {
            LavaAreaStore store = registry.lavaStore(area.id());
            if (store != null) {
                store.clearPending(playerId);
            }
        }
    }

    public String colorize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    private final class QuitListener implements Listener {
        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            clearSession(event.getPlayer().getUniqueId());
        }
    }
    public void disable() {
    }
}
