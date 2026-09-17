package com.rootrecord.minecraft.roottimes.mysql;

import com.rootrecord.minecraft.roottimes.config.TimesConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

public final class AfkSessionStore {

    private AfkSessionStore() {}

    public static void initSchema(Connection c, TimesConfig config) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                """
                CREATE TABLE IF NOT EXISTS %s (
                  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                  uuid CHAR(36) NOT NULL,
                  started_at DATETIME NOT NULL,
                  ended_at DATETIME NULL,
                  seconds BIGINT NOT NULL DEFAULT 0,
                  INDEX idx_afk_uuid_started (uuid, started_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """
                        .formatted(config.afkSessionsTable()))) {
            ps.executeUpdate();
        }
    }

    public static void recordSession(Connection c, TimesConfig config, UUID uuid, Instant start, Instant end)
            throws SQLException {
        long seconds = Math.max(0L, end.getEpochSecond() - start.getEpochSecond());
        if (seconds <= 0) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement(
                """
                INSERT INTO %s (uuid, started_at, ended_at, seconds)
                VALUES (?, ?, ?, ?)
                """
                        .formatted(config.afkSessionsTable()))) {
            ps.setString(1, uuid.toString());
            ps.setTimestamp(2, Timestamp.from(start));
            ps.setTimestamp(3, Timestamp.from(end));
            ps.setLong(4, seconds);
            ps.executeUpdate();
        }
    }

    public static long secondsToday(Connection c, TimesConfig config, UUID uuid) throws SQLException {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant start = today.atStartOfDay().toInstant(ZoneOffset.UTC);
        try (PreparedStatement ps = c.prepareStatement(
                """
                SELECT COALESCE(SUM(seconds), 0) FROM %s
                WHERE uuid = ? AND started_at >= ?
                """
                        .formatted(config.afkSessionsTable()))) {
            ps.setString(1, uuid.toString());
            ps.setTimestamp(2, Timestamp.from(start));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }
}
