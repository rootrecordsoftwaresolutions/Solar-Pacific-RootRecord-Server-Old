package com.rootrecord.minecraft.rootping;

import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import com.rootrecord.minecraft.common.mysql.MysqlConnections;
import com.rootrecord.minecraft.rootcore.api.RootCoreApi;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

/** Append-only samples: root_ping_samples. */
public final class PingSampleStore {

    private final JavaPlugin plugin;
    private final String table;
    private volatile boolean ready;

    public PingSampleStore(JavaPlugin plugin, String tablePrefix) {
        this.plugin = plugin;
        this.table = (tablePrefix == null || tablePrefix.isBlank() ? "root_" : tablePrefix) + "ping_samples";
    }

    public void initSchema() {
        RootMcDatabaseConfig.DatabaseSettings db = database();
        if (db == null || !db.isConfigured()) {
            plugin.getLogger().warning("Root-Ping: MySQL not configured — samples will not persist.");
            ready = false;
            return;
        }
        try (Connection c = MysqlConnections.open(db); Statement st = c.createStatement()) {
            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS "
                            + table
                            + " ("
                            + "id BIGINT NOT NULL AUTO_INCREMENT,"
                            + "sampled_at DATETIME NOT NULL,"
                            + "trigger_type VARCHAR(16) NOT NULL,"
                            + "server_id VARCHAR(64) NULL,"
                            + "host_label VARCHAR(32) NULL,"
                            + "minecraft_uuid CHAR(36) NOT NULL,"
                            + "username VARCHAR(16) NOT NULL,"
                            + "ping_ms INT NOT NULL,"
                            + "ip VARCHAR(64) NULL,"
                            + "remote_port INT NULL,"
                            + "protocol_version INT NULL,"
                            + "client_brand VARCHAR(64) NULL,"
                            + "locale VARCHAR(16) NULL,"
                            + "client_view_distance INT NULL,"
                            + "virtual_host VARCHAR(255) NULL,"
                            + "tps_1m DOUBLE NULL,"
                            + "tps_5m DOUBLE NULL,"
                            + "tps_15m DOUBLE NULL,"
                            + "mspt_ms DOUBLE NULL,"
                            + "online_players INT NULL,"
                            + "max_players INT NULL,"
                            + "heap_used_mb BIGINT NULL,"
                            + "heap_max_mb BIGINT NULL,"
                            + "heap_pct DOUBLE NULL,"
                            + "process_cpu_pct DOUBLE NULL,"
                            + "disk_used_pct DOUBLE NULL,"
                            + "PRIMARY KEY (id),"
                            + "KEY idx_ping_uuid_time (minecraft_uuid, sampled_at),"
                            + "KEY idx_ping_sampled (sampled_at)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            ready = true;
            plugin.getLogger().info("Root-Ping samples table ready: " + table);
        } catch (Exception e) {
            ready = false;
            plugin.getLogger().warning("Root-Ping schema failed: " + e.getMessage());
        }
    }

    public boolean ready() {
        return ready;
    }

    public void insert(ConnectionSample sample) {
        if (!ready || sample == null || sample.uuid() == null) {
            return;
        }
        RootMcDatabaseConfig.DatabaseSettings db = database();
        if (db == null) {
            return;
        }
        String sql =
                "INSERT INTO "
                        + table
                        + " (sampled_at, trigger_type, server_id, host_label, minecraft_uuid, username,"
                        + " ping_ms, ip, remote_port, protocol_version, client_brand, locale,"
                        + " client_view_distance, virtual_host, tps_1m, tps_5m, tps_15m, mspt_ms,"
                        + " online_players, max_players, heap_used_mb, heap_max_mb, heap_pct,"
                        + " process_cpu_pct, disk_used_pct)"
                        + " VALUES (UTC_TIMESTAMP(),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = MysqlConnections.open(db); PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, sample.trigger());
            ps.setString(i++, blankToNull(sample.serverId()));
            ps.setString(i++, blankToNull(sample.hostLabel()));
            ps.setString(i++, sample.uuid().toString());
            ps.setString(i++, sample.username());
            ps.setInt(i++, sample.pingMs());
            ps.setString(i++, blankToNull(sample.ip()));
            ps.setInt(i++, sample.remotePort());
            ps.setInt(i++, sample.protocolVersion());
            ps.setString(i++, blankToNull(sample.clientBrand()));
            ps.setString(i++, blankToNull(sample.locale()));
            ps.setInt(i++, sample.clientViewDistance());
            ps.setString(i++, blankToNull(sample.virtualHost()));
            ps.setDouble(i++, sample.tps1m());
            ps.setDouble(i++, sample.tps5m());
            ps.setDouble(i++, sample.tps15m());
            ps.setDouble(i++, sample.msptMs());
            ps.setInt(i++, sample.onlinePlayers());
            ps.setInt(i++, sample.maxPlayers());
            ps.setLong(i++, sample.heapUsedMb());
            ps.setLong(i++, sample.heapMaxMb());
            ps.setDouble(i++, sample.heapPct());
            ps.setDouble(i++, sample.processCpuPct());
            ps.setDouble(i, sample.diskUsedPct());
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("Root-Ping insert failed: " + e.getMessage());
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
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
