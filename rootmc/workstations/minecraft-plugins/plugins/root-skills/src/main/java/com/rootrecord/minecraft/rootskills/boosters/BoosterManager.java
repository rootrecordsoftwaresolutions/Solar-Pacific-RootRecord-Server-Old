package com.rootrecord.minecraft.rootskills.boosters;

import com.rootrecord.minecraft.rootskills.RootSkillsPlugin;
import com.rootrecord.minecraft.rootskills.api.SkillId;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BoosterManager {

    public record BoosterOffer(String id, String displayName, double multiplier, long durationMs, double price) {}

    private final RootSkillsPlugin plugin;
    private final Map<UUID, Map<String, ActiveBooster>> active = new ConcurrentHashMap<>();
    private Economy economy;

    public record ActiveBooster(String id, SkillId skill, double multiplier, long expiresAt) {}

    public BoosterManager(RootSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp =
                    Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
            }
        }
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("features.boosters", true);
    }

    public double xpMultiplier(UUID playerId, SkillId skill) {
        if (!enabled() || playerId == null) {
            return 1.0;
        }
        prune(playerId);
        Map<String, ActiveBooster> map = active.get(playerId);
        if (map == null || map.isEmpty()) {
            return 1.0;
        }
        double mult = 1.0;
        long now = System.currentTimeMillis();
        for (ActiveBooster b : map.values()) {
            if (b.expiresAt() < now) {
                continue;
            }
            if (b.skill() == null || b.skill() == skill) {
                mult *= b.multiplier();
            }
        }
        return mult;
    }

    public void grant(UUID playerId, String boosterId, SkillId skill, double multiplier, long durationMs) {
        long expires = System.currentTimeMillis() + durationMs;
        active.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>())
                .put(boosterId, new ActiveBooster(boosterId, skill, multiplier, expires));
        persist(playerId, boosterId, skill, multiplier, expires);
    }

    public boolean buy(Player player, String boosterId) {
        if (!enabled() || player == null) {
            return false;
        }
        BoosterOffer offer = offer(boosterId);
        if (offer == null) {
            return false;
        }
        if (economy != null) {
            if (!economy.has(player, offer.price())) {
                player.sendMessage(plugin.colorize("&cNot enough Gold."));
                return false;
            }
            economy.withdrawPlayer(player, offer.price());
        }
        grant(player.getUniqueId(), offer.id(), null, offer.multiplier(), offer.durationMs());
        player.sendMessage(plugin.colorize("&aBooster activated: &e" + offer.displayName()));
        return true;
    }

    public BoosterOffer offer(String id) {
        if (id == null) {
            return null;
        }
        // Built-in offers; config can override later
        return switch (id.toLowerCase()) {
            case "xp2" -> new BoosterOffer("xp2", "2x XP (1h)", 2.0, 3_600_000L, 500);
            case "xp15" -> new BoosterOffer("xp15", "1.5x XP (30m)", 1.5, 1_800_000L, 200);
            default -> null;
        };
    }

    private void prune(UUID playerId) {
        Map<String, ActiveBooster> map = active.get(playerId);
        if (map == null) {
            return;
        }
        long now = System.currentTimeMillis();
        map.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
    }

    private void persist(UUID playerId, String boosterId, SkillId skill, double multiplier, long expires) {
        if (plugin.database() == null || !plugin.database().available()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.database().connection();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO " + plugin.schemaManager().table("boosters")
                                 + " (uuid, booster_id, skill_id, multiplier, expires_at) VALUES (?,?,?,?,?)"
                                 + " ON DUPLICATE KEY UPDATE skill_id=VALUES(skill_id),"
                                 + " multiplier=VALUES(multiplier), expires_at=VALUES(expires_at)")) {
                ps.setString(1, playerId.toString());
                ps.setString(2, boosterId);
                ps.setString(3, skill == null ? null : skill.key());
                ps.setDouble(4, multiplier);
                ps.setLong(5, expires);
                ps.executeUpdate();
            } catch (Exception ex) {
                plugin.getLogger().warning("Booster persist failed: " + ex.getMessage());
            }
        });
    }

    public void loadPlayer(UUID playerId) {
        if (plugin.database() == null || !plugin.database().available()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection c = plugin.database().connection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT booster_id, skill_id, multiplier, expires_at FROM "
                                 + plugin.schemaManager().table("boosters")
                                 + " WHERE uuid=? AND expires_at > ?")) {
                ps.setString(1, playerId.toString());
                ps.setLong(2, System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) {
                    Map<String, ActiveBooster> map = new ConcurrentHashMap<>();
                    while (rs.next()) {
                        String skillRaw = rs.getString("skill_id");
                        SkillId skill = skillRaw == null ? null : SkillId.fromString(skillRaw).orElse(null);
                        map.put(rs.getString("booster_id"), new ActiveBooster(
                                rs.getString("booster_id"),
                                skill,
                                rs.getDouble("multiplier"),
                                rs.getLong("expires_at")));
                    }
                    if (!map.isEmpty()) {
                        active.put(playerId, map);
                    }
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Booster load failed: " + ex.getMessage());
            }
        });
    }
}
