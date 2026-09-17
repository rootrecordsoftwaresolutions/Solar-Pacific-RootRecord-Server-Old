package com.rootrecord.minecraft.rootskills.storage;

import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import com.rootrecord.minecraft.rootskills.RootSkillsPlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;

/** HikariCP MySQL pool via shared {@code plugins/RootMC/database.yml}. Null = memory-only. */
public final class Database {

    private final RootSkillsPlugin plugin;
    private HikariDataSource dataSource;

    public Database(RootSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean open() {
        RootMcDatabaseConfig.ensureDefaults(plugin);
        FileConfiguration cfg = plugin.getConfig();
        RootMcDatabaseConfig.DatabaseSettings db = RootMcDatabaseConfig.resolve(plugin, cfg);

        if (!db.enabled() || !db.isConfigured()) {
            plugin.getLogger().severe(
                    "MySQL not configured — set plugins/RootMC/database.yml (memory-only fallback).");
            dataSource = null;
            return false;
        }

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(db.jdbcUrl());
        hc.setUsername(db.username());
        hc.setPassword(db.password() == null ? "" : db.password());
        hc.setPoolName("RootSkills-Hikari");
        hc.setMaximumPoolSize(cfg.getInt("mysql.pool.maximum-pool-size", Math.max(2, db.poolSize())));
        hc.setMinimumIdle(cfg.getInt("mysql.pool.minimum-idle", 2));
        hc.setConnectionTimeout(cfg.getLong("mysql.pool.connection-timeout-ms", 10_000L));
        hc.setIdleTimeout(cfg.getLong("mysql.pool.idle-timeout-ms", 600_000L));
        hc.setMaxLifetime(cfg.getLong("mysql.pool.max-lifetime-ms", 1_800_000L));
        hc.setDriverClassName("com.mysql.cj.jdbc.Driver");

        try {
            HikariDataSource ds = new HikariDataSource(hc);
            try (Connection ignored = ds.getConnection()) {
                // verify
            }
            this.dataSource = ds;
            plugin.getLogger().info(
                    "MySQL pool ready: "
                            + db.username()
                            + "@"
                            + db.host()
                            + ":"
                            + db.port()
                            + "/"
                            + db.database());
            return true;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "MySQL unavailable — memory-only fallback: " + ex.getMessage(), ex);
            if (dataSource != null) {
                try {
                    dataSource.close();
                } catch (Exception ignored) {
                }
            }
            dataSource = null;
            return false;
        }
    }

    public boolean available() {
        return dataSource != null && !dataSource.isClosed();
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public Connection connection() throws SQLException {
        if (!available()) {
            throw new SQLException("Root-Skills database not available");
        }
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }
}
