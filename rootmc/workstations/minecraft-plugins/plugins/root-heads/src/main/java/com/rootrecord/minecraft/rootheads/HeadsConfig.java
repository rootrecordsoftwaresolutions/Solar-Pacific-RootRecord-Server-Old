package com.rootrecord.minecraft.rootheads;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HeadsConfig {

    public record MobHead(
            String id,
            EntityType entityType,
            String displayName,
            Material material,
            String texture,
            double chance,
            double lootingBonus
    ) {}

    public record PlayerHeads(
            boolean enabled,
            boolean requirePvp,
            long samePairCooldownMs,
            double chance,
            double lootingBonus,
            String displayName,
            List<String> lore
    ) {}

    private final boolean enabled;
    private final boolean requireDirectPlayerKill;
    private final boolean denySpawner;
    private final boolean denySpawnerEgg;
    private final long sameMobCooldownMs;
    private final Map<String, String> messages;
    private final Map<String, MobHead> mobs;
    private final PlayerHeads playerHeads;

    private HeadsConfig(
            boolean enabled,
            boolean requireDirectPlayerKill,
            boolean denySpawner,
            boolean denySpawnerEgg,
            long sameMobCooldownMs,
            Map<String, String> messages,
            Map<String, MobHead> mobs,
            PlayerHeads playerHeads) {
        this.enabled = enabled;
        this.requireDirectPlayerKill = requireDirectPlayerKill;
        this.denySpawner = denySpawner;
        this.denySpawnerEgg = denySpawnerEgg;
        this.sameMobCooldownMs = sameMobCooldownMs;
        this.messages = messages;
        this.mobs = mobs;
        this.playerHeads = playerHeads;
    }

    public static HeadsConfig from(FileConfiguration cfg) {
        Map<String, String> messages = new LinkedHashMap<>();
        ConfigurationSection msgSec = cfg.getConfigurationSection("messages");
        if (msgSec != null) {
            for (String key : msgSec.getKeys(false)) {
                messages.put(key, msgSec.getString(key, ""));
            }
        }

        Map<String, MobHead> mobs = new LinkedHashMap<>();
        ConfigurationSection mobSec = cfg.getConfigurationSection("mobs");
        if (mobSec != null) {
            for (String id : mobSec.getKeys(false)) {
                ConfigurationSection row = mobSec.getConfigurationSection(id);
                if (row == null || !row.getBoolean("enabled", true)) {
                    continue;
                }
                EntityType type;
                try {
                    type = EntityType.valueOf(id.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                Material mat = Material.matchMaterial(row.getString("material", "PLAYER_HEAD"));
                if (mat == null || mat.isAir()) {
                    mat = Material.PLAYER_HEAD;
                }
                double chance = Math.max(0, Math.min(1, row.getDouble("chance", 0.02)));
                double looting = Math.max(0, row.getDouble("looting-bonus", 0.005));
                String key = id.toLowerCase(Locale.ROOT);
                mobs.put(key, new MobHead(
                        key,
                        type,
                        row.getString("display-name", "&f" + id + " Head"),
                        mat,
                        row.getString("texture", ""),
                        chance,
                        looting));
            }
        }

        ConfigurationSection ph = cfg.getConfigurationSection("player-heads");
        List<String> lore = ph != null ? ph.getStringList("lore") : List.of();
        if (lore.isEmpty()) {
            lore = List.of("&7Root-Heads collectible");
        }
        long pairSec = ph != null ? Math.max(0, ph.getLong("same-pair-cooldown-seconds", 86400)) : 86400L;
        PlayerHeads playerHeads = new PlayerHeads(
                ph == null || ph.getBoolean("enabled", true),
                ph == null || ph.getBoolean("require-pvp", true),
                pairSec * 1000L,
                Math.max(0, Math.min(1, ph != null ? ph.getDouble("chance", 0.05) : 0.05)),
                Math.max(0, ph != null ? ph.getDouble("looting-bonus", 0.0) : 0.0),
                ph != null ? ph.getString("display-name", "&c{player}'s Head") : "&c{player}'s Head",
                Collections.unmodifiableList(new ArrayList<>(lore)));

        long cooldownSec = Math.max(0, cfg.getLong("same-mob-cooldown-seconds", 300));
        return new HeadsConfig(
                cfg.getBoolean("enabled", true),
                cfg.getBoolean("require-direct-player-kill", true),
                cfg.getBoolean("deny-spawner", true),
                cfg.getBoolean("deny-spawner-egg", true),
                cooldownSec * 1000L,
                Collections.unmodifiableMap(messages),
                Collections.unmodifiableMap(mobs),
                playerHeads);
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean requireDirectPlayerKill() {
        return requireDirectPlayerKill;
    }

    public boolean denySpawner() {
        return denySpawner;
    }

    public boolean denySpawnerEgg() {
        return denySpawnerEgg;
    }

    public long sameMobCooldownMs() {
        return sameMobCooldownMs;
    }

    public Map<String, String> messages() {
        return messages;
    }

    public Map<String, MobHead> mobs() {
        return mobs;
    }

    public MobHead mob(String id) {
        if (id == null) {
            return null;
        }
        return mobs.get(id.toLowerCase(Locale.ROOT));
    }

    public MobHead byEntity(EntityType type) {
        if (type == null) {
            return null;
        }
        return mobs.get(type.name().toLowerCase(Locale.ROOT));
    }

    public PlayerHeads playerHeads() {
        return playerHeads;
    }

    public boolean playerHeadsEnabled() {
        return playerHeads.enabled();
    }
}
