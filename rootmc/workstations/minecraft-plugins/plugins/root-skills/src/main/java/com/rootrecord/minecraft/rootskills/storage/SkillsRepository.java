package com.rootrecord.minecraft.rootskills.storage;

import com.rootrecord.minecraft.rootskills.RootSkillsPlugin;
import com.rootrecord.minecraft.rootskills.api.SkillId;
import com.rootrecord.minecraft.rootskills.model.PlayerSkillsProfile;
import com.rootrecord.minecraft.rootskills.model.SkillProgress;
import com.rootrecord.minecraft.rootskills.model.TalentState;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class SkillsRepository {

    private final RootSkillsPlugin plugin;
    private final Database database;
    private final SchemaManager schema;
    private final Map<UUID, PlayerSkillsProfile> cache = new ConcurrentHashMap<>();

    public SkillsRepository(RootSkillsPlugin plugin, Database database, SchemaManager schema) {
        this.plugin = plugin;
        this.database = database;
        this.schema = schema;
    }

    public Optional<PlayerSkillsProfile> find(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.get(playerId));
    }

    public PlayerSkillsProfile getOrCreate(UUID playerId) {
        return cache.computeIfAbsent(playerId, id -> {
            PlayerSkillsProfile loaded = loadSync(id);
            return loaded != null ? loaded : new PlayerSkillsProfile(id);
        });
    }

    public void loadAsync(UUID playerId, String username, Runnable onLoaded) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerSkillsProfile profile = loadSync(playerId);
            if (profile == null) {
                profile = new PlayerSkillsProfile(playerId);
            }
            if (username != null) {
                profile.setUsername(username);
            }
            PlayerSkillsProfile finalProfile = profile;
            Bukkit.getScheduler().runTask(plugin, () -> {
                cache.put(playerId, finalProfile);
                if (plugin.talentManager() != null) {
                    plugin.talentManager().unlockByLevel(finalProfile);
                }
                if (onLoaded != null) {
                    onLoaded.run();
                }
            });
        });
    }

    public PlayerSkillsProfile loadSync(UUID playerId) {
        if (playerId == null || database == null || !database.available()) {
            return null;
        }
        try (Connection c = database.connection()) {
            PlayerSkillsProfile profile = new PlayerSkillsProfile(playerId);
            boolean found = false;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT username, class_id, mana, prefs_json, migrated_from, migrated_at FROM "
                            + schema.table("users") + " WHERE uuid=?")) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        found = true;
                        profile.setUsername(rs.getString("username"));
                        profile.setClassId(rs.getString("class_id"));
                        profile.setMana(rs.getDouble("mana"));
                        profile.setPrefsJson(rs.getString("prefs_json"));
                        profile.setMigratedFrom(rs.getString("migrated_from"));
                        long migratedAt = rs.getLong("migrated_at");
                        if (!rs.wasNull()) {
                            profile.setMigratedAt(migratedAt);
                        }
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT skill_id, level, xp, prestige, buffs_json FROM "
                            + schema.table("skills") + " WHERE uuid=?")) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        found = true;
                        Optional<SkillId> skill = SkillId.fromString(rs.getString("skill_id"));
                        if (skill.isEmpty()) {
                            continue;
                        }
                        SkillProgress p = profile.skill(skill.get());
                        p.setLevel(rs.getInt("level"));
                        p.setXp(rs.getLong("xp"));
                        p.setPrestige(rs.getInt("prestige"));
                        p.setBuffsJson(rs.getString("buffs_json"));
                        p.setPrestigeBuff(1.0 + (0.05 * p.prestige()));
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT talent_id, unlocked, equipped, slot FROM "
                            + schema.table("talents") + " WHERE uuid=?")) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        found = true;
                        TalentState t = profile.talent(rs.getString("talent_id"));
                        t.setUnlocked(rs.getBoolean("unlocked"));
                        t.setEquipped(rs.getBoolean("equipped"));
                        t.setSlot(rs.getInt("slot"));
                    }
                }
            }
            profile.clearDirty();
            return found ? profile : null;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Load profile failed for " + playerId + ": " + ex.getMessage(), ex);
            return null;
        }
    }

    public void saveAsync(PlayerSkillsProfile profile) {
        if (profile == null) {
            return;
        }
        profile.markDirty();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> saveSync(profile));
    }

    public void saveSync(PlayerSkillsProfile profile) {
        if (profile == null) {
            return;
        }
        cache.put(profile.playerId(), profile);
        if (database == null || !database.available()) {
            profile.clearDirty();
            return;
        }
        try (Connection c = database.connection()) {
            c.setAutoCommit(false);
            long now = System.currentTimeMillis();
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + schema.table("users")
                            + " (uuid, username, class_id, mana, prefs_json, migrated_from, migrated_at, updated_at)"
                            + " VALUES (?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE"
                            + " username=VALUES(username), class_id=VALUES(class_id), mana=VALUES(mana),"
                            + " prefs_json=VALUES(prefs_json), migrated_from=VALUES(migrated_from),"
                            + " migrated_at=VALUES(migrated_at), updated_at=VALUES(updated_at)")) {
                ps.setString(1, profile.playerId().toString());
                ps.setString(2, profile.username());
                ps.setString(3, profile.classId());
                ps.setDouble(4, profile.mana());
                ps.setString(5, profile.prefsJson());
                ps.setString(6, profile.migratedFrom());
                if (profile.migratedAt() == null) {
                    ps.setObject(7, null);
                } else {
                    ps.setLong(7, profile.migratedAt());
                }
                ps.setLong(8, now);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + schema.table("skills")
                            + " (uuid, skill_id, level, xp, prestige, buffs_json) VALUES (?,?,?,?,?,?)"
                            + " ON DUPLICATE KEY UPDATE level=VALUES(level), xp=VALUES(xp),"
                            + " prestige=VALUES(prestige), buffs_json=VALUES(buffs_json)")) {
                for (SkillProgress sp : profile.skills().values()) {
                    if (sp.level() == 0 && sp.xp() == 0 && sp.prestige() == 0) {
                        continue;
                    }
                    ps.setString(1, profile.playerId().toString());
                    ps.setString(2, sp.skillId().key());
                    ps.setInt(3, sp.level());
                    ps.setLong(4, sp.xp());
                    ps.setInt(5, sp.prestige());
                    ps.setString(6, sp.buffsJson());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + schema.table("talents")
                            + " (uuid, talent_id, unlocked, equipped, slot) VALUES (?,?,?,?,?)"
                            + " ON DUPLICATE KEY UPDATE unlocked=VALUES(unlocked),"
                            + " equipped=VALUES(equipped), slot=VALUES(slot)")) {
                for (TalentState t : profile.talents().values()) {
                    if (!t.unlocked() && !t.equipped()) {
                        continue;
                    }
                    ps.setString(1, profile.playerId().toString());
                    ps.setString(2, t.talentId());
                    ps.setBoolean(3, t.unlocked());
                    ps.setBoolean(4, t.equipped());
                    ps.setInt(5, t.slot());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            c.commit();
            profile.clearDirty();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Save profile failed for " + profile.playerId() + ": " + ex.getMessage(), ex);
        }
    }

    public void setLevel(UUID playerId, SkillId skill, int level) {
        PlayerSkillsProfile profile = getOrCreate(playerId);
        SkillProgress sp = profile.skill(skill);
        sp.setLevel(level);
        if (plugin.xpService() != null && plugin.xpService().formula() != null) {
            sp.setXp(plugin.xpService().formula().totalXpForLevel(level));
        }
        profile.markDirty();
        saveAsync(profile);
    }

    public void setXp(UUID playerId, SkillId skill, long xp) {
        PlayerSkillsProfile profile = getOrCreate(playerId);
        SkillProgress sp = profile.skill(skill);
        sp.setXp(xp);
        if (plugin.xpService() != null && plugin.xpService().formula() != null) {
            sp.setLevel(plugin.xpService().formula().levelForTotalXp(xp));
        }
        profile.markDirty();
        saveAsync(profile);
    }

    public void unload(UUID playerId) {
        if (playerId == null) {
            return;
        }
        PlayerSkillsProfile profile = cache.remove(playerId);
        if (profile != null) {
            saveSync(profile);
        }
    }

    public void saveAllSync() {
        for (PlayerSkillsProfile profile : cache.values()) {
            saveSync(profile);
        }
    }

    public void mergeSkillGreatest(PlayerSkillsProfile profile, SkillId skill, int level, long xp) {
        SkillProgress sp = profile.skill(skill);
        if (level > sp.level()) {
            sp.setLevel(level);
            sp.setXp(Math.max(xp, sp.xp()));
            profile.markDirty();
        } else if (level == sp.level() && xp > sp.xp()) {
            sp.setXp(xp);
            profile.markDirty();
        }
        // never decrease
    }
}
