package com.rootrecord.minecraft.rootskills.talents;

import com.rootrecord.minecraft.rootskills.RootSkillsPlugin;
import com.rootrecord.minecraft.rootskills.api.SkillId;
import com.rootrecord.minecraft.rootskills.api.events.RootSkillsTalentTriggerEvent;
import com.rootrecord.minecraft.rootskills.model.PlayerSkillsProfile;
import com.rootrecord.minecraft.rootskills.model.TalentState;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public final class TalentManager {

    private static final List<String> BUNDLED_TALENTS = List.of(
            "mining_super_breaker.yml",
            "woodcutting_tree_feller.yml",
            "excavation_giga_drill.yml",
            "herbalism_green_thumb.yml",
            "fishing_ice_fishing.yml",
            "repair_repair_mastery.yml",
            "salvage_arcane_salvage.yml",
            "smelting_flux_mining.yml",
            "alchemy_catalysis.yml",
            "taming_call_of_the_wild.yml",
            "acrobatics_roll.yml",
            "unarmed_berserk.yml",
            "swords_serrated_strikes.yml",
            "axes_skull_splitter.yml",
            "archery_skill_shot.yml",
            "crossbows_piercing.yml",
            "tridents_impale.yml",
            "maces_crush.yml",
            "spears_lunge.yml",
            "defense_thick_skin.yml",
            "elytra_air_mastery.yml");

    private final RootSkillsPlugin plugin;
    private final Map<String, TalentDefinition> talents = new LinkedHashMap<>();

    public TalentManager(RootSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        talents.clear();
        File dir = new File(plugin.getDataFolder(), "talents");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        for (String name : BUNDLED_TALENTS) {
            if (!new File(dir, name).exists()) {
                try {
                    plugin.saveResource("talents/" + name, false);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Missing bundled talent: " + name);
                }
            }
        }
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                try {
                    loadFile(YamlConfiguration.loadConfiguration(file));
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "Talent load failed: " + file.getName(), ex);
                }
            }
        }
        if (talents.isEmpty()) {
            for (String name : BUNDLED_TALENTS) {
                try (InputStream in = plugin.getResource("talents/" + name)) {
                    if (in == null) {
                        continue;
                    }
                    loadFile(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
                } catch (Exception ex) {
                    plugin.getLogger().warning("Classpath talent " + name + ": " + ex.getMessage());
                }
            }
        }
        plugin.getLogger().info("Loaded " + talents.size() + " talents");
    }

    public void reload() {
        load();
    }

    @SuppressWarnings("unchecked")
    private void loadFile(YamlConfiguration yaml) {
        String id = yaml.getString("id");
        if (id == null || id.isBlank()) {
            return;
        }
        Optional<SkillId> skill = SkillId.fromString(yaml.getString("skill", ""));
        if (skill.isEmpty()) {
            plugin.getLogger().warning("Talent " + id + " missing skill");
            return;
        }
        List<String> triggers = yaml.getStringList("triggers");
        List<Map<String, Object>> effects = new ArrayList<>();
        List<?> rawEffects = yaml.getList("effects");
        if (rawEffects != null) {
            for (Object o : rawEffects) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> map = new HashMap<>();
                    m.forEach((k, v) -> map.put(String.valueOf(k), v));
                    effects.add(map);
                } else if (o instanceof ConfigurationSection sec) {
                    effects.add(sec.getValues(false));
                }
            }
        }
        List<Map<String, Object>> conditions = new ArrayList<>();
        List<?> rawCond = yaml.getList("conditions");
        if (rawCond != null) {
            for (Object o : rawCond) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> map = new HashMap<>();
                    m.forEach((k, v) -> map.put(String.valueOf(k), v));
                    conditions.add(map);
                }
            }
        }
        int unlock = yaml.getInt("unlock-level", yaml.getInt("required_level", 1));
        TalentDefinition def = new TalentDefinition(
                id,
                yaml.getString("display-name", id),
                yaml.getString("description", ""),
                skill.get(),
                unlock,
                yaml.getInt("max-rank", 1),
                yaml.getDouble("mana-cost", 0),
                yaml.getInt("cooldown-seconds", 0),
                yaml.getInt("duration-seconds", 0),
                triggers,
                effects,
                conditions);
        talents.put(id.toLowerCase(Locale.ROOT), def);
    }

    public Collection<TalentDefinition> all() {
        return talents.values();
    }

    public Optional<TalentDefinition> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(talents.get(id.toLowerCase(Locale.ROOT)));
    }

    public void unlockByLevel(PlayerSkillsProfile profile) {
        if (profile == null || !plugin.getConfig().getBoolean("features.talents", true)) {
            return;
        }
        boolean changed = false;
        for (TalentDefinition def : talents.values()) {
            int level = profile.skill(def.skill()).level();
            if (level >= def.unlockLevel()) {
                TalentState state = profile.talent(def.id());
                if (!state.unlocked()) {
                    state.setUnlocked(true);
                    if (state.rank() <= 0) {
                        state.setRank(1);
                    }
                    changed = true;
                }
            }
        }
        if (changed) {
            profile.markDirty();
            plugin.repository().saveAsync(profile);
        }
    }

    public void handleTrigger(Player player, String triggerId, Map<String, Object> context) {
        if (player == null || triggerId == null || !plugin.getConfig().getBoolean("features.talents", true)) {
            return;
        }
        PlayerSkillsProfile profile = plugin.repository().getOrCreate(player.getUniqueId());
        long now = System.currentTimeMillis();
        for (TalentDefinition def : talents.values()) {
            if (!def.triggers().contains(triggerId)) {
                continue;
            }
            TalentState state = profile.talent(def.id());
            if (!state.unlocked()) {
                continue;
            }
            // Passive/combat triggers require equipped; activate trigger can run if unlocked
            boolean activate = "on_ability_activate".equals(triggerId);
            if (!activate && !state.equipped()) {
                continue;
            }
            if (state.onCooldown(now)) {
                continue;
            }
            if (!plugin.conditionEval().matches(player, def.conditions(), context)) {
                continue;
            }
            RootSkillsTalentTriggerEvent event =
                    new RootSkillsTalentTriggerEvent(player, def.id(), triggerId);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                continue;
            }
            if (def.manaCost() > 0 && !plugin.manaManager().spend(profile, def.manaCost())) {
                player.sendMessage(plugin.colorize("&cNot enough mana."));
                continue;
            }
            plugin.effectRunner().run(player, def.effects(), context, def);
            if (def.cooldownSeconds() > 0) {
                long until = now + def.cooldownSeconds() * 1000L;
                state.setCooldownUntilMs(until);
                plugin.cooldownService().start(player.getUniqueId(), def.id(), until);
            }
            if (def.durationSeconds() > 0 && plugin.abilitySessions() != null) {
                for (Map<String, Object> effect : def.effects()) {
                    String eid = String.valueOf(effect.getOrDefault("id", ""));
                    plugin.abilitySessions().start(player.getUniqueId(), eid, def.durationSeconds() * 1000L);
                }
            }
            profile.markDirty();
            player.sendMessage(plugin.msg("talents.triggered").replace("{talent}", def.displayName()));
        }
    }

    public boolean activateAbility(Player player, String talentId) {
        Optional<TalentDefinition> def = get(talentId);
        if (def.isEmpty()) {
            return false;
        }
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("talent", talentId);
        handleTrigger(player, "on_ability_activate", ctx);
        return true;
    }
}
