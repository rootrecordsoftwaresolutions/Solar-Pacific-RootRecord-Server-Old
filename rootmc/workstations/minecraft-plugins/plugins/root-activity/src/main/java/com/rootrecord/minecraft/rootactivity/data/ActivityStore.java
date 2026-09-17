package com.rootrecord.minecraft.rootactivity.data;

import com.rootrecord.minecraft.rootactivity.config.ActivityConfig;
import com.rootrecord.minecraft.rootactivity.timezone.TimezoneDef;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ActivityStore {

    public enum TimezoneSource {
        IP("ip"),
        DISCORD("discord");

        private final String dbValue;

        TimezoneSource(String dbValue) {
            this.dbValue = dbValue;
        }

        public String dbValue() {
            return dbValue;
        }

        public static Optional<TimezoneSource> parse(String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            String v = raw.trim().toLowerCase();
            for (TimezoneSource s : values()) {
                if (s.dbValue.equals(v)) {
                    return Optional.of(s);
                }
            }
            return Optional.empty();
        }
    }

    public record PlayerTimezone(UUID uuid, String timezoneKey, TimezoneSource source, String lastIp) {}

    private final ActivityConfig config;

    public ActivityStore(ActivityConfig config) {
        this.config = config;
    }

    public void initSchema() throws SQLException {
        if (!config.mysqlConfigured()) {
            return;
        }
        try (Connection c = open(); Statement st = c.createStatement()) {
            st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS %s (
                      minecraft_uuid CHAR(36) PRIMARY KEY,
                      timezone_key VARCHAR(32) NOT NULL,
                      source VARCHAR(16) NOT NULL,
                      last_ip VARCHAR(45) NULL,
                      updated_at DATETIME NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """
                            .formatted(config.timezoneTable()));
            st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS %s (
                      minecraft_uuid CHAR(36) NOT NULL,
                      local_hour TINYINT NOT NULL,
                      play_seconds BIGINT NOT NULL DEFAULT 0,
                      PRIMARY KEY (minecraft_uuid, local_hour)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """
                            .formatted(config.hourlyTable()));
        }
    }

    public Optional<PlayerTimezone> getTimezone(UUID uuid) throws SQLException {
        if (!config.mysqlConfigured()) {
            return Optional.empty();
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT timezone_key, source, last_ip FROM "
                                + config.timezoneTable()
                                + " WHERE minecraft_uuid = ? LIMIT 1")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                TimezoneSource source = TimezoneSource.parse(rs.getString("source")).orElse(TimezoneSource.IP);
                return Optional.of(new PlayerTimezone(
                        uuid,
                        rs.getString("timezone_key"),
                        source,
                        rs.getString("last_ip")));
            }
        }
    }

    public boolean upsertTimezone(UUID uuid, String timezoneKey, TimezoneSource source, String lastIp)
            throws SQLException {
        if (!config.mysqlConfigured()) {
            return false;
        }
        Optional<PlayerTimezone> existing = getTimezone(uuid);
        if (existing.isPresent() && existing.get().source() == TimezoneSource.DISCORD && source == TimezoneSource.IP) {
            return false;
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        """
                        INSERT INTO %s (minecraft_uuid, timezone_key, source, last_ip, updated_at)
                        VALUES (?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                          timezone_key = VALUES(timezone_key),
                          source = VALUES(source),
                          last_ip = VALUES(last_ip),
                          updated_at = VALUES(updated_at)
                        """
                                .formatted(config.timezoneTable()))) {
            ps.setString(1, uuid.toString());
            ps.setString(2, timezoneKey);
            ps.setString(3, source.dbValue());
            ps.setString(4, lastIp);
            ps.setTimestamp(5, Timestamp.from(Instant.now()));
            ps.executeUpdate();
            return true;
        }
    }

    public void addPlaySeconds(UUID uuid, int localHour, long seconds) throws SQLException {
        if (!config.mysqlConfigured() || seconds <= 0) {
            return;
        }
        int hour = ((localHour % 24) + 24) % 24;
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        """
                        INSERT INTO %s (minecraft_uuid, local_hour, play_seconds)
                        VALUES (?, ?, ?)
                        ON DUPLICATE KEY UPDATE play_seconds = play_seconds + VALUES(play_seconds)
                        """
                                .formatted(config.hourlyTable()))) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, hour);
            ps.setLong(3, seconds);
            ps.executeUpdate();
        }
    }

    public int[] loadHourlyBuckets(UUID uuid) throws SQLException {
        int[] buckets = TimezoneDef.emptyHourBuckets();
        if (!config.mysqlConfigured()) {
            return buckets;
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT local_hour, play_seconds FROM "
                                + config.hourlyTable()
                                + " WHERE minecraft_uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int h = rs.getInt("local_hour");
                    if (h >= 0 && h < 24) {
                        buckets[h] = (int) Math.min(Integer.MAX_VALUE, rs.getLong("play_seconds"));
                    }
                }
            }
        }
        return buckets;
    }

    public Set<String> registeredTimezoneKeys() throws SQLException {
        Set<String> keys = new HashSet<>();
        if (!config.mysqlConfigured()) {
            return keys;
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT DISTINCT timezone_key FROM " + config.timezoneTable());
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                keys.add(rs.getString("timezone_key"));
            }
        }
        return keys;
    }

    public Map<String, Integer> countPlayersByTimezone() throws SQLException {
        Map<String, Integer> out = new HashMap<>();
        if (!config.mysqlConfigured()) {
            return out;
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT timezone_key, COUNT(*) AS n FROM "
                                + config.timezoneTable()
                                + " GROUP BY timezone_key");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getString("timezone_key"), rs.getInt("n"));
            }
        }
        return out;
    }

    public Map<String, int[]> realmBucketsByTimezone() throws SQLException {
        Map<String, int[]> out = new HashMap<>();
        if (!config.mysqlConfigured()) {
            return out;
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        """
                        SELECT t.timezone_key, h.local_hour, SUM(h.play_seconds) AS secs
                        FROM %s h
                        INNER JOIN %s t ON t.minecraft_uuid = h.minecraft_uuid
                        GROUP BY t.timezone_key, h.local_hour
                        """
                                .formatted(config.hourlyTable(), config.timezoneTable()));
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String key = rs.getString("timezone_key");
                int hour = rs.getInt("local_hour");
                long secs = rs.getLong("secs");
                int[] buckets = out.computeIfAbsent(key, k -> TimezoneDef.emptyHourBuckets());
                if (hour >= 0 && hour < 24) {
                    buckets[hour] = (int) Math.min(Integer.MAX_VALUE, secs);
                }
            }
        }
        return out;
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(
                config.jdbcUrl(), config.mysqlUsername(), config.mysqlPassword());
    }
}
