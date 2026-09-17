package com.rootrecord.minecraft.common.mysql;

import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Properties;

/** Shared JDBC open with short retries — Shockbyte can deny bursts of concurrent auth. */
public final class MysqlConnections {

    private MysqlConnections() {}

    public static Connection open(RootMcDatabaseConfig.DatabaseSettings settings) throws SQLException {
        return open(settings.jdbcUrl(), settings.username(), settings.password());
    }

    public static Connection open(String jdbcUrl, String username, String password) throws SQLException {
        ensureDriver();
        SQLException last = null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            try {
                Properties props = new Properties();
                props.setProperty("user", username == null ? "" : username);
                props.setProperty("password", password == null ? "" : password);
                props.setProperty("allowPublicKeyRetrieval", "true");
                props.setProperty("useSSL", "false");
                props.setProperty("serverTimezone", "UTC");
                return DriverManager.getConnection(jdbcUrl, props);
            } catch (SQLException ex) {
                last = ex;
                if (!isRetryable(ex) || attempt == 4) {
                    throw ex;
                }
                try {
                    Thread.sleep(750L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
        throw last != null ? last : new SQLException("MySQL connect failed");
    }

    private static void ensureDriver() {
        try {
            Plugin rootMc = Bukkit.getPluginManager().getPlugin("RootMC");
            Plugin rootTimes = Bukkit.getPluginManager().getPlugin("Root-Times");
            ClassLoader loader = rootMc != null
                    ? rootMc.getClass().getClassLoader()
                    : rootTimes != null
                            ? rootTimes.getClass().getClassLoader()
                            : MysqlConnections.class.getClassLoader();
            Class.forName("com.mysql.cj.jdbc.Driver", true, loader);
        } catch (Throwable ignored) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (Throwable ignored2) {
                // DriverManager may still resolve a registered driver.
            }
        }
    }

    private static boolean isRetryable(SQLException ex) {
        String msg = String.valueOf(ex.getMessage()).toLowerCase(Locale.ROOT);
        return msg.contains("access denied")
                || msg.contains("too many connections")
                || msg.contains("communications link failure")
                || msg.contains("connection is not available")
                || ex.getErrorCode() == 1040
                || ex.getErrorCode() == 1045;
    }
}
