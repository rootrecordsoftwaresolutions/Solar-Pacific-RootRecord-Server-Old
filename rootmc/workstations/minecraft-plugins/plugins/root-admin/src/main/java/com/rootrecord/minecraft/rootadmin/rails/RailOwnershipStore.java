package com.rootrecord.minecraft.rootadmin.rails;

import com.rootrecord.minecraft.common.RootRecordFolders;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Tracks who placed each rail block (persisted under plugins/RootMC/rail-ownership.yml). */
public final class RailOwnershipStore {

    public record Owner(UUID uuid, String name) {}

    private final Plugin plugin;
    private final File file;
    private final Map<String, Owner> owners = new ConcurrentHashMap<>();

    public RailOwnershipStore(Plugin plugin) {
        this.plugin = plugin;
        RootRecordFolders.ensureDir(plugin);
        this.file = new File(RootRecordFolders.dir(plugin), "rail-ownership.yml");
        load();
    }

    public static String key(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    public void track(Block block, UUID uuid, String name) {
        if (uuid == null || name == null || name.isBlank()) {
            return;
        }
        owners.put(key(block), new Owner(uuid, name));
        saveQuietly();
    }

    public void trackColumn(RailColumn column, UUID uuid, String name) {
        if (uuid == null || name == null || name.isBlank()) {
            return;
        }
        for (Block block : column.allSlots()) {
            owners.put(key(block), new Owner(uuid, name));
        }
        saveQuietly();
    }

    public void forgetColumn(RailColumn column) {
        boolean changed = false;
        for (Block block : column.allSlots()) {
            if (owners.remove(key(block)) != null) {
                changed = true;
            }
        }
        if (changed) {
            saveQuietly();
        }
    }

    public Owner ownerOf(Block block) {
        return owners.get(key(block));
    }

    public void forget(Block block) {
        if (owners.remove(key(block)) != null) {
            saveQuietly();
        }
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("rails");
        if (section == null) {
            return;
        }
        for (String locKey : section.getKeys(false)) {
            String uuidRaw = section.getString(locKey + ".uuid");
            String name = section.getString(locKey + ".name", "Unknown");
            if (uuidRaw == null || uuidRaw.isBlank()) {
                continue;
            }
            try {
                owners.put(locKey, new Owner(UUID.fromString(uuidRaw), name));
            } catch (IllegalArgumentException ignored) {
                /* skip corrupt row */
            }
        }
    }

    private void saveQuietly() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::saveSync);
    }

    private synchronized void saveSync() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Owner> entry : owners.entrySet()) {
            String base = "rails." + entry.getKey();
            yaml.set(base + ".uuid", entry.getValue().uuid().toString());
            yaml.set(base + ".name", entry.getValue().name());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save rail ownership: " + ex.getMessage());
        }
    }

    public void flush() {
        saveSync();
    }
}
