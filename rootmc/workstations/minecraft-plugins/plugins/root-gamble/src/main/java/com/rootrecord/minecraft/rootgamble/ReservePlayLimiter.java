package com.rootrecord.minecraft.rootgamble;

import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import com.rootrecord.minecraft.common.mysql.MysqlConnections;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Rolling-window cap on vs-Server-Reserve gamble rounds (roulette, hilo, lotto). */
public final class ReservePlayLimiter {

    public record CheckResult(boolean allowed, int used, int max, long retryMs) {}

    private final JavaPlugin plugin;
    private final String table;
    private final boolean storeReady;
    private final ConcurrentHashMap<UUID, List<Long>> memory = new ConcurrentHashMap<>();

    public ReservePlayLimiter(JavaPlugin plugin, GambleStoreReady storeReady, String tablePrefix) {
        this.plugin = plugin;
        this.storeReady = storeReady.ready();
        String prefix = tablePrefix == null || tablePrefix.isBlank() ? "root_" : tablePrefix;
        this.table = prefix + "gamble_reserve_play";
    }

    public interface GambleStoreReady {
        boolean ready();
    }

    public void initSchema() {
        if (!storeReady) {
            return;
        }
        RootMcDatabaseConfig.DatabaseSettings db = database();
        if (db == null || !db.isConfigured()) {
            return;
        }
        try (Connection c = MysqlConnections.open(db);
                var st = c.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      player_uuid CHAR(36) NOT NULL,
                      played_at DATETIME NOT NULL,
                      INDEX idx_reserve_player_time (player_uuid, played_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(table));
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "reserve play schema: " + ex.getMessage());
        }
    }

    public CheckResult check(UUID uuid, GambleConfig config) {
        if (!config.reserveLimitEnabled() || uuid == null) {
            return new CheckResult(true, 0, config.reserveLimitMaxPlays(), 0L);
        }
        int max = config.reserveLimitMaxPlays();
        long windowMs = config.reserveLimitWindowHours() * 3_600_000L;
        long cutoff = System.currentTimeMillis() - windowMs;
        int used = countSince(uuid, cutoff);
        if (used < max) {
            return new CheckResult(true, used, max, 0L);
        }
        long retryMs = retryAfterMs(uuid, cutoff, windowMs);
        return new CheckResult(false, used, max, retryMs);
    }

    public void record(UUID uuid, GambleConfig config) {
        if (!config.reserveLimitEnabled() || uuid == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long cutoff = now - config.reserveLimitWindowHours() * 3_600_000L;
        if (storeReady) {
            RootMcDatabaseConfig.DatabaseSettings db = database();
            if (db != null && db.isConfigured()) {
                try (Connection c = MysqlConnections.open(db)) {
                    try (PreparedStatement del = c.prepareStatement(
                            "DELETE FROM " + table + " WHERE player_uuid = ? AND played_at < ?")) {
                        del.setString(1, uuid.toString());
                        del.setTimestamp(2, Timestamp.from(Instant.ofEpochMilli(cutoff)));
                        del.executeUpdate();
                    }
                    try (PreparedStatement ins = c.prepareStatement(
                            "INSERT INTO " + table + " (player_uuid, played_at) VALUES (?,?)")) {
                        ins.setString(1, uuid.toString());
                        ins.setTimestamp(2, Timestamp.from(Instant.ofEpochMilli(now)));
                        ins.executeUpdate();
                    }
                    return;
                } catch (SQLException ex) {
                    plugin.getLogger().log(Level.WARNING, "reserve play record: " + ex.getMessage());
                }
            }
        }
        memory.compute(uuid, (id, list) -> {
            List<Long> out = list == null ? new ArrayList<>() : new ArrayList<>(list);
            out.removeIf(ts -> ts < cutoff);
            out.add(now);
            return out;
        });
    }

    private int countSince(UUID uuid, long cutoffMs) {
        if (storeReady) {
            RootMcDatabaseConfig.DatabaseSettings db = database();
            if (db != null && db.isConfigured()) {
                try (Connection c = MysqlConnections.open(db);
                        PreparedStatement ps = c.prepareStatement(
                                "SELECT COUNT(*) FROM " + table
                                        + " WHERE player_uuid = ? AND played_at >= ?")) {
                    ps.setString(1, uuid.toString());
                    ps.setTimestamp(2, Timestamp.from(Instant.ofEpochMilli(cutoffMs)));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return rs.getInt(1);
                        }
                    }
                } catch (SQLException ex) {
                    plugin.getLogger().log(Level.WARNING, "reserve play count: " + ex.getMessage());
                }
            }
        }
        List<Long> list = memory.get(uuid);
        if (list == null) {
            return 0;
        }
        int n = 0;
        for (Long ts : list) {
            if (ts != null && ts >= cutoffMs) {
                n++;
            }
        }
        return n;
    }

    private long retryAfterMs(UUID uuid, long cutoffMs, long windowMs) {
        long oldest = Long.MAX_VALUE;
        if (storeReady) {
            RootMcDatabaseConfig.DatabaseSettings db = database();
            if (db != null && db.isConfigured()) {
                try (Connection c = MysqlConnections.open(db);
                        PreparedStatement ps = c.prepareStatement(
                                "SELECT MIN(played_at) FROM " + table
                                        + " WHERE player_uuid = ? AND played_at >= ?")) {
                    ps.setString(1, uuid.toString());
                    ps.setTimestamp(2, Timestamp.from(Instant.ofEpochMilli(cutoffMs)));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            Timestamp ts = rs.getTimestamp(1);
                            if (ts != null) {
                                oldest = ts.getTime();
                            }
                        }
                    }
                } catch (SQLException ex) {
                    plugin.getLogger().log(Level.WARNING, "reserve play retry: " + ex.getMessage());
                }
            }
        } else {
            List<Long> list = memory.get(uuid);
            if (list != null) {
                for (Long ts : list) {
                    if (ts != null && ts >= cutoffMs) {
                        oldest = Math.min(oldest, ts);
                    }
                }
            }
        }
        if (oldest == Long.MAX_VALUE) {
            return windowMs;
        }
        return Math.max(0L, (oldest + windowMs) - System.currentTimeMillis());
    }

    private RootMcDatabaseConfig.DatabaseSettings database() {
        if (plugin instanceof RootGamblePlugin gamble) {
            return RootMcDatabaseConfig.resolve(plugin, gamble.yaml().config());
        }
        return RootMcDatabaseConfig.resolve(plugin, null);
    }
}
