package com.rootrecord.minecraft.roottry;

import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import com.rootrecord.minecraft.common.mysql.MysqlConnections;
import com.rootrecord.minecraft.rootcore.api.RootCoreApi;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Forever completions: root_try_completions. */
public final class TryCompletionStore {

    private final JavaPlugin plugin;
    private final String table;
    private volatile boolean ready;

    public TryCompletionStore(JavaPlugin plugin, String tablePrefix) {
        this.plugin = plugin;
        this.table = (tablePrefix == null || tablePrefix.isBlank() ? "root_" : tablePrefix) + "try_completions";
    }

    public void initSchema() {
        RootMcDatabaseConfig.DatabaseSettings db = database();
        if (db == null || !db.isConfigured()) {
            plugin.getLogger().warning("Root-Try: MySQL not configured — completions will not persist.");
            ready = false;
            return;
        }
        try (Connection c = MysqlConnections.open(db); Statement st = c.createStatement()) {
            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS "
                            + table
                            + " ("
                            + "minecraft_uuid CHAR(36) NOT NULL,"
                            + "try_id VARCHAR(64) NOT NULL,"
                            + "completed_at DATETIME NOT NULL,"
                            + "server_id VARCHAR(64) NULL,"
                            + "reward_g DOUBLE NOT NULL DEFAULT 1,"
                            + "PRIMARY KEY (minecraft_uuid, try_id)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            ready = true;
            plugin.getLogger().info("Root-Try completions table ready: " + table);
        } catch (Exception e) {
            ready = false;
            plugin.getLogger().warning("Root-Try schema failed: " + e.getMessage());
        }
    }

    public Set<String> completedIds(UUID uuid) {
        Set<String> out = new HashSet<>();
        if (!ready || uuid == null) {
            return out;
        }
        RootMcDatabaseConfig.DatabaseSettings db = database();
        if (db == null) {
            return out;
        }
        String sql = "SELECT try_id FROM " + table + " WHERE minecraft_uuid = ?";
        try (Connection c = MysqlConnections.open(db);
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Root-Try load completions failed: " + e.getMessage());
        }
        return out;
    }

    /** @return true if newly inserted */
    public boolean markComplete(UUID uuid, String tryId, double rewardG, String serverId) {
        if (!ready || uuid == null || tryId == null || tryId.isBlank()) {
            return false;
        }
        RootMcDatabaseConfig.DatabaseSettings db = database();
        if (db == null) {
            return false;
        }
        String sql =
                "INSERT IGNORE INTO "
                        + table
                        + " (minecraft_uuid, try_id, completed_at, server_id, reward_g) VALUES (?, ?, UTC_TIMESTAMP(), ?, ?)";
        try (Connection c = MysqlConnections.open(db); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, tryId);
            ps.setString(3, serverId);
            ps.setDouble(4, rewardG);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            plugin.getLogger().warning("Root-Try markComplete failed: " + e.getMessage());
            return false;
        }
    }

    private RootMcDatabaseConfig.DatabaseSettings database() {
        RegisteredServiceProvider<RootCoreApi> rsp =
                Bukkit.getServicesManager().getRegistration(RootCoreApi.class);
        if (rsp == null || rsp.getProvider() == null) {
            return null;
        }
        return rsp.getProvider().databaseSettings();
    }
}
