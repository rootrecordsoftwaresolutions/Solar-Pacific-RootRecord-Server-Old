package com.rootrecord.minecraft.roothelp.commandtest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CommandTestStore {

    public record Progress(
            UUID uuid,
            Instant enrolledAt,
            Instant completedAt,
            double totalGoldPaid,
            int eligibleCount,
            double goldPerCommand) {}

    public record DoneEntry(String testKey, Instant testedAt, double goldPaid, double feeRefunded, String reportNote) {}

    private final CommandTestConfig config;

    public CommandTestStore(CommandTestConfig config) {
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
                      enrolled_at DATETIME NOT NULL,
                      completed_at DATETIME NULL,
                      total_gold_paid DOUBLE NOT NULL DEFAULT 0,
                      eligible_count INT NOT NULL DEFAULT 0,
                      gold_per_command DOUBLE NOT NULL DEFAULT 0
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """
                            .formatted(config.progressTable()));
            st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS %s (
                      minecraft_uuid CHAR(36) NOT NULL,
                      test_key VARCHAR(64) NOT NULL,
                      tested_at DATETIME NOT NULL,
                      gold_paid DOUBLE NOT NULL DEFAULT 0,
                      fee_refunded DOUBLE NOT NULL DEFAULT 0,
                      report_note TEXT NULL,
                      PRIMARY KEY (minecraft_uuid, test_key)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """
                            .formatted(config.doneTable()));
        }
    }

    public Optional<Progress> findProgress(UUID uuid) throws SQLException {
        if (!config.mysqlConfigured()) {
            return Optional.empty();
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT enrolled_at, completed_at, total_gold_paid, eligible_count, gold_per_command FROM "
                                + config.progressTable()
                                + " WHERE minecraft_uuid = ? LIMIT 1")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Progress(
                        uuid,
                        rs.getTimestamp("enrolled_at").toInstant(),
                        rs.getTimestamp("completed_at") == null
                                ? null
                                : rs.getTimestamp("completed_at").toInstant(),
                        rs.getDouble("total_gold_paid"),
                        rs.getInt("eligible_count"),
                        rs.getDouble("gold_per_command")));
            }
        }
    }

    public void enroll(UUID uuid, int eligibleCount, double goldPerCommand) throws SQLException {
        if (!config.mysqlConfigured()) {
            return;
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO "
                                + config.progressTable()
                                + " (minecraft_uuid, enrolled_at, eligible_count, gold_per_command) VALUES (?, ?, ?, ?)"
                                + " ON DUPLICATE KEY UPDATE eligible_count = VALUES(eligible_count),"
                                + " gold_per_command = VALUES(gold_per_command)")) {
            ps.setString(1, uuid.toString());
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setInt(3, eligibleCount);
            ps.setDouble(4, goldPerCommand);
            ps.executeUpdate();
        }
    }

    public Set<String> completedKeys(UUID uuid) throws SQLException {
        Set<String> keys = new HashSet<>();
        if (!config.mysqlConfigured()) {
            return keys;
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT test_key FROM " + config.doneTable() + " WHERE minecraft_uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    keys.add(rs.getString("test_key"));
                }
            }
        }
        return keys;
    }

    public boolean markDone(UUID uuid, String testKey, double goldPaid, double feeRefunded) throws SQLException {
        if (!config.mysqlConfigured()) {
            return false;
        }
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO "
                            + config.doneTable()
                            + " (minecraft_uuid, test_key, tested_at, gold_paid, fee_refunded)"
                            + " VALUES (?, ?, ?, ?, ?)")) {
                ins.setString(1, uuid.toString());
                ins.setString(2, testKey);
                ins.setTimestamp(3, Timestamp.from(Instant.now()));
                ins.setDouble(4, goldPaid);
                ins.setDouble(5, feeRefunded);
                ins.executeUpdate();
            } catch (SQLException ex) {
                c.rollback();
                if (ex.getMessage() != null && ex.getMessage().toLowerCase(Locale.ROOT).contains("duplicate")) {
                    return false;
                }
                throw ex;
            }
            try (PreparedStatement upd = c.prepareStatement(
                    "UPDATE "
                            + config.progressTable()
                            + " SET total_gold_paid = total_gold_paid + ? WHERE minecraft_uuid = ?")) {
                upd.setDouble(1, goldPaid + feeRefunded);
                upd.setString(2, uuid.toString());
                upd.executeUpdate();
            }
            c.commit();
            return true;
        }
    }

    public void markCompleted(UUID uuid) throws SQLException {
        if (!config.mysqlConfigured()) {
            return;
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "UPDATE "
                                + config.progressTable()
                                + " SET completed_at = ? WHERE minecraft_uuid = ? AND completed_at IS NULL")) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
    }

    public void saveReport(UUID uuid, String testKey, String note) throws SQLException {
        if (!config.mysqlConfigured()) {
            return;
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "UPDATE "
                                + config.doneTable()
                                + " SET report_note = ? WHERE minecraft_uuid = ? AND test_key = ?")) {
            ps.setString(1, note);
            ps.setString(2, uuid.toString());
            ps.setString(3, testKey);
            ps.executeUpdate();
        }
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(config.jdbcUrl(), config.mysqlUsername(), config.mysqlPassword());
    }
}
