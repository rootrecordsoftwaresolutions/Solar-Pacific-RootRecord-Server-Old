package com.rootrecord.minecraft.rootchamber;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/** Optional async inserts into RootMC MySQL (reads plugins/RootMC/database.yml). */
final class ChamberMysqlStore {

    private final JavaPlugin plugin;
    private final AtomicBoolean schemaReady = new AtomicBoolean(false);
    private volatile boolean enabled;
    private volatile String jdbcUrl;
    private volatile String username;
    private volatile String password;
    private volatile String tableName;

    ChamberMysqlStore(JavaPlugin plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    void initAsync() {
        if (!enabled) {
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ensureSchema();
                schemaReady.set(true);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Chamber MySQL schema init failed: " + ex.getMessage());
            }
        });
    }

    void reloadConfig() {
        RootMcDatabaseConfig.DatabaseSettings db = RootMcDatabaseConfig.resolve(plugin, null);
        enabled = db.enabled() && db.isConfigured();
        if (!enabled) {
            return;
        }
        File spawnCfg = RootRecordFolders.configFile(plugin, RootRecordFolders.ROOT_SPAWN_CONFIG);
        if (spawnCfg.isFile()) {
            YamlConfiguration spawnYaml = YamlConfiguration.loadConfiguration(spawnCfg);
            enabled = spawnYaml.getBoolean("chamber.stats.mysql_enabled", true);
        }
        if (!enabled) {
            return;
        }
        username = db.username();
        password = db.password();
        tableName = db.tablePrefix() + "chamber_runs";
        jdbcUrl = db.jdbcUrl();
    }

    void insert(ChamberRunRecorder.RunRecord record) {
        if (!enabled) {
            return;
        }
        try {
            if (!schemaReady.get()) {
                ensureSchema();
                schemaReady.set(true);
            }
            try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
                    PreparedStatement ps = conn.prepareStatement(
                            """
                            INSERT INTO %s (
                              player_uuid, player_name, outcome, duration_ms,
                              lava_total, lava_ceilings_opened, lava_floors_opened,
                              prize_gold, prize_paid, recorded_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """.formatted(tableName))) {
                ps.setString(1, record.playerId().toString());
                ps.setString(2, record.playerName());
                ps.setString(3, record.outcome().name());
                ps.setLong(4, record.durationMs());
                ps.setInt(5, record.lavaTotal());
                ps.setInt(6, record.lavaCeilingsOpened());
                ps.setInt(7, record.lavaFloorsOpened());
                ps.setDouble(8, record.prizeGold());
                ps.setBoolean(9, record.prizePaid());
                ps.setTimestamp(10, Timestamp.from(Instant.now()));
                ps.executeUpdate();
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.FINE, "Chamber MySQL insert failed: " + ex.getMessage());
        }
    }

    private void ensureSchema() throws SQLException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
                PreparedStatement ps = conn.prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS %s (
                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                          player_uuid CHAR(36) NOT NULL,
                          player_name VARCHAR(16) NOT NULL,
                          outcome VARCHAR(16) NOT NULL,
                          duration_ms BIGINT NOT NULL,
                          lava_total INT NOT NULL DEFAULT 0,
                          lava_ceilings_opened INT NOT NULL DEFAULT 0,
                          lava_floors_opened INT NOT NULL DEFAULT 0,
                          prize_gold DOUBLE NOT NULL DEFAULT 0,
                          prize_paid TINYINT(1) NOT NULL DEFAULT 0,
                          recorded_at DATETIME NOT NULL,
                          INDEX idx_chamber_player (player_uuid),
                          INDEX idx_chamber_outcome (outcome),
                          INDEX idx_chamber_recorded (recorded_at)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """.formatted(tableName))) {
            ps.executeUpdate();
        }
    }
}
