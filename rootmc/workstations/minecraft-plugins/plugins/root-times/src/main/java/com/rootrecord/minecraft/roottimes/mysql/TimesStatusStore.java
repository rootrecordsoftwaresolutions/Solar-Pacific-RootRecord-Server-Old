package com.rootrecord.minecraft.roottimes.mysql;

import com.rootrecord.minecraft.roottimes.config.TimesConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

/** Live Times status row for Hyperdrive sync (one row per host MySQL database). */
public final class TimesStatusStore {

    private TimesStatusStore() {}

    public static String tableName(TimesConfig config) {
        String configured = config.timesStatusTable();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return config.tablePrefix() + "times_status";
    }

    public static void initSchema(Connection c, TimesConfig config) throws SQLException {
        String table = tableName(config);
        try (PreparedStatement ps = c.prepareStatement(
                """
                CREATE TABLE IF NOT EXISTS %s (
                  id TINYINT NOT NULL PRIMARY KEY DEFAULT 1,
                  day_id BIGINT NOT NULL DEFAULT 0,
                  tod_ticks INT NOT NULL DEFAULT 0,
                  full_time BIGINT NOT NULL DEFAULT 0,
                  phase VARCHAR(32) NOT NULL DEFAULT '',
                  length_minutes INT NOT NULL DEFAULT 30,
                  online INT NOT NULL DEFAULT 0,
                  afk INT NOT NULL DEFAULT 0,
                  players_json MEDIUMTEXT NOT NULL,
                  plugins_json MEDIUMTEXT NOT NULL,
                  timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
                  updated_at DATETIME(3) NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """
                        .formatted(table))) {
            ps.executeUpdate();
        }
    }

    public static void upsert(
            Connection c,
            TimesConfig config,
            long dayId,
            int todTicks,
            long fullTime,
            String phase,
            int lengthMinutes,
            int online,
            int afk,
            String playersJson,
            String pluginsJson,
            String timezone)
            throws SQLException {
        String table = tableName(config);
        Timestamp now = Timestamp.from(Instant.now());
        try (PreparedStatement ps = c.prepareStatement(
                """
                INSERT INTO %s (
                  id, day_id, tod_ticks, full_time, phase, length_minutes,
                  online, afk, players_json, plugins_json, timezone, updated_at
                ) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  day_id = VALUES(day_id),
                  tod_ticks = VALUES(tod_ticks),
                  full_time = VALUES(full_time),
                  phase = VALUES(phase),
                  length_minutes = VALUES(length_minutes),
                  online = VALUES(online),
                  afk = VALUES(afk),
                  players_json = VALUES(players_json),
                  plugins_json = VALUES(plugins_json),
                  timezone = VALUES(timezone),
                  updated_at = VALUES(updated_at)
                """
                        .formatted(table))) {
            ps.setLong(1, dayId);
            ps.setInt(2, todTicks);
            ps.setLong(3, fullTime);
            ps.setString(4, phase == null || phase.isBlank() ? "—" : phase);
            ps.setInt(5, Math.max(1, lengthMinutes));
            ps.setInt(6, Math.max(0, online));
            ps.setInt(7, Math.max(0, afk));
            ps.setString(8, playersJson != null ? playersJson : "[]");
            ps.setString(9, pluginsJson != null ? pluginsJson : "[]");
            ps.setString(10, timezone == null || timezone.isBlank() ? "UTC" : timezone);
            ps.setTimestamp(11, now);
            ps.executeUpdate();
        }
    }
}
