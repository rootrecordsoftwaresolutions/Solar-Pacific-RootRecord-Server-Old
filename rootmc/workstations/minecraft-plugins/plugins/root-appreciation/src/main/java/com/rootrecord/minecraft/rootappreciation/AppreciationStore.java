package com.rootrecord.minecraft.rootappreciation;

import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import com.rootrecord.minecraft.common.mysql.MysqlConnections;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/** Ledger + player streak for Appreciation Tokens. */
public final class AppreciationStore {

    public record Stats(long issued, long redeemed, long supply) {}

    public record PlayerStreak(int streakDay, long lastBonusAtMs) {}

    public record PendingGrant(long id, UUID uuid, String playerName, int amount, String reason, String idempotencyKey) {}

    public record VoteRow(long id, UUID uuid) {}

    private final JavaPlugin plugin;
    private final String ledgerTable;
    private final String playerTable;
    private final String aggregateTable;
    private final String pendingTable;
    private final String votesTable;
    private volatile boolean ready;

    public AppreciationStore(JavaPlugin plugin, String tablePrefix) {
        this.plugin = plugin;
        String prefix = tablePrefix == null || tablePrefix.isBlank() ? "root_" : tablePrefix;
        this.ledgerTable = prefix + "appreciation_ledger";
        this.playerTable = prefix + "appreciation_player";
        this.aggregateTable = prefix + "appreciation_aggregate";
        this.pendingTable = prefix + "appreciation_pending";
        this.votesTable = prefix + "rewards_votes";
    }

    public boolean ready() {
        return ready;
    }

