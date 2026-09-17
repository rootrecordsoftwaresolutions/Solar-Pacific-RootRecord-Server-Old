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

public final class RootMapperPlugin extends JavaPlugin {

    private RootRecordYamlConfig yaml;
    private AreaRegistry registry;
    private final Map<UUID, MapperSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        RootRecordFolders.ensureDir(this);
        yaml = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_MAPPER_CONFIG, "root-mapper.yml");
        registry = new AreaRegistry(this, yaml);
        reloadAll();

        var cmd = getCommand("mapper");
        if (cmd != null) {
            MapperCommand handler = new MapperCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }

        getServer().getPluginManager().registerEvents(new MapperListener(this), this);
        getServer().getPluginManager().registerEvents(new QuitListener(), this);

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
}
