package com.rootrecord.minecraft.roottimes.mysql;

import com.rootrecord.minecraft.roottimes.config.TimesConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ActivityHarvestStore {

    private ActivityHarvestStore() {}

    public static void initSchema(Connection c, TimesConfig config) throws SQLException {
        try (PreparedStatement tz = c.prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS %s (
                          minecraft_uuid CHAR(36) PRIMARY KEY,
                          timezone_key VARCHAR(32) NOT NULL,
                          source VARCHAR(16) NOT NULL,
                          last_ip VARCHAR(45) NULL,
                          updated_at DATETIME NOT NULL
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """
                                .formatted(config.activityTimezoneTable()));
                PreparedStatement hourly = c.prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS %s (
                          minecraft_uuid CHAR(36) NOT NULL,
                          local_hour TINYINT NOT NULL,
                          play_seconds BIGINT NOT NULL DEFAULT 0,
                          PRIMARY KEY (minecraft_uuid, local_hour)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """
                                .formatted(config.activityHourlyTable()))) {
            tz.executeUpdate();
            hourly.executeUpdate();
        }
    }

    public static Optional<String> getTimezoneKey(Connection c, TimesConfig config, UUID uuid)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT timezone_key FROM "
                        + config.activityTimezoneTable()
                        + " WHERE minecraft_uuid = ? LIMIT 1")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(rs.getString(1));
            }
        }
    }

    public static void ensureTimezone(
            Connection c, TimesConfig config, UUID uuid, String timezoneKey, String lastIp)
            throws SQLException {
        ensureTimezone(c, config, uuid, timezoneKey, "times", lastIp);
    }

    public static void ensureTimezone(
            Connection c,
            TimesConfig config,
            UUID uuid,
            String timezoneKey,
            String source,
            String lastIp)
            throws SQLException {
        Optional<String> existing = getTimezoneKey(c, config, uuid);
        if (existing.isPresent()) {
            return;
        }
        String src = source == null || source.isBlank() ? "times" : source.trim();
        try (PreparedStatement ps = c.prepareStatement(
                """
                INSERT INTO %s (minecraft_uuid, timezone_key, source, last_ip, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at)
                """
                        .formatted(config.activityTimezoneTable()))) {
            ps.setString(1, uuid.toString());
            ps.setString(2, timezoneKey);
            ps.setString(3, src);
            ps.setString(4, lastIp);
            ps.setTimestamp(5, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
    }

    public static void addPlaySeconds(Connection c, TimesConfig config, UUID uuid, int localHour, long seconds)
            throws SQLException {
        if (seconds <= 0) {
            return;
        }
        int hour = ((localHour % 24) + 24) % 24;
        try (PreparedStatement ps = c.prepareStatement(
                """
                INSERT INTO %s (minecraft_uuid, local_hour, play_seconds)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE play_seconds = play_seconds + VALUES(play_seconds)
                """
                        .formatted(config.activityHourlyTable()))) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, hour);
            ps.setLong(3, seconds);
            ps.executeUpdate();
        }
    }

    public static Map<String, long[]> loadTimezoneHourTotals(Connection c, TimesConfig config)
            throws SQLException {
        Map<String, long[]> out = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement(
                """
                SELECT t.timezone_key, h.local_hour, SUM(h.play_seconds) AS secs
                FROM %s h
                JOIN %s t ON t.minecraft_uuid = h.minecraft_uuid
                GROUP BY t.timezone_key, h.local_hour
                """
                        .formatted(config.activityHourlyTable(), config.activityTimezoneTable()));
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String key = rs.getString(1);
                int hour = rs.getInt(2);
                long secs = rs.getLong(3);
                long[] buckets = out.computeIfAbsent(key, k -> new long[24]);
                if (hour >= 0 && hour < 24) {
                    buckets[hour] = secs;
                }
            }
        }
        return out;
    }
}
