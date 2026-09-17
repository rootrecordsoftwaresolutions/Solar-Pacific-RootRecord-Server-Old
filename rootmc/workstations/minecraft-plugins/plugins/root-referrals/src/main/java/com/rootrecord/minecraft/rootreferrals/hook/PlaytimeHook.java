package com.rootrecord.minecraft.rootreferrals.hook;

import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import com.rootrecord.minecraft.common.mysql.MysqlConnections;
import com.rootrecord.minecraft.rootcore.api.RootCoreApi;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Reads network playtime from Root-Times scoped table via direct SQL. */
public final class PlaytimeHook {

    private static final String SCOPE_GLOBAL = "*";

    private final JavaPlugin plugin;
    private final String playtimeTable;

    public PlaytimeHook(JavaPlugin plugin, String tablePrefix) {
        this.plugin = plugin;
        String prefix = tablePrefix == null || tablePrefix.isBlank() ? "root_" : tablePrefix;
        this.playtimeTable = prefix + "playtime";
    }

    public long totalSeconds(UUID uuid) {
        if (uuid == null) {
            return 0L;
        }
        RootMcDatabaseConfig.DatabaseSettings db = database();
        if (db == null || !db.isConfigured()) {
            return 0L;
        }
        long star = secondsForScope(db, uuid, SCOPE_GLOBAL);
        long sumServers = sumServerScopes(db, uuid);
        if (star < 0 && sumServers <= 0) {
            return 0L;
        }
        return Math.max(star < 0 ? 0L : star, sumServers);
    }

    public int activeDays(UUID uuid) {
        if (uuid == null) {
            return 0;
        }
        RootMcDatabaseConfig.DatabaseSettings db = database();
        if (db == null || !db.isConfigured()) {
            return 0;
        }
        String sql =
                "SELECT first_join_at, last_login_at FROM "
                        + playtimeTable
                        + " WHERE uuid = ? AND scope = ? LIMIT 1";
        try (Connection c = MysqlConnections.open(db); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, SCOPE_GLOBAL);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return 0;
                }
                var first = rs.getTimestamp("first_join_at");
                var last = rs.getTimestamp("last_login_at");
                if (first == null || last == null) {
                    return 0;
                }
                LocalDate start = first.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
                LocalDate end = last.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
                return (int) Math.max(1, ChronoUnit.DAYS.between(start, end) + 1);
            }
        } catch (Exception e) {
            plugin.getLogger().fine("activeDays failed: " + e.getMessage());
            return 0;
        }
    }

    private long secondsForScope(RootMcDatabaseConfig.DatabaseSettings db, UUID uuid, String scope) {
        String sql = "SELECT seconds FROM " + playtimeTable + " WHERE uuid = ? AND scope = ? LIMIT 1";
        try (Connection c = MysqlConnections.open(db); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, scope);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return -1L;
                }
                return rs.getLong(1);
            }
        } catch (Exception ex) {
            if (isMissingScopeColumn(ex)) {
                return legacyTotalSeconds(db, uuid);
            }
            plugin.getLogger().fine("secondsForScope failed: " + ex.getMessage());
            return -1L;
        }
    }

    private long sumServerScopes(RootMcDatabaseConfig.DatabaseSettings db, UUID uuid) {
        String sql = "SELECT COALESCE(SUM(seconds), 0) FROM " + playtimeTable + " WHERE uuid = ? AND scope <> '*'";
        try (Connection c = MysqlConnections.open(db); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (Exception ex) {
            if (isMissingScopeColumn(ex)) {
                long legacy = legacyTotalSeconds(db, uuid);
                return legacy < 0 ? 0L : legacy;
            }
            return 0L;
        }
    }

    private long legacyTotalSeconds(RootMcDatabaseConfig.DatabaseSettings db, UUID uuid) {
        String sql = "SELECT total_playtime_seconds FROM " + playtimeTable + " WHERE uuid = ? LIMIT 1";
        try (Connection c = MysqlConnections.open(db); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return -1L;
                }
                return rs.getLong(1);
            }
        } catch (Exception e) {
            return -1L;
        }
    }

    private static boolean isMissingScopeColumn(Exception ex) {
        String msg = ex.getMessage();
        return msg != null && msg.contains("Unknown column 'scope'");
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
