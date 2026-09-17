package com.rootrecord.minecraft.rootskills.classes;

import com.rootrecord.minecraft.rootskills.RootSkillsPlugin;
import com.rootrecord.minecraft.rootskills.api.SkillId;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ClassManager {

    public record ClassDefinition(
            String id,
            String displayName,
            String description,
            List<String> primarySkills,
            Map<SkillId, Double> skillMultipliers) {}

    private final RootSkillsPlugin plugin;
    private final Map<String, ClassDefinition> classes = new LinkedHashMap<>();

    public ClassManager(RootSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        classes.clear();
        File dir = new File(plugin.getDataFolder(), "classes");
        if (!dir.exists()) {
            dir.mkdirs();
            plugin.saveResource("classes/warrior.yml", false);
            plugin.saveResource("classes/artisan.yml", false);
        }
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                loadYaml(YamlConfiguration.loadConfiguration(f));
            }
        }
        if (classes.isEmpty()) {
            for (String name : List.of("warrior.yml", "artisan.yml")) {
                try (InputStream in = plugin.getResource("classes/" + name)) {
                    if (in != null) {
                        loadYaml(YamlConfiguration.loadConfiguration(
                                new InputStreamReader(in, StandardCharsets.UTF_8)));
                    }
                } catch (Exception ignored) {
                }
            }
        }
        plugin.getLogger().info("Loaded " + classes.size() + " classes");
    }

    private void loadYaml(YamlConfiguration yaml) {
        String id = yaml.getString("id");
        if (id == null) {
            return;
        }
        Map<SkillId, Double> mults = new HashMap<>();
        List<String> primary = yaml.getStringList("primary-skills");
        for (String s : primary) {
            SkillId.fromString(s).ifPresent(sk -> mults.put(sk, 1.25));
        }
        if (yaml.isConfigurationSection("skill-multipliers")) {
            for (String key : yaml.getConfigurationSection("skill-multipliers").getKeys(false)) {
                SkillId.fromString(key).ifPresent(sk ->
                        mults.put(sk, yaml.getDouble("skill-multipliers." + key, 1.0)));
            }
        }
        classes.put(id.toLowerCase(Locale.ROOT), new ClassDefinition(
                id.toLowerCase(Locale.ROOT),
                yaml.getString("display-name", id),
                yaml.getString("description", ""),
                primary,
                mults));
    }

    public void reload() {
        load();
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("features.classes", true);
    }

    public Optional<ClassDefinition> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(classes.get(id.toLowerCase(Locale.ROOT)));
    }

    public Map<String, ClassDefinition> all() {
        return classes;
    }

    public double skillXpMultiplier(String classId, SkillId skill) {
        if (classId == null || skill == null) {
            return 1.0;
        }
        return get(classId).map(c -> c.skillMultipliers().getOrDefault(skill, 1.0)).orElse(1.0);
    }

    public boolean select(java.util.UUID playerId, String classId) {
        Optional<ClassDefinition> def = get(classId);
        if (def.isEmpty()) {
            return false;
        }
        var profile = plugin.repository().getOrCreate(playerId);
        profile.setClassId(def.get().id());
        plugin.repository().saveAsync(profile);
        return true;
    }
}
