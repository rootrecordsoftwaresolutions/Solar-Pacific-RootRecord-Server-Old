package com.rootrecord.minecraft.rootskills.skills;

import com.rootrecord.minecraft.rootskills.RootSkillsPlugin;
import com.rootrecord.minecraft.rootskills.api.SkillId;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/** Loads skills/*.yml — XP tables + talent refs for v1. */
public final class SkillCatalog {

    private final RootSkillsPlugin plugin;
    private final Map<SkillId, SkillDefinition> skills = new EnumMap<>(SkillId.class);

    public SkillCatalog(RootSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        skills.clear();
        File dir = new File(plugin.getDataFolder(), "skills");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        for (SkillId id : SkillId.values()) {
            String resource = "skills/" + id.key() + ".yml";
            File out = new File(dir, id.key() + ".yml");
            if (!out.exists()) {
                try {
                    plugin.saveResource(resource, false);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Missing bundled skill yaml: " + resource);
                }
            }
        }
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                try {
                    loadFile(YamlConfiguration.loadConfiguration(file));
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "Skill load failed: " + file.getName(), ex);
                }
            }
        }
        // Ensure every skill has a definition (defaults if yaml missing)
        for (SkillId id : SkillId.values()) {
            if (!skills.containsKey(id)) {
                skills.put(id, defaultDef(id));
                try (InputStream in = plugin.getResource("skills/" + id.key() + ".yml")) {
                    if (in != null) {
                        loadFile(YamlConfiguration.loadConfiguration(
                                new InputStreamReader(in, StandardCharsets.UTF_8)));
                    }
                } catch (Exception ignored) {
                }
            }
        }
        plugin.getLogger().info("Loaded " + skills.size() + " skill definitions");
    }

    public void reload() {
        load();
    }

    private void loadFile(YamlConfiguration yaml) {
        Optional<SkillId> idOpt = SkillId.fromString(yaml.getString("id", ""));
        if (idOpt.isEmpty()) {
            return;
        }
        SkillId id = idOpt.get();
        Material icon = Material.BOOK;
        try {
            icon = Material.valueOf(yaml.getString("icon", "BOOK").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
        }
        Map<String, Long> blockXp = new HashMap<>();
        ConfigurationSection blocks = yaml.getConfigurationSection("xp-sources.block-break");
        if (blocks != null) {
            for (String key : blocks.getKeys(false)) {
                blockXp.put(key.toUpperCase(Locale.ROOT), blocks.getLong(key));
            }
        }
        long defaultBlock = yaml.getLong("xp-sources.default-block", fallbackDefaultBlock(id));
        long combatHit = yaml.getLong("xp-sources.combat-hit", 1L);
        long combatKill = yaml.getLong("xp-sources.combat-kill", 10L);
        long action = yaml.getLong("xp-sources.action", fallbackAction(id));
        List<String> talents = yaml.getStringList("talents");
        skills.put(id, new SkillDefinition(
                id,
                yaml.getString("display-name", id.key()),
                yaml.getBoolean("enabled", true),
                yaml.getInt("max-level", 1000),
                icon,
                blockXp,
                defaultBlock,
                combatHit,
                combatKill,
                action,
                talents));
    }

    private static SkillDefinition defaultDef(SkillId id) {
        return new SkillDefinition(
                id,
                capitalize(id.key()),
                true,
                1000,
                Material.BOOK,
                Map.of(),
                fallbackDefaultBlock(id),
                1L,
                10L,
                fallbackAction(id),
                List.of());
    }

    private static long fallbackDefaultBlock(SkillId id) {
        return switch (id) {
            case MINING -> 5L;
            case WOODCUTTING -> 8L;
            case HERBALISM -> 6L;
            case EXCAVATION -> 4L;
            default -> 5L;
        };
    }

    private static long fallbackAction(SkillId id) {
        return switch (id) {
            case FISHING -> 25L;
            case ALCHEMY -> 20L;
            case TAMING -> 50L;
            case SMELTING -> 5L;
            case REPAIR -> 8L;
            case SALVAGE -> 12L;
            case ACROBATICS -> 2L;
            case DEFENSE -> 1L;
            case ELYTRA -> 2L;
            default -> 5L;
        };
    }

    private static String capitalize(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }

    public Optional<SkillDefinition> get(SkillId id) {
        return Optional.ofNullable(skills.get(id));
    }

    public SkillDefinition require(SkillId id) {
        return skills.getOrDefault(id, defaultDef(id));
    }

    public Map<SkillId, SkillDefinition> all() {
        return Map.copyOf(skills);
    }

    public boolean enabled(SkillId id) {
        SkillDefinition def = skills.get(id);
        return def == null || def.enabled();
    }
}
