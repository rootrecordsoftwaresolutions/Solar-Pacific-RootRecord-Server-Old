package com.rootrecord.minecraft.rootmapper;

import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AreaRegistry {

    private final JavaPlugin plugin;
    private final RootRecordYamlConfig yaml;
    private MapperConfig config;
    private final Map<String, MappedArea> areas = new LinkedHashMap<>();
    private final Map<String, PolygonAreaStore> polygonStores = new LinkedHashMap<>();
    private final Map<String, LavaAreaStore> lavaStores = new LinkedHashMap<>();
    private final Map<String, PolygonBoundary> boundaries = new LinkedHashMap<>();

    public AreaRegistry(JavaPlugin plugin, RootRecordYamlConfig yaml) {
        this.plugin = plugin;
        this.yaml = yaml;
    }

    public void reload() {
        yaml.reload();
        FileConfiguration cfg = yaml.config();
        config = MapperConfig.from(cfg);
        areas.clear();
        polygonStores.clear();
        lavaStores.clear();
        boundaries.clear();

        ConfigurationSection section = cfg.getConfigurationSection("areas");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection areaSection = section.getConfigurationSection(id);
                if (areaSection == null) {
                    continue;
                }
                register(MapperConfig.parseArea(id, areaSection));
            }
        }
    }

    public MapperConfig config() {
        return config;
    }

    public MappedArea area(String id) {
        if (id == null) {
            return null;
        }
        return areas.get(id.trim().toLowerCase());
    }

    public List<MappedArea> areas() {
        return List.copyOf(areas.values());
    }

    public List<String> areaIds() {
        return new ArrayList<>(areas.keySet());
    }

    public PolygonAreaStore polygonStore(String areaId) {
        return polygonStores.get(areaId);
    }

    public LavaAreaStore lavaStore(String areaId) {
        return lavaStores.get(areaId);
    }

    public PolygonBoundary boundary(String areaId) {
        return boundaries.get(areaId);
    }

    public void setBoundary(String areaId, PolygonBoundary boundary) {
        boundaries.put(areaId, boundary);
    }

    public void reloadBoundary(String areaId) {
        PolygonAreaStore store = polygonStores.get(areaId);
        if (store != null) {
            boundaries.put(areaId, store.loadOptional());
        }
    }

    public boolean createArea(String rawId, MapperMode mode, String label) {
        String id = MapperFiles.sanitize(rawId);
        if (areas.containsKey(id)) {
            return false;
        }
        String display = label == null || label.isBlank() ? id : label.trim();
        MappedArea area = new MappedArea(id, display, MapperFiles.defaultPolygonFile(id),
                MapperFiles.defaultLavaFile(id), mode);
        register(area);
        FileConfiguration cfg = yaml.config();
        String base = "areas." + id;
        cfg.set(base + ".label", display);
        cfg.set(base + ".file", area.polygonFile());
        cfg.set(base + ".lava_file", area.lavaFile());
        cfg.set(base + ".mode", mode.configKey());
        yaml.save();
        return true;
    }

    private void register(MappedArea area) {
        areas.put(area.id(), area);
        polygonStores.put(area.id(), new PolygonAreaStore(plugin, area.polygonFile(), area.displayLabel()));
        lavaStores.put(area.id(), new LavaAreaStore(plugin, area.lavaFile(), area.displayLabel()));
        boundaries.put(area.id(), polygonStores.get(area.id()).loadOptional());
    }

    public RootRecordYamlConfig yaml() {
        return yaml;
    }

    public List<String> tabAreaIds(String prefix) {
        String needle = prefix == null ? "" : prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String id : areas.keySet()) {
            if (id.startsWith(needle)) {
                out.add(id);
            }
        }
        Collections.sort(out);
        return out;
    }
}
