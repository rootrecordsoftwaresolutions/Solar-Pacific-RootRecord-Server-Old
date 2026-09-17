package com.rootrecord.minecraft.roottry;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Loads catalog.yml (jar default + optional plugins/RootMC/catalog-try.yml override). */
public final class TryCatalog {

    private final Map<String, TryEntry> byId;
    private final List<TryEntry> ordered;
    private final int version;

    private TryCatalog(Map<String, TryEntry> byId, List<TryEntry> ordered, int version) {
        this.byId = byId;
        this.ordered = ordered;
        this.version = version;
    }

    public static TryCatalog load(JavaPlugin plugin, double defaultRewardG) {
        FileConfiguration cfg = loadMerged(plugin);
        int version = cfg.getInt("version", 1);
        List<?> raw = cfg.getList("tries");
        Map<String, TryEntry> map = new LinkedHashMap<>();
        List<TryEntry> list = new ArrayList<>();
        if (raw != null) {
            for (Object o : raw) {
                if (!(o instanceof ConfigurationSection) && !(o instanceof Map<?, ?>)) {
                    continue;
                }
                ConfigurationSection sec;
                if (o instanceof ConfigurationSection cs) {
                    sec = cs;
                } else {
                    YamlConfiguration wrap = new YamlConfiguration();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) o;
                    for (Map.Entry<String, Object> e : m.entrySet()) {
                        wrap.set(e.getKey(), e.getValue());
                    }
                    sec = wrap;
                }
                String id = sec.getString("id", "").trim();
                if (id.isEmpty() || map.containsKey(id)) {
                    continue;
                }
                List<String> match = sec.getStringList("match");
                double reward = sec.contains("reward-g") ? sec.getDouble("reward-g") : defaultRewardG;
                TryEntry entry = new TryEntry(
                        id,
                        sec.getString("title", id),
                        sec.getString("hint", ""),
                        sec.getString("host", "both"),
                        match,
                        reward);
                map.put(id, entry);
                list.add(entry);
            }
        }
        plugin.getLogger().info("Root-Try catalog loaded — " + list.size() + " tries (v" + version + ")");
        return new TryCatalog(Collections.unmodifiableMap(map), Collections.unmodifiableList(list), version);
    }

    private static FileConfiguration loadMerged(JavaPlugin plugin) {
        YamlConfiguration jar = new YamlConfiguration();
        try (InputStream in = plugin.getResource("catalog.yml")) {
            if (in != null) {
                jar.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to read jar catalog.yml: " + e.getMessage());
        }
        File override = com.rootrecord.minecraft.common.RootRecordFolders.configFile(plugin, "catalog-try.yml");
        if (override.isFile()) {
            try {
                YamlConfiguration disk = YamlConfiguration.loadConfiguration(override);
                for (String key : disk.getKeys(false)) {
                    jar.set(key, disk.get(key));
                }
                plugin.getLogger().info("Merged catalog override from " + override.getPath());
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to merge catalog-try.yml: " + e.getMessage());
            }
        }
        return jar;
    }

    public int version() {
        return version;
    }

    public TryEntry get(String id) {
        if (id == null) {
            return null;
        }
        return byId.get(id.trim());
    }

    public List<TryEntry> all() {
        return ordered;
    }

    public List<TryEntry> forHost(String host) {
        String h = host == null ? "both" : host.toLowerCase(Locale.ROOT);
        List<TryEntry> out = new ArrayList<>();
        for (TryEntry e : ordered) {
            if (e.appliesToHost(h)) {
                out.add(e);
            }
        }
        return out;
    }

    /** Prefer the longest matching path so {@code /shop create} beats {@code /shop}. */
    public TryEntry matchCommand(String host, String commandLine) {
        TryEntry best = null;
        int bestLen = -1;
        for (TryEntry e : forHost(host)) {
            int len = e.longestMatchLength(commandLine);
            if (len > bestLen) {
                bestLen = len;
                best = e;
            }
        }
        return best;
    }
}
