package com.rootrecord.minecraft.roottimes.mysql;

import com.rootrecord.minecraft.common.McDayClock;
import com.rootrecord.minecraft.roottimes.config.TimesConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Playtime storage.
 *
 * <p>{@code root_playtime} — one row per (player, scope). Scope {@code *} is the network total
 * (= sum of server scopes). Server scopes are canonical {@code towny} / {@code claims}.
 *
 * <p>Session flushes increment only the local server scope, then recompute {@code *}.
 * Legacy unscoped rows and historical totals are attributed to {@code towny}.
 */
public final class PlaytimeStore {

    public static final String SCOPE_GLOBAL = "*";
    public static final String SCOPE_TOWNY = "towny";
    public static final String SCOPE_CLAIMS = "claims";

    private PlaytimeStore() {}

    public static void initSchema(Connection c, TimesConfig config) throws SQLException {
        migrateLegacyUnscopedTable(c, config.playtimeTable(), false);
        migrateLegacyUnscopedTable(c, config.playtimeMonthlyTable(), true);

        String table = config.playtimeTable();
        String monthly = config.playtimeMonthlyTable();
        try (PreparedStatement ps = c.prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS %s (
                          uuid CHAR(36) NOT NULL,
                          scope VARCHAR(64) NOT NULL,
                          username VARCHAR(16) NOT NULL,
                          seconds BIGINT NOT NULL DEFAULT 0,
                          first_join_at DATETIME NOT NULL,
                          last_login_at DATETIME NOT NULL,
                          updated_at DATETIME NOT NULL,
                          PRIMARY KEY (uuid, scope),
                          INDEX idx_playtime_scope (scope, seconds),
                          INDEX idx_playtime_username (username)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """
                                .formatted(table));
                PreparedStatement monthlyPs = c.prepareStatement(
                        """
                        CREATE TABLE IF NOT EXISTS %s (
                          uuid CHAR(36) NOT NULL,
                          scope VARCHAR(64) NOT NULL,
                          month_key CHAR(7) NOT NULL,
                          seconds BIGINT NOT NULL DEFAULT 0,
                          updated_at DATETIME NOT NULL,
                          PRIMARY KEY (uuid, scope, month_key),
                          INDEX idx_playtime_month (scope, month_key, seconds)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """
                                .formatted(monthly))) {
            ps.executeUpdate();
            monthlyPs.executeUpdate();
        }
        migrateLegacyIfPresent(c, config);
        normalizeKnownScopes(c, table);
        normalizeKnownScopesMonthly(c, monthly);
        foldStarOnlyIntoTowny(c, table);
        foldStarOnlyIntoTownyMonthly(c, monthly);
        recomputeAllStarPlaytime(c, table);
        recomputeAllStarPlaytimeMonthly(c, monthly);
    }

    /**
     * Convert legacy one-row-per-player tables (no {@code scope}) into scoped tables.
     * All prior totals become {@code towny}.
     */
    private static void migrateLegacyUnscopedTable(Connection c, String table, boolean monthly)
            throws SQLException {
        if (!tableExists(c, table) || columnExists(c, table, "scope")) {
            return;
        }
        boolean hasLegacySeconds = monthly
                ? columnExists(c, table, "playtime_seconds")
                : columnExists(c, table, "total_playtime_seconds");
        if (!hasLegacySeconds) {
            return;
        }
        String bak = table + "_legacy_pre_scope";
        if (tableExists(c, bak)) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("DROP TABLE `" + bak + "`");
            }
        }
        try (Statement st = c.createStatement()) {
            st.executeUpdate("RENAME TABLE `" + table + "` TO `" + bak + "`");
        }
        if (monthly) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate(
                        """
                        CREATE TABLE `%s` (
                          uuid CHAR(36) NOT NULL,
                          scope VARCHAR(64) NOT NULL,
                          month_key CHAR(7) NOT NULL,
                          seconds BIGINT NOT NULL DEFAULT 0,
                          updated_at DATETIME NOT NULL,
                          PRIMARY KEY (uuid, scope, month_key),
                          INDEX idx_playtime_month (scope, month_key, seconds)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """
                                .formatted(table));
            }
            String secondsCol = columnExists(c, bak, "playtime_seconds") ? "playtime_seconds" : "seconds";
            try (PreparedStatement ps = c.prepareStatement(
                    """
                    INSERT INTO `%s` (uuid, scope, month_key, seconds, updated_at)
                    SELECT uuid, ?, month_key, %s, COALESCE(updated_at, UTC_TIMESTAMP())
                    FROM `%s`
                    """
                            .formatted(table, secondsCol, bak))) {
                ps.setString(1, SCOPE_TOWNY);
                ps.executeUpdate();
            }
        } else {
            try (Statement st = c.createStatement()) {
                st.executeUpdate(
                        """
                        CREATE TABLE `%s` (
                          uuid CHAR(36) NOT NULL,
                          scope VARCHAR(64) NOT NULL,
                          username VARCHAR(16) NOT NULL,
                          seconds BIGINT NOT NULL DEFAULT 0,
                          first_join_at DATETIME NOT NULL,
                          last_login_at DATETIME NOT NULL,
                          updated_at DATETIME NOT NULL,
                          PRIMARY KEY (uuid, scope),
                          INDEX idx_playtime_scope (scope, seconds),
                          INDEX idx_playtime_username (username)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """
                                .formatted(table));
            }
            String secondsCol = columnExists(c, bak, "total_playtime_seconds")
                    ? "total_playtime_seconds"
                    : "seconds";
            try (PreparedStatement ps = c.prepareStatement(
                    """
                    INSERT INTO `%s` (uuid, scope, username, seconds, first_join_at, last_login_at, updated_at)
                    SELECT uuid, ?, COALESCE(username, 'Unknown'), %s,
                           COALESCE(first_join_at, UTC_TIMESTAMP()),
                           COALESCE(last_login_at, UTC_TIMESTAMP()),
                           COALESCE(updated_at, UTC_TIMESTAMP())
                    FROM `%s`
                    """
                            .formatted(table, secondsCol, bak))) {
                ps.setString(1, SCOPE_TOWNY);
                ps.executeUpdate();
            }
        }
    }

    /** One-shot copy from old root_rootmc_* shapes into scoped tables (past → towny). */
    private static void migrateLegacyIfPresent(Connection c, TimesConfig config) throws SQLException {
        String prefix = config.tablePrefix();
        String legacyGlobal = prefix + "rootmc_playtime";
        String legacyServer = prefix + "rootmc_playtime_server";
        String legacyMonthly = prefix + "rootmc_playtime_monthly";
        String table = config.playtimeTable();
        String monthly = config.playtimeMonthlyTable();

        if (tableExists(c, legacyGlobal) && !legacyGlobal.equals(table)) {
            if (columnExists(c, legacyGlobal, "scope") && columnExists(c, legacyGlobal, "seconds")) {
                // Already scoped (e.g. prior Root-Times migration left root_rootmc_playtime in new shape).
                try (PreparedStatement ps = c.prepareStatement(
                        """
                        INSERT IGNORE INTO %s (uuid, scope, username, seconds, first_join_at, last_login_at, updated_at)
                        SELECT uuid, scope, COALESCE(username, 'Unknown'), seconds,
                               COALESCE(first_join_at, UTC_TIMESTAMP()),
                               COALESCE(last_login_at, UTC_TIMESTAMP()),
                               COALESCE(updated_at, UTC_TIMESTAMP())
                        FROM %s
                        """
                                .formatted(table, legacyGlobal))) {
                    ps.executeUpdate();
                }
            } else if (columnExists(c, legacyGlobal, "total_playtime_seconds")) {
                try (PreparedStatement ps = c.prepareStatement(
                        """
                        INSERT IGNORE INTO %s (uuid, scope, username, seconds, first_join_at, last_login_at, updated_at)
                        SELECT uuid, ?, username, total_playtime_seconds, first_join_at, last_login_at, updated_at
                        FROM %s
                        """
                                .formatted(table, legacyGlobal))) {
                    ps.setString(1, SCOPE_TOWNY);
                    ps.executeUpdate();
                }
            }
        }
        if (tableExists(c, legacyServer)
                && columnExists(c, legacyServer, "server_id")
                && columnExists(c, legacyServer, "playtime_seconds")) {
            try (PreparedStatement ps = c.prepareStatement(
                    """
                    INSERT IGNORE INTO %s (uuid, scope, username, seconds, first_join_at, last_login_at, updated_at)
                    SELECT uuid, server_id, username, playtime_seconds, first_join_at, last_login_at, updated_at
                    FROM %s
                    """
                            .formatted(table, legacyServer))) {
                ps.executeUpdate();
            }
            normalizeKnownScopes(c, table);
        }
        if (tableExists(c, legacyMonthly) && !legacyMonthly.equals(monthly)) {
            if (columnExists(c, legacyMonthly, "scope") && columnExists(c, legacyMonthly, "seconds")) {
                try (PreparedStatement ps = c.prepareStatement(
                        """
                        INSERT IGNORE INTO %s (uuid, scope, month_key, seconds, updated_at)
                        SELECT uuid, scope, month_key, seconds, COALESCE(updated_at, UTC_TIMESTAMP())
                        FROM %s
                        """
                                .formatted(monthly, legacyMonthly))) {
                    ps.executeUpdate();
                }
            } else if (columnExists(c, legacyMonthly, "playtime_seconds")) {
                try (PreparedStatement ps = c.prepareStatement(
                        """
                        INSERT IGNORE INTO %s (uuid, scope, month_key, seconds, updated_at)
                        SELECT uuid, ?, month_key, playtime_seconds, updated_at
                        FROM %s
                        """
                                .formatted(monthly, legacyMonthly))) {
                    ps.setString(1, SCOPE_TOWNY);
                    ps.executeUpdate();
                }
            }
        }
    }

    private static void normalizeKnownScopes(Connection c, String table) throws SQLException {
        if (!columnExists(c, table, "scope")) {
            return;
        }
        mergeScopeAliases(c, table, SCOPE_CLAIMS, "c", "g2", "gen2", "gen-2");
        mergeScopeAliases(c, table, SCOPE_TOWNY, "t", "g1", "gen1", "gen-1", "official");
        mergeScopeAliases(c, table, "dev", "test", "portal", "devportal", "rootmc-dev", "rootmc_dev");
    }

    /**
     * Fold alias scopes into {@code canonical}. When a canonical row already exists for the same
     * player (and month if monthly), merge seconds into it and delete the alias — a plain UPDATE
     * would hit PRIMARY KEY collisions.
     */
    private static void mergeScopeAliases(Connection c, String table, String canonical, String... aliases)
            throws SQLException {
        if (aliases.length == 0) {
            return;
        }
        String inList = String.join(
                ",",
                java.util.Arrays.stream(aliases)
                        .map(a -> "'" + a.replace("'", "''").toLowerCase(Locale.ROOT) + "'")
                        .toList());
        String canon = canonical.replace("'", "''");
        boolean monthly = columnExists(c, table, "month_key");
        try (Statement st = c.createStatement()) {
            if (monthly) {
                st.executeUpdate(
                        """
                        UPDATE `%s` t
                        INNER JOIN (
                          SELECT uuid, month_key,
                                 SUM(seconds) AS add_sec,
                                 MAX(updated_at) AS max_updated
                          FROM `%s`
                          WHERE LOWER(scope) IN (%s)
                          GROUP BY uuid, month_key
                        ) a ON a.uuid = t.uuid AND a.month_key = t.month_key
                        SET t.seconds = t.seconds + a.add_sec,
                            t.updated_at = GREATEST(t.updated_at, a.max_updated)
                        WHERE t.scope = '%s'
                        """
                                .formatted(table, table, inList, canon));
                st.executeUpdate(
                        """
                        DELETE a FROM `%s` a
                        INNER JOIN `%s` t
                          ON t.uuid = a.uuid AND t.month_key = a.month_key AND t.scope = '%s'
                        WHERE LOWER(a.scope) IN (%s)
                        """
                                .formatted(table, table, canon, inList));
            } else {
                st.executeUpdate(
                        """
                        UPDATE `%s` t
                        INNER JOIN (
                          SELECT uuid,
                                 SUM(seconds) AS add_sec,
                                 MAX(updated_at) AS max_updated,
                                 MAX(last_login_at) AS max_login,
                                 MIN(first_join_at) AS min_join
                          FROM `%s`
                          WHERE LOWER(scope) IN (%s)
                          GROUP BY uuid
                        ) a ON a.uuid = t.uuid
                        SET t.seconds = t.seconds + a.add_sec,
                            t.updated_at = GREATEST(t.updated_at, a.max_updated),
                            t.last_login_at = GREATEST(t.last_login_at, a.max_login),
                            t.first_join_at = LEAST(t.first_join_at, a.min_join)
                        WHERE t.scope = '%s'
                        """
                                .formatted(table, table, inList, canon));
                st.executeUpdate(
                        """
                        DELETE a FROM `%s` a
                        INNER JOIN `%s` t
                          ON t.uuid = a.uuid AND t.scope = '%s'
                        WHERE LOWER(a.scope) IN (%s)
                        """
                                .formatted(table, table, canon, inList));
            }
            st.executeUpdate(
                    "UPDATE `"
                            + table
                            + "` SET scope='"
                            + canon
                            + "' WHERE LOWER(scope) IN ("
                            + inList
                            + ")");
        }
    }

    private static void normalizeKnownScopesMonthly(Connection c, String table) throws SQLException {
        if (!tableExists(c, table) || !columnExists(c, table, "scope")) {
            return;
        }
        normalizeKnownScopes(c, table);
    }

    /** Players with only a {@code *} row (no server scopes) → treat that total as towny. */
    private static void foldStarOnlyIntoTowny(Connection c, String table) throws SQLException {
        if (!columnExists(c, table, "scope")) {
            return;
        }
        try (Statement st = c.createStatement()) {
            st.executeUpdate(
                    """
                    INSERT IGNORE INTO `%s` (uuid, scope, username, seconds, first_join_at, last_login_at, updated_at)
                    SELECT s.uuid, 'towny', s.username, s.seconds, s.first_join_at, s.last_login_at, s.updated_at
                    FROM `%s` s
                    WHERE s.scope = '*'
                      AND NOT EXISTS (
                        SELECT 1 FROM `%s` o WHERE o.uuid = s.uuid AND o.scope <> '*'
                      )
                    """
                            .formatted(table, table, table));
        }
    }

    private static void foldStarOnlyIntoTownyMonthly(Connection c, String table) throws SQLException {
        if (!tableExists(c, table) || !columnExists(c, table, "scope")) {
            return;
        }
        try (Statement st = c.createStatement()) {
            st.executeUpdate(
                    """
                    INSERT IGNORE INTO `%s` (uuid, scope, month_key, seconds, updated_at)
                    SELECT s.uuid, 'towny', s.month_key, s.seconds, s.updated_at
                    FROM `%s` s
                    WHERE s.scope = '*'
                      AND NOT EXISTS (
                        SELECT 1 FROM `%s` o
                        WHERE o.uuid = s.uuid AND o.month_key = s.month_key AND o.scope <> '*'
                      )
                    """
                            .formatted(table, table, table));
        }
    }

    private static boolean tableExists(Connection c, String table) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                """
                SELECT 1 FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                LIMIT 1
                """)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean columnExists(Connection c, String table, String column) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                """
                SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                LIMIT 1
                """)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static void recordLogin(Connection c, TimesConfig config, UUID uuid, String username)
            throws SQLException {
        Timestamp now = Timestamp.from(Instant.now());
        String name = username == null ? "Unknown" : username;
        try {
            upsertLogin(c, config.playtimeTable(), uuid, config.serverId(), name, now);
            recomputeStarForPlayer(c, config.playtimeTable(), uuid);
        } catch (SQLException ex) {
            if (!isMissingColumns(ex, "scope", "seconds")) {
                throw ex;
            }
            upsertLegacyLogin(c, config.playtimeTable(), uuid, name, now);
        }
    }

    private static void upsertLogin(
            Connection c, String table, UUID uuid, String scope, String username, Timestamp now)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                """
                INSERT INTO %s (uuid, scope, username, seconds, first_join_at, last_login_at, updated_at)
                VALUES (?, ?, ?, 0, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  username = VALUES(username),
                  last_login_at = VALUES(last_login_at),
                  updated_at = VALUES(updated_at)
                """
                        .formatted(table))) {
            ps.setString(1, uuid.toString());
            ps.setString(2, scope);
            ps.setString(3, username);
            ps.setTimestamp(4, now);
            ps.setTimestamp(5, now);
            ps.setTimestamp(6, now);
            ps.executeUpdate();
        }
    }

    private static void upsertLegacyLogin(
            Connection c, String table, UUID uuid, String username, Timestamp now)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                """
                INSERT INTO %s (uuid, username, total_playtime_seconds, first_join_at, last_login_at, updated_at)
                VALUES (?, ?, 0, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  username = VALUES(username),
                  last_login_at = VALUES(last_login_at),
                  updated_at = VALUES(updated_at)
                """
                        .formatted(table))) {
            ps.setString(1, uuid.toString());
            ps.setString(2, username);
            ps.setTimestamp(3, now);
            ps.setTimestamp(4, now);
            ps.setTimestamp(5, now);
            ps.executeUpdate();
        }
    }

    public static void addSession(Connection c, TimesConfig config, UUID uuid, long sessionSeconds)
            throws SQLException {
        if (sessionSeconds <= 0) {
            return;
        }
        String monthKey = YearMonth.now(McDayClock.zone()).toString();
        Timestamp now = Timestamp.from(Instant.now());
        try {
            // Server scope only — * is recomputed as SUM(towny + claims + …).
            addSeconds(c, config.playtimeTable(), uuid, config.serverId(), sessionSeconds, now);
            addMonthly(
                    c, config.playtimeMonthlyTable(), uuid, config.serverId(), monthKey, sessionSeconds, now);
            recomputeStarForPlayer(c, config.playtimeTable(), uuid);
            recomputeStarMonthlyForPlayer(c, config.playtimeMonthlyTable(), uuid, monthKey);
        } catch (SQLException ex) {
            if (!isMissingColumns(ex, "scope", "seconds")) {
                throw ex;
            }
            addLegacySeconds(c, config.playtimeTable(), uuid, sessionSeconds, now);
            addLegacyMonthly(c, config.playtimeMonthlyTable(), uuid, monthKey, sessionSeconds, now);
        }
    }

    private static void addSeconds(
            Connection c, String table, UUID uuid, String scope, long seconds, Timestamp now)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                """
                INSERT INTO %s (uuid, scope, username, seconds, first_join_at, last_login_at, updated_at)
                VALUES (?, ?, 'Unknown', ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  seconds = seconds + VALUES(seconds),
                  updated_at = VALUES(updated_at)
                """
                        .formatted(table))) {
            ps.setString(1, uuid.toString());
            ps.setString(2, scope);
            ps.setLong(3, seconds);
            ps.setTimestamp(4, now);
            ps.setTimestamp(5, now);
            ps.setTimestamp(6, now);
            ps.executeUpdate();
        }
    }

    private static void addMonthly(
            Connection c,
            String table,
            UUID uuid,
            String scope,
            String monthKey,
            long seconds,
            Timestamp now)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                """
                INSERT INTO %s (uuid, scope, month_key, seconds, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  seconds = seconds + VALUES(seconds),
                  updated_at = VALUES(updated_at)
                """
                        .formatted(table))) {
            ps.setString(1, uuid.toString());
            ps.setString(2, scope);
            ps.setString(3, monthKey);
            ps.setLong(4, seconds);
            ps.setTimestamp(5, now);
            ps.executeUpdate();
        }
    }

    private static void recomputeStarForPlayer(Connection c, String table, UUID uuid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                """
                INSERT INTO %s (uuid, scope, username, seconds, first_join_at, last_login_at, updated_at)
                SELECT uuid, '*',
                       COALESCE(MAX(username), 'Unknown'),
                       COALESCE(SUM(seconds), 0),
                       MIN(first_join_at),
                       MAX(last_login_at),
                       UTC_TIMESTAMP()
                FROM %s
                WHERE uuid = ? AND scope <> '*'
                GROUP BY uuid
                ON DUPLICATE KEY UPDATE
                  username = VALUES(username),
                  seconds = VALUES(seconds),
                  first_join_at = LEAST(%s.first_join_at, VALUES(first_join_at)),
                  last_login_at = GREATEST(%s.last_login_at, VALUES(last_login_at)),
                  updated_at = UTC_TIMESTAMP()
                """
                        .formatted(table, table, table, table))) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    private static void recomputeStarMonthlyForPlayer(
            Connection c, String table, UUID uuid, String monthKey) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                """
                INSERT INTO %s (uuid, scope, month_key, seconds, updated_at)
                SELECT uuid, '*', month_key, COALESCE(SUM(seconds), 0), UTC_TIMESTAMP()
                FROM %s
                WHERE uuid = ? AND month_key = ? AND scope <> '*'
                GROUP BY uuid, month_key
                ON DUPLICATE KEY UPDATE
                  seconds = VALUES(seconds),
                  updated_at = UTC_TIMESTAMP()
                """
                        .formatted(table, table))) {
            ps.setString(1, uuid.toString());
            ps.setString(2, monthKey);
            ps.executeUpdate();
        }
    }

    private static void recomputeAllStarPlaytime(Connection c, String table) throws SQLException {
        if (!columnExists(c, table, "scope")) {
            return;
        }
        try (Statement st = c.createStatement()) {
            st.executeUpdate(
                    """
                    INSERT INTO `%s` (uuid, scope, username, seconds, first_join_at, last_login_at, updated_at)
                    SELECT uuid, '*',
                           COALESCE(MAX(username), 'Unknown'),
                           COALESCE(SUM(seconds), 0),
                           MIN(first_join_at),
                           MAX(last_login_at),
                           UTC_TIMESTAMP()
                    FROM `%s`
                    WHERE scope <> '*'
                    GROUP BY uuid
                    ON DUPLICATE KEY UPDATE
                      username = VALUES(username),
                      seconds = VALUES(seconds),
                      first_join_at = LEAST(`%s`.first_join_at, VALUES(first_join_at)),
                      last_login_at = GREATEST(`%s`.last_login_at, VALUES(last_login_at)),
                      updated_at = UTC_TIMESTAMP()
                    """
                            .formatted(table, table, table, table));
        }
    }

    private static void recomputeAllStarPlaytimeMonthly(Connection c, String table) throws SQLException {
        if (!tableExists(c, table) || !columnExists(c, table, "scope")) {
            return;
        }
        try (Statement st = c.createStatement()) {
            st.executeUpdate(
                    """
                    INSERT INTO `%s` (uuid, scope, month_key, seconds, updated_at)
                    SELECT uuid, '*', month_key, COALESCE(SUM(seconds), 0), UTC_TIMESTAMP()
                    FROM `%s`
                    WHERE scope <> '*'
                    GROUP BY uuid, month_key
                    ON DUPLICATE KEY UPDATE
                      seconds = VALUES(seconds),
                      updated_at = UTC_TIMESTAMP()
                    """
                            .formatted(table, table));
        }
    }

    public static Optional<Long> totalSeconds(Connection c, TimesConfig config, UUID uuid) throws SQLException {
        long star = secondsForScope(c, config.playtimeTable(), uuid, SCOPE_GLOBAL).orElse(-1L);
        long sumServers = 0L;
        for (long seconds : serverScopeSeconds(c, config, uuid).values()) {
            sumServers += Math.max(0L, seconds);
        }
        // Prefer network total; never trust a stale/zero * when server scopes have time.
        if (star < 0L && sumServers <= 0L) {
            return Optional.empty();
        }
        return Optional.of(Math.max(star < 0L ? 0L : star, sumServers));
    }

    public static Optional<Long> serverSeconds(Connection c, TimesConfig config, UUID uuid) throws SQLException {
        return secondsForScope(c, config.playtimeTable(), uuid, config.serverId());
    }

    /** Per-server scopes only (excludes {@code *}), ordered towny → claims → others. */
    public static Map<String, Long> serverScopeSeconds(Connection c, TimesConfig config, UUID uuid)
            throws SQLException {
        Map<String, Long> out = new LinkedHashMap<>();
        out.put(SCOPE_TOWNY, 0L);
        out.put(SCOPE_CLAIMS, 0L);
        String table = config.playtimeTable();
        try {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT scope, seconds FROM " + table + " WHERE uuid = ? AND scope <> '*'")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String scope = TimesConfig.normalizeServerBucket(rs.getString("scope"));
                        if (SCOPE_GLOBAL.equals(scope)) {
                            continue;
                        }
                        out.merge(scope, Math.max(0L, rs.getLong("seconds")), Long::sum);
                    }
                }
            }
        } catch (SQLException ex) {
            if (!isMissingColumns(ex, "scope", "seconds")) {
                throw ex;
            }
            long legacy = secondsForScope(c, table, uuid, SCOPE_GLOBAL).orElse(0L);
            out.put(SCOPE_TOWNY, legacy);
        }
        return out;
    }

    private static Optional<Long> secondsForScope(Connection c, String table, UUID uuid, String scope)
            throws SQLException {
        try {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT seconds FROM " + table + " WHERE uuid = ? AND scope = ? LIMIT 1")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, scope);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(rs.getLong(1));
                }
            }
        } catch (SQLException ex) {
            if (!isMissingColumns(ex, "scope", "seconds")) {
                throw ex;
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT total_playtime_seconds FROM " + table + " WHERE uuid = ? LIMIT 1")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(rs.getLong(1));
                }
            }
        }
    }

    private static void addLegacySeconds(
            Connection c, String table, UUID uuid, long seconds, Timestamp now)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                """
                INSERT INTO %s (uuid, username, total_playtime_seconds, first_join_at, last_login_at, updated_at)
                VALUES (?, 'Unknown', ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  total_playtime_seconds = total_playtime_seconds + VALUES(total_playtime_seconds),
                  updated_at = VALUES(updated_at)
                """
                        .formatted(table))) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, seconds);
            ps.setTimestamp(3, now);
            ps.setTimestamp(4, now);
            ps.setTimestamp(5, now);
            ps.executeUpdate();
        }
    }

    private static void addLegacyMonthly(
            Connection c, String table, UUID uuid, String monthKey, long seconds, Timestamp now)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                """
                INSERT INTO %s (uuid, month_key, playtime_seconds, updated_at)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  playtime_seconds = playtime_seconds + VALUES(playtime_seconds),
                  updated_at = VALUES(updated_at)
                """
                        .formatted(table))) {
            ps.setString(1, uuid.toString());
            ps.setString(2, monthKey);
            ps.setLong(3, seconds);
            ps.setTimestamp(4, now);
            ps.executeUpdate();
        }
    }

    public static String formatDuration(long totalSeconds) {
        long sec = Math.max(0L, totalSeconds);
        long days = sec / 86_400L;
        long hours = (sec % 86_400L) / 3_600L;
        long minutes = (sec % 3_600L) / 60L;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    public static String formatCountdown(long millis) {
        long sec = Math.max(0L, millis / 1000L);
        long hours = sec / 3_600L;
        long minutes = (sec % 3_600L) / 60L;
        long seconds = sec % 60L;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    public record PlaytimeTopRow(String username, long seconds) {}

    /** Top all-time playtime (global scope when available). */
    public static java.util.List<PlaytimeTopRow> topPlaytime(Connection c, TimesConfig config, int limit)
            throws SQLException {
        int capped = Math.max(1, Math.min(10, limit));
        String table = config.playtimeTable();
        java.util.List<PlaytimeTopRow> rows = new java.util.ArrayList<>();
        try {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT username, seconds FROM "
                            + table
                            + " WHERE scope = '*' ORDER BY seconds DESC LIMIT ?")) {
                ps.setInt(1, capped);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new PlaytimeTopRow(rs.getString(1), rs.getLong(2)));
                    }
                }
            }
            if (!rows.isEmpty()) {
                return rows;
            }
        } catch (SQLException ex) {
            if (!isMissingColumns(ex, "scope", "seconds") && !isMissingTable(ex)) {
                throw ex;
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT username, total_playtime_seconds FROM "
                        + table
                        + " ORDER BY total_playtime_seconds DESC LIMIT ?")) {
            ps.setInt(1, capped);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new PlaytimeTopRow(rs.getString(1), rs.getLong(2)));
                }
            }
        } catch (SQLException ex) {
            if (isMissingTable(ex) || isMissingColumns(ex, "total_playtime_seconds")) {
                return rows;
            }
            throw ex;
        }
        return rows;
    }

    public static String displayScopeLabel(String scope) {
        String s = scope == null ? "" : scope.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case SCOPE_TOWNY -> "Towny";
            case SCOPE_CLAIMS -> "Claims";
            case SCOPE_GLOBAL -> "Total";
            default -> {
                if (s.isEmpty()) {
                    yield "Server";
                }
                yield Character.toUpperCase(s.charAt(0)) + s.substring(1);
            }
        };
    }

    private static boolean isMissingColumns(SQLException ex, String... names) {
        String msg = ex.getMessage();
        if (msg == null) {
            return false;
        }
        for (String name : names) {
            if (msg.contains("Unknown column '" + name + "'")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMissingTable(SQLException ex) {
        String msg = ex.getMessage();
        if (msg == null) {
            return false;
        }
        String lower = msg.toLowerCase(Locale.ROOT);
        return lower.contains("doesn't exist") || lower.contains("does not exist") || lower.contains("unknown table");
    }
}