    public void initSchema() {
        RootMcDatabaseConfig.DatabaseSettings db = database();
        if (db == null || !db.isConfigured()) {
            plugin.getLogger().warning("Root-Appreciation: MySQL not configured — stats/bonus will not persist.");
            ready = false;
            return;
        }
        try (Connection c = MysqlConnections.open(db); Statement st = c.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      player_uuid CHAR(36) NOT NULL,
                      player_name VARCHAR(16) NOT NULL,
                      action VARCHAR(16) NOT NULL,
                      amount INT NOT NULL,
                      reason VARCHAR(64) NOT NULL,
                      issue_batch VARCHAR(40) NULL,
                      created_at DATETIME NOT NULL,
                      INDEX idx_appr_player (player_uuid),
                      INDEX idx_appr_action (action),
                      INDEX idx_appr_created (created_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(ledgerTable));
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      uuid CHAR(36) NOT NULL PRIMARY KEY,
                      display_name VARCHAR(16) NOT NULL,
                      issued_total BIGINT NOT NULL DEFAULT 0,
                      redeemed_total BIGINT NOT NULL DEFAULT 0,
                      streak_day INT NOT NULL DEFAULT 0,
                      last_bonus_at DATETIME NULL,
                      updated_at DATETIME NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(playerTable));
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      id TINYINT NOT NULL PRIMARY KEY,
                      issued_total BIGINT NOT NULL DEFAULT 0,
                      redeemed_total BIGINT NOT NULL DEFAULT 0
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(aggregateTable));
            st.executeUpdate(
                    "INSERT IGNORE INTO " + aggregateTable + " (id, issued_total, redeemed_total) VALUES (1, 0, 0)");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      player_uuid CHAR(36) NOT NULL,
                      player_name VARCHAR(16) NOT NULL,
                      amount INT NOT NULL,
                      reason VARCHAR(64) NOT NULL,
                      idempotency_key VARCHAR(96) NOT NULL,
                      created_at DATETIME NOT NULL,
                      delivered_at DATETIME NULL,
                      UNIQUE KEY uq_appr_pending_key (idempotency_key),
                      INDEX idx_appr_pending_player (player_uuid, delivered_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(pendingTable));
            ready = true;
            plugin.getLogger().info("Root-Appreciation MySQL ready.");
        } catch (SQLException ex) {
            ready = false;
            plugin.getLogger().log(Level.SEVERE, "Root-Appreciation schema failed: " + ex.getMessage());
        }
    }

    /**
     * Queue tokens for later delivery. Returns {@code true} if a new pending row was inserted
     * (false when the idempotency key already exists).
     */
    public boolean enqueuePending(UUID uuid, String name, int amount, String reason, String idempotencyKey) {
        if (!ready || uuid == null || amount <= 0 || idempotencyKey == null || idempotencyKey.isBlank()) {
            return false;
        }
        String key = idempotencyKey.trim();
        if (key.length() > 96) {
            key = key.substring(0, 96);
        }
        try (Connection c = MysqlConnections.open(database());
                PreparedStatement ps = c.prepareStatement(
                        "INSERT IGNORE INTO " + pendingTable
                                + " (player_uuid, player_name, amount, reason, idempotency_key, created_at, delivered_at)"
                                + " VALUES (?, ?, ?, ?, ?, ?, NULL)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name == null || name.isBlank() ? "player" : name);
            ps.setInt(3, amount);
            ps.setString(4, reason == null ? "pending" : reason);
            ps.setString(5, key);
            ps.setTimestamp(6, Timestamp.from(Instant.now()));
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            plugin.getLogger().warning("Appreciation pending enqueue failed: " + ex.getMessage());
            return false;
        }
    }

    public List<PendingGrant> listUndelivered(UUID uuid) {
        if (!ready || uuid == null) {
            return List.of();
        }
        List<PendingGrant> out = new ArrayList<>();
        try (Connection c = MysqlConnections.open(database());
                PreparedStatement ps = c.prepareStatement(
                        "SELECT id, player_uuid, player_name, amount, reason, idempotency_key FROM " + pendingTable
                                + " WHERE player_uuid = ? AND delivered_at IS NULL ORDER BY id ASC")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new PendingGrant(
                            rs.getLong(1),
                            UUID.fromString(rs.getString(2)),
                            rs.getString(3),
                            rs.getInt(4),
                            rs.getString(5),
                            rs.getString(6)));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Appreciation pending list failed: " + ex.getMessage());
        }
        return out;
    }

    public boolean markDelivered(long pendingId) {
        if (!ready || pendingId <= 0) {
            return false;
        }
        try (Connection c = MysqlConnections.open(database());
                PreparedStatement ps = c.prepareStatement(
                        "UPDATE " + pendingTable + " SET delivered_at = ? WHERE id = ? AND delivered_at IS NULL")) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setLong(2, pendingId);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            plugin.getLogger().warning("Appreciation pending mark failed: " + ex.getMessage());
            return false;
        }
    }

    /** Vote rows from Root-Play rewards table (same MySQL) for backfill. */
    public List<VoteRow> listVotesForPlayer(UUID uuid) {
        if (!ready || uuid == null) {
            return List.of();
        }
        List<VoteRow> out = new ArrayList<>();
        try (Connection c = MysqlConnections.open(database());
                PreparedStatement ps = c.prepareStatement(
                        "SELECT id, uuid FROM " + votesTable + " WHERE uuid = ? ORDER BY id ASC")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new VoteRow(rs.getLong(1), UUID.fromString(rs.getString(2))));
                }
            }
        } catch (SQLException ex) {
            // Table may not exist yet on a fresh host — not fatal.
            String msg = ex.getMessage();
            if (msg == null || (!msg.contains("doesn't exist") && !msg.contains("Unknown table"))) {
                plugin.getLogger().warning("Appreciation vote backfill scan failed: " + ex.getMessage());
            }
        }
        return out;
    }

    public Stats globalStats() {
        if (!ready) {
            return new Stats(0, 0, 0);
        }
        try (Connection c = MysqlConnections.open(database());
                PreparedStatement ps = c.prepareStatement(
                        "SELECT issued_total, redeemed_total FROM " + aggregateTable + " WHERE id = 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long issued = rs.getLong(1);
                    long redeemed = rs.getLong(2);
                    return new Stats(issued, redeemed, Math.max(0, issued - redeemed));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Appreciation global stats failed: " + ex.getMessage());
        }
        return new Stats(0, 0, 0);
    }

    public Stats playerStats(UUID uuid) {
        if (!ready) {
            return new Stats(0, 0, 0);
        }
        try (Connection c = MysqlConnections.open(database());
                PreparedStatement ps = c.prepareStatement(
                        "SELECT issued_total, redeemed_total FROM " + playerTable + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long issued = rs.getLong(1);
                    long redeemed = rs.getLong(2);
                    return new Stats(issued, redeemed, Math.max(0, issued - redeemed));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Appreciation player stats failed: " + ex.getMessage());
        }
        return new Stats(0, 0, 0);
    }

    public Optional<PlayerStreak> playerStreak(UUID uuid) {
        if (!ready) {
            return Optional.empty();
        }
        try (Connection c = MysqlConnections.open(database());
                PreparedStatement ps = c.prepareStatement(
                        "SELECT streak_day, last_bonus_at FROM " + playerTable + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp ts = rs.getTimestamp(2);
                    long ms = ts == null ? 0L : ts.getTime();
                    return Optional.of(new PlayerStreak(rs.getInt(1), ms));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Appreciation streak read failed: " + ex.getMessage());
        }
        return Optional.empty();
    }

    public boolean recordIssue(UUID uuid, String name, int amount, String reason, String batchId) {
        return recordMovement(uuid, name, "ISSUE", amount, reason, batchId);
    }

    public boolean recordRedeem(UUID uuid, String name, int amount, String reason) {
        return recordMovement(uuid, name, "REDEEM", amount, reason, null);
    }

    private boolean recordMovement(
            UUID uuid, String name, String action, int amount, String reason, String batchId) {
        if (!ready || amount <= 0) {
            return false;
        }
        RootMcDatabaseConfig.DatabaseSettings db = database();
        try (Connection c = MysqlConnections.open(db)) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ledger = c.prepareStatement(
                        "INSERT INTO " + ledgerTable
                                + " (player_uuid, player_name, action, amount, reason, issue_batch, created_at)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    ledger.setString(1, uuid.toString());
                    ledger.setString(2, name == null ? "player" : name);
                    ledger.setString(3, action);
                    ledger.setInt(4, amount);
                    ledger.setString(5, reason == null ? "" : reason);
                    ledger.setString(6, batchId);
                    ledger.setTimestamp(7, Timestamp.from(Instant.now()));
                    ledger.executeUpdate();
                }
                ensurePlayerRow(c, uuid, name);
                if ("ISSUE".equals(action)) {
                    try (PreparedStatement upd = c.prepareStatement(
                            "UPDATE " + playerTable
                                    + " SET issued_total = issued_total + ?, display_name = ?, updated_at = ? WHERE uuid = ?")) {
                        upd.setInt(1, amount);
                        upd.setString(2, name == null ? "player" : name);
                        upd.setTimestamp(3, Timestamp.from(Instant.now()));
                        upd.setString(4, uuid.toString());
                        upd.executeUpdate();
                    }
                    try (PreparedStatement agg = c.prepareStatement(
                            "UPDATE " + aggregateTable + " SET issued_total = issued_total + ? WHERE id = 1")) {
                        agg.setInt(1, amount);
                        agg.executeUpdate();
                    }
                } else {
                    try (PreparedStatement upd = c.prepareStatement(
                            "UPDATE " + playerTable
                                    + " SET redeemed_total = redeemed_total + ?, display_name = ?, updated_at = ? WHERE uuid = ?")) {
                        upd.setInt(1, amount);
                        upd.setString(2, name == null ? "player" : name);
                        upd.setTimestamp(3, Timestamp.from(Instant.now()));
                        upd.setString(4, uuid.toString());
                        upd.executeUpdate();
                    }
                    try (PreparedStatement agg = c.prepareStatement(
                            "UPDATE " + aggregateTable + " SET redeemed_total = redeemed_total + ? WHERE id = 1")) {
                        agg.setInt(1, amount);
                        agg.executeUpdate();
                    }
                }
                c.commit();
                return true;
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Appreciation ledger write failed: " + ex.getMessage());
            return false;
        }
    }

    public boolean updateStreak(UUID uuid, String name, int streakDay, long bonusAtMs) {
        if (!ready) {
            return false;
        }
        try (Connection c = MysqlConnections.open(database())) {
            ensurePlayerRow(c, uuid, name);
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + playerTable
                            + " SET streak_day = ?, last_bonus_at = ?, display_name = ?, updated_at = ? WHERE uuid = ?")) {
                ps.setInt(1, streakDay);
                ps.setTimestamp(2, new Timestamp(bonusAtMs));
                ps.setString(3, name == null ? "player" : name);
                ps.setTimestamp(4, Timestamp.from(Instant.now()));
                ps.setString(5, uuid.toString());
                ps.executeUpdate();
            }
            return true;
        } catch (SQLException ex) {
            plugin.getLogger().warning("Appreciation streak update failed: " + ex.getMessage());
            return false;
        }
    }

    private void ensurePlayerRow(Connection c, UUID uuid, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT IGNORE INTO " + playerTable
                        + " (uuid, display_name, issued_total, redeemed_total, streak_day, last_bonus_at, updated_at)"
                        + " VALUES (?, ?, 0, 0, 0, NULL, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name == null ? "player" : name);
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
    }

    private RootMcDatabaseConfig.DatabaseSettings database() {
        return RootMcDatabaseConfig.resolve(plugin, null);
    }
}
