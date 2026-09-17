package com.rootrecord.minecraft.rootskills.storage;

import com.rootrecord.minecraft.rootskills.RootSkillsPlugin;

import java.sql.Connection;
import java.sql.Statement;
import java.util.logging.Level;

public final class SchemaManager {

    private final RootSkillsPlugin plugin;
    private final String prefix;

    public SchemaManager(RootSkillsPlugin plugin, String tablePrefix) {
        this.plugin = plugin;
        this.prefix = tablePrefix == null || tablePrefix.isBlank() ? "root_skills_" : tablePrefix;
    }

    public String prefix() {
        return prefix;
    }

    public String table(String name) {
        return prefix + name;
    }

    public void init(Database database) {
        if (database == null || !database.available()) {
            plugin.getLogger().warning("SchemaManager: skipped (no database)");
            return;
        }
        try (Connection c = database.connection(); Statement st = c.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      uuid CHAR(36) NOT NULL,
                      username VARCHAR(16) NULL,
                      class_id VARCHAR(64) NULL,
                      mana DOUBLE NOT NULL DEFAULT 0,
                      prefs_json TEXT NULL,
                      migrated_from VARCHAR(64) NULL,
                      migrated_at BIGINT NULL,
                      updated_at BIGINT NOT NULL,
                      PRIMARY KEY (uuid)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(table("users")));

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      uuid CHAR(36) NOT NULL,
                      skill_id VARCHAR(32) NOT NULL,
                      level INT NOT NULL DEFAULT 0,
                      xp BIGINT NOT NULL DEFAULT 0,
                      prestige INT NOT NULL DEFAULT 0,
                      buffs_json TEXT NULL,
                      PRIMARY KEY (uuid, skill_id),
                      INDEX idx_skill_level (skill_id, level)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(table("skills")));

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      uuid CHAR(36) NOT NULL,
                      talent_id VARCHAR(64) NOT NULL,
                      unlocked TINYINT(1) NOT NULL DEFAULT 0,
                      equipped TINYINT(1) NOT NULL DEFAULT 0,
                      slot INT NOT NULL DEFAULT -1,
                      PRIMARY KEY (uuid, talent_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(table("talents")));

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      uuid CHAR(36) NOT NULL,
                      booster_id VARCHAR(64) NOT NULL,
                      skill_id VARCHAR(32) NULL,
                      multiplier DOUBLE NOT NULL DEFAULT 1.0,
                      expires_at BIGINT NOT NULL,
                      PRIMARY KEY (uuid, booster_id),
                      INDEX idx_booster_exp (expires_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(table("boosters")));

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      party_id CHAR(36) NOT NULL,
                      leader_uuid CHAR(36) NOT NULL,
                      name VARCHAR(64) NULL,
                      xp_share DOUBLE NOT NULL DEFAULT 0.25,
                      PRIMARY KEY (party_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(table("parties")));

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      party_id CHAR(36) NOT NULL,
                      uuid CHAR(36) NOT NULL,
                      PRIMARY KEY (party_id, uuid),
                      UNIQUE KEY uk_member (uuid)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(table("party_members")));

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      run_id CHAR(36) NOT NULL,
                      started_at BIGINT NOT NULL,
                      finished_at BIGINT NULL,
                      rows_seen INT NOT NULL DEFAULT 0,
                      rows_written INT NOT NULL DEFAULT 0,
                      conflicts INT NOT NULL DEFAULT 0,
                      dry_run TINYINT(1) NOT NULL DEFAULT 0,
                      checksum VARCHAR(128) NULL,
                      detail TEXT NULL,
                      PRIMARY KEY (run_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(table("migration_log")));

            plugin.getLogger().info("Schema ready (prefix=" + prefix + ")");
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Schema init failed: " + ex.getMessage(), ex);
        }
    }
}
