package com.rootrecord.minecraft.rootrewards.data;

import com.rootrecord.minecraft.rootrewards.config.RewardsConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class RewardsStore {

    private final RewardsConfig config;

    public RewardsStore(RewardsConfig config) {
        this.config = config;
    }

    public void initSchema() throws SQLException {
        if (!config.mysqlEnabled()) {
            return;
        }
        try (Connection c = open(); Statement st = c.createStatement()) {
            st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS %s (
                      uuid CHAR(36) PRIMARY KEY,
                      last_claimed_tier INT NOT NULL DEFAULT -1,
                      updated_at DATETIME NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """
                            .formatted(config.claimsTable()));
            st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS %s (
                      uuid CHAR(36) PRIMARY KEY,
                      total_playtime_seconds BIGINT NOT NULL DEFAULT 0,
                      updated_at DATETIME NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """
                            .formatted(config.fallbackPlaytimeTable()));
            st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS %s (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      uuid CHAR(36) NOT NULL,
                      service VARCHAR(64) NOT NULL,
                      voted_at DATETIME NOT NULL,
                      gold_earned DOUBLE NOT NULL DEFAULT 0,
                      INDEX idx_rewards_votes_uuid_service (uuid, service, voted_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """
                            .formatted(config.votesTable()));
            ensureVoteGoldColumn(st);
        }
    }

    private void ensureVoteGoldColumn(Statement st) throws SQLException {
        try {
            st.executeUpdate(
                    "ALTER TABLE " + config.votesTable() + " ADD COLUMN gold_earned DOUBLE NOT NULL DEFAULT 0");
        } catch (SQLException ex) {
            String msg = ex.getMessage();
            if (msg == null || (!msg.contains("Duplicate column") && !msg.contains("duplicate column name"))) {
                throw ex;
            }
        }
    }

    public long readTotalPlaytimeSeconds(UUID uuid) throws SQLException {
        if (config.mysqlEnabled() && config.useRootMcPlaytime()) {
            Long fromRootMc = readRootMcPlaytime(uuid);
            if (fromRootMc != null) {
                return fromRootMc;
            }
        }
        return readFallbackPlaytime(uuid);
    }

    public PlaytimeBreakdown readPlaytimeBreakdown(UUID uuid) throws SQLException {
        if (uuid == null) {
            return new PlaytimeBreakdown(null, null, 0L, Map.of());
        }
        if (config.mysqlEnabled() && config.useRootMcPlaytime()) {
            PlaytimeBreakdown scoped = readRootMcBreakdown(uuid);
            if (scoped != null) {
                return scoped;
            }
        }
        long seconds = readFallbackPlaytime(uuid);
        Map<String, Long> byServer = new LinkedHashMap<>();
        byServer.put("towny", seconds);
        byServer.put("claims", 0L);
        return new PlaytimeBreakdown(uuid, null, seconds, byServer);
    }

    public Optional<PlaytimeRow> findPlaytimeRow(UUID uuid) throws SQLException {
        if (uuid == null) {
            return Optional.empty();
        }
        if (config.mysqlEnabled() && config.useRootMcPlaytime()) {
            Optional<PlaytimeRow> row = readRootMcRow(uuid);
            if (row.isPresent()) {
                return row;
            }
        }
        long seconds = readFallbackPlaytime(uuid);
        if (seconds <= 0L && !config.mysqlEnabled()) {
            return Optional.empty();
        }
        return Optional.of(new PlaytimeRow(uuid, null, seconds));
    }

    public Optional<PlaytimeRow> findByUsername(String username) throws SQLException {
        if (username == null || username.isBlank() || !config.mysqlEnabled()) {
            return Optional.empty();
        }
        if (config.useRootMcPlaytime()) {
            Optional<PlaytimeRow> row = readRootMcRowByUsername(username.trim());
            if (row.isPresent()) {
                return row;
            }
        }
        return Optional.empty();
    }

    public List<PlaytimeRow> topPlaytime(int limit) throws SQLException {
        int capped = Math.max(1, Math.min(50, limit));
        if (!config.mysqlEnabled()) {
            return List.of();
        }
        if (config.useRootMcPlaytime()) {
            List<PlaytimeRow> rows = readTopFromPlaytimeTable(config.playtimeTableFqn(), capped);
            if (!rows.isEmpty()) {
                return rows;
            }
        }
        return readTopFromFallbackTable(config.fallbackPlaytimeTable(), capped);
    }

    public int rankForUuid(UUID uuid) throws SQLException {
        if (uuid == null || !config.mysqlEnabled()) {
            return -1;
        }
        Optional<PlaytimeRow> self = findPlaytimeRow(uuid);
        if (self.isEmpty()) {
            return -1;
        }
        if (config.useRootMcPlaytime()) {
            try (Connection c = open();
                    PreparedStatement ps = c.prepareStatement(
                            "SELECT COUNT(*) + 1 FROM "
                                    + config.playtimeTableFqn()
                                    + " WHERE scope = '*' AND seconds > ?")) {
                ps.setLong(1, self.get().totalSeconds());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : -1;
                }
            } catch (SQLException ex) {
                if (isMissingColumns(ex, "scope", "seconds")) {
                    try (Connection c = open();
                            PreparedStatement ps = c.prepareStatement(
                                    "SELECT COUNT(*) + 1 FROM "
                                            + config.playtimeTableFqn()
                                            + " WHERE total_playtime_seconds > ?")) {
                        ps.setLong(1, self.get().totalSeconds());
                        try (ResultSet rs = ps.executeQuery()) {
                            return rs.next() ? rs.getInt(1) : -1;
                        }
                    }
                }
                if (isMissingTable(ex)) {
                    return -1;
                }
                throw ex;
            }
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) + 1 FROM "
                                + config.fallbackPlaytimeTable()
                                + " WHERE total_playtime_seconds > ?")) {
            ps.setLong(1, self.get().totalSeconds());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        } catch (SQLException ex) {
            if (isMissingTable(ex)) {
                return -1;
            }
            throw ex;
        }
    }

    private Optional<PlaytimeRow> readRootMcRow(UUID uuid) throws SQLException {
        String table = config.playtimeTableFqn();
        try {
            try (Connection c = open();
                    PreparedStatement ps = c.prepareStatement(
                            """
                            SELECT uuid, username, seconds FROM %s
                            WHERE uuid = ? AND scope = '*' LIMIT 1
                            """
                                    .formatted(table))) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(mapScopedPlaytimeRow(rs));
                }
            }
        } catch (SQLException ex) {
            if (isMissingColumns(ex, "scope", "seconds")) {
                try (Connection c = open();
                        PreparedStatement ps = c.prepareStatement(
                                """
                                SELECT uuid, username, total_playtime_seconds FROM %s
                                WHERE uuid = ? LIMIT 1
                                """
                                        .formatted(table))) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return Optional.empty();
                        }
                        return Optional.of(new PlaytimeRow(
                                UUID.fromString(rs.getString("uuid")),
                                rs.getString("username"),
                                rs.getLong("total_playtime_seconds")));
                    }
                }
            }
            if (isMissingTable(ex)) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    private Optional<PlaytimeRow> readRootMcRowByUsername(String username) throws SQLException {
        String table = config.playtimeTableFqn();
        try {
            try (Connection c = open();
                    PreparedStatement ps = c.prepareStatement(
                            """
                            SELECT uuid, username, seconds FROM %s
                            WHERE scope = '*' AND LOWER(username) = LOWER(?) LIMIT 1
                            """
                                    .formatted(table))) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(mapScopedPlaytimeRow(rs));
                }
            }
        } catch (SQLException ex) {
            if (isMissingColumns(ex, "scope", "seconds")) {
                try (Connection c = open();
                        PreparedStatement ps = c.prepareStatement(
                                """
                                SELECT uuid, username, total_playtime_seconds FROM %s
                                WHERE LOWER(username) = LOWER(?) LIMIT 1
                                """
                                        .formatted(table))) {
                    ps.setString(1, username);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return Optional.empty();
                        }
                        return Optional.of(new PlaytimeRow(
                                UUID.fromString(rs.getString("uuid")),
                                rs.getString("username"),
                                rs.getLong("total_playtime_seconds")));
                    }
                }
            }
            if (isMissingTable(ex)) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    private List<PlaytimeRow> readTopFromPlaytimeTable(String table, int limit) throws SQLException {
        String sql = "SELECT uuid, username, seconds FROM " + table
                + " WHERE scope = '*' ORDER BY seconds DESC LIMIT ?";
        List<PlaytimeRow> out = new ArrayList<>();
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapScopedPlaytimeRow(rs));
                }
            }
        } catch (SQLException ex) {
            if (isMissingColumns(ex, "scope", "seconds")) {
                String legacySql = "SELECT uuid, username, total_playtime_seconds FROM " + table
                        + " ORDER BY total_playtime_seconds DESC LIMIT ?";
                try (Connection c = open();
                        PreparedStatement ps = c.prepareStatement(legacySql)) {
                    ps.setInt(1, limit);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            out.add(new PlaytimeRow(
                                    UUID.fromString(rs.getString("uuid")),
                                    rs.getString("username"),
                                    rs.getLong("total_playtime_seconds")));
                        }
                    }
                }
                return out;
            }
            if (isMissingTable(ex)) {
                return List.of();
            }
            throw ex;
        }
        return out;
    }

    private List<PlaytimeRow> readTopFromFallbackTable(String table, int limit) throws SQLException {
        String sql = "SELECT uuid, total_playtime_seconds FROM " + table
                + " ORDER BY total_playtime_seconds DESC LIMIT ?";
        List<PlaytimeRow> out = new ArrayList<>();
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new PlaytimeRow(
                            UUID.fromString(rs.getString("uuid")),
                            null,
                            rs.getLong("total_playtime_seconds")));
                }
            }
        } catch (SQLException ex) {
            if (isMissingTable(ex)) {
                return List.of();
            }
            throw ex;
        }
        return out;
    }

    private static PlaytimeRow mapScopedPlaytimeRow(ResultSet rs) throws SQLException {
        return new PlaytimeRow(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("username"),
                rs.getLong("seconds"));
    }

    private Long readRootMcPlaytime(UUID uuid) throws SQLException {
        String table = config.playtimeTableFqn();
        try {
            long star = -1L;
            try (Connection c = open();
                    PreparedStatement ps = c.prepareStatement(
                            "SELECT seconds FROM " + table + " WHERE uuid = ? AND scope = '*' LIMIT 1")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        star = Math.max(0L, rs.getLong("seconds"));
                    }
                }
            }
            long sumServers = 0L;
            try (Connection c = open();
                    PreparedStatement ps = c.prepareStatement(
                            "SELECT COALESCE(SUM(seconds), 0) FROM " + table
                                    + " WHERE uuid = ? AND scope <> '*'")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        sumServers = Math.max(0L, rs.getLong(1));
                    }
                }
            }
            // Prefer network total; never trust a stale/zero * when server scopes have time.
            if (star < 0L && sumServers <= 0L) {
                return 0L;
            }
            return Math.max(star < 0L ? 0L : star, sumServers);
        } catch (SQLException ex) {
            if (isMissingColumns(ex, "scope", "seconds")) {
                try (Connection c = open();
                        PreparedStatement ps = c.prepareStatement(
                                "SELECT total_playtime_seconds FROM " + table + " WHERE uuid = ? LIMIT 1")) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return rs.getLong("total_playtime_seconds");
                        }
                    }
                }
                return 0L;
            }
            if (isMissingTable(ex)) {
                return null;
            }
            throw ex;
        }
    }

    private PlaytimeBreakdown readRootMcBreakdown(UUID uuid) throws SQLException {
        String table = config.playtimeTableFqn();
        try {
            long total = 0L;
            String username = null;
            Map<String, Long> byServer = new LinkedHashMap<>();
            byServer.put("towny", 0L);
            byServer.put("claims", 0L);
            try (Connection c = open();
                    PreparedStatement ps = c.prepareStatement(
                            "SELECT scope, username, seconds FROM " + table + " WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    boolean any = false;
                    while (rs.next()) {
                        any = true;
                        String scope = normalizeScope(rs.getString("scope"));
                        long seconds = Math.max(0L, rs.getLong("seconds"));
                        String name = rs.getString("username");
                        if (name != null && !name.isBlank()) {
                            username = name;
                        }
                        if ("*".equals(scope)) {
                            total = seconds;
                        } else {
                            byServer.merge(scope, seconds, Long::sum);
                        }
                    }
                    if (!any) {
                        return null;
                    }
                }
            }
            long sumServers = byServer.values().stream().mapToLong(Long::longValue).sum();
            total = Math.max(total, sumServers);
            return new PlaytimeBreakdown(uuid, username, total, byServer);
        } catch (SQLException ex) {
            if (isMissingColumns(ex, "scope", "seconds")) {
                try (Connection c = open();
                        PreparedStatement ps = c.prepareStatement(
                                """
                                SELECT uuid, username, total_playtime_seconds FROM %s
                                WHERE uuid = ? LIMIT 1
                                """
                                        .formatted(table))) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return null;
                        }
                        long seconds = rs.getLong("total_playtime_seconds");
                        Map<String, Long> byServer = new LinkedHashMap<>();
                        byServer.put("towny", seconds);
                        byServer.put("claims", 0L);
                        return new PlaytimeBreakdown(
                                UUID.fromString(rs.getString("uuid")),
                                rs.getString("username"),
                                seconds,
                                byServer);
                    }
                }
            }
            if (isMissingTable(ex)) {
                return null;
            }
            throw ex;
        }
    }

    private static String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return "other";
        }
        String s = scope.trim().toLowerCase();
        if ("*".equals(s)) {
            return "*";
        }
        return switch (s) {
            case "claims", "c", "g2", "gen2", "gen-2" -> "claims";
            case "towny", "t", "g1", "gen1", "gen-1", "official" -> "towny";
            default -> s;
        };
    }

    private long readFallbackPlaytime(UUID uuid) throws SQLException {
        if (!config.mysqlEnabled()) {
            return 0L;
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT total_playtime_seconds FROM " + config.fallbackPlaytimeTable() + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("total_playtime_seconds");
                }
            }
        }
        return 0L;
    }

    public void addFallbackPlaytime(UUID uuid, long deltaSeconds) throws SQLException {
        if (!config.mysqlEnabled() || deltaSeconds <= 0) {
            return;
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        """
                        INSERT INTO %s (uuid, total_playtime_seconds, updated_at)
                        VALUES (?, ?, NOW())
                        ON DUPLICATE KEY UPDATE
                          total_playtime_seconds = total_playtime_seconds + VALUES(total_playtime_seconds),
                          updated_at = NOW()
                        """
                                .formatted(config.fallbackPlaytimeTable()))) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, deltaSeconds);
            ps.executeUpdate();
        }
    }

    public int lastClaimedTier(UUID uuid) throws SQLException {
        if (!config.mysqlEnabled()) {
            return -1;
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT last_claimed_tier FROM " + config.claimsTable() + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("last_claimed_tier");
                }
            }
        }
        return -1;
    }

    public void setLastClaimedTier(UUID uuid, int tier) throws SQLException {
        if (!config.mysqlEnabled()) {
            return;
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        """
                        INSERT INTO %s (uuid, last_claimed_tier, updated_at)
                        VALUES (?, ?, NOW())
                        ON DUPLICATE KEY UPDATE last_claimed_tier = VALUES(last_claimed_tier), updated_at = NOW()
                        """
                                .formatted(config.claimsTable()))) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, tier);
            ps.executeUpdate();
        }
    }

    public Optional<Instant> lastVoteAt(UUID uuid, String service) throws SQLException {
        if (!config.mysqlEnabled()) {
            return Optional.empty();
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        """
                        SELECT voted_at FROM %s
                        WHERE uuid = ? AND service = ?
                        ORDER BY voted_at DESC LIMIT 1
                        """
                                .formatted(config.votesTable()))) {
            ps.setString(1, uuid.toString());
            ps.setString(2, service);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getTimestamp("voted_at").toInstant());
                }
            }
        }
        return Optional.empty();
    }

    /** Latest vote timestamp per Votifier service name for a player. */
    public java.util.Map<String, Instant> lastVotesByService(UUID uuid) throws SQLException {
        if (!config.mysqlEnabled() || uuid == null) {
            return java.util.Map.of();
        }
        java.util.Map<String, Instant> out = new java.util.HashMap<>();
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        """
                        SELECT service, MAX(voted_at) AS last_vote FROM %s
                        WHERE uuid = ?
                        GROUP BY service
                        """
                                .formatted(config.votesTable()))) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String service = rs.getString("service");
                    java.sql.Timestamp ts = rs.getTimestamp("last_vote");
                    if (service != null && !service.isBlank() && ts != null) {
                        out.put(service, ts.toInstant());
                    }
                }
            }
        }
        return out;
    }

    /** Confirmed listing votes since {@code since}, newest first. */
    public List<Instant> voteTimesSince(UUID uuid, Instant since) throws SQLException {
        if (!config.mysqlEnabled() || uuid == null || since == null) {
            return List.of();
        }
        List<Instant> out = new ArrayList<>();
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        """
                        SELECT voted_at FROM %s
                        WHERE LOWER(REPLACE(uuid, '-', '')) = LOWER(REPLACE(?, '-', ''))
                          AND voted_at >= ?
                        ORDER BY voted_at DESC
                        """
                                .formatted(config.votesTable()))) {
            ps.setString(1, uuid.toString());
            ps.setTimestamp(2, Timestamp.from(since));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp(1);
                    if (ts != null) {
                        out.add(ts.toInstant());
                    }
                }
            }
        }
        return out;
    }

    public Optional<Instant> lastVoteAtAny(UUID uuid) throws SQLException {
        if (!config.mysqlEnabled()) {
            return Optional.empty();
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT MAX(voted_at) AS last_vote FROM " + config.votesTable() + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getTimestamp("last_vote") != null) {
                    return Optional.of(rs.getTimestamp("last_vote").toInstant());
                }
            }
        }
        return Optional.empty();
    }

    public VoteTotals recordVote(UUID uuid, String service, double goldEarned) throws SQLException {
        return recordVoteWithId(uuid, service, goldEarned).totals();
    }

    public VoteInsert recordVoteWithId(UUID uuid, String service, double goldEarned) throws SQLException {
        if (!config.mysqlEnabled()) {
            return new VoteInsert(VoteTotals.empty(), -1L);
        }
        long voteId = -1L;
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO " + config.votesTable()
                                + " (uuid, service, voted_at, gold_earned) VALUES (?, ?, NOW(), ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, service);
            ps.setDouble(3, Math.max(0.0, goldEarned));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    voteId = keys.getLong(1);
                }
            }
        }
        return new VoteInsert(readVoteTotals(uuid), voteId);
    }

    public VoteTotals readVoteTotals(UUID uuid) throws SQLException {
        if (!config.mysqlEnabled() || uuid == null) {
            return VoteTotals.empty();
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*), COALESCE(SUM(gold_earned), 0) FROM "
                                + config.votesTable()
                                + " WHERE LOWER(REPLACE(uuid, '-', '')) = LOWER(REPLACE(?, '-', ''))")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new VoteTotals(rs.getInt(1), rs.getDouble(2));
                }
            }
        }
        return VoteTotals.empty();
    }

    private Connection open() throws SQLException {
        String url = "jdbc:mysql://" + config.mysqlHost() + ":" + config.mysqlPort() + "/"
                + config.mysqlDatabase() + "?" + config.mysqlJdbcParams();
        return DriverManager.getConnection(url, config.mysqlUsername(), config.mysqlPassword());
    }

    private static boolean isMissingTable(SQLException ex) {
        String msg = ex.getMessage();
        return msg != null && (msg.contains("doesn't exist") || msg.contains("Unknown table"));
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
}
