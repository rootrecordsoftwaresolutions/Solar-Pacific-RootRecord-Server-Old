package com.rootrecord.minecraft.rootloans.data;

import com.rootrecord.minecraft.rootloans.config.LoansConfig;
import com.rootrecord.minecraft.rootloans.config.TownLoansConfig;

import java.sql.Connection;
import java.sql.DriverManager;
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

public final class TownLoansStore {

    private final LoansConfig mysql;
    private final TownLoansConfig config;

    public TownLoansStore(LoansConfig mysql, TownLoansConfig config) {
        this.mysql = mysql;
        this.config = config;
    }

    public void initSchema() throws SQLException {
        if (!mysql.mysqlEnabled()) {
            return;
        }
        try (Connection c = open(); Statement st = c.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      town_name VARCHAR(64) PRIMARY KEY,
                      mayor_uuid CHAR(36) NOT NULL,
                      mayor_username VARCHAR(32) NOT NULL,
                      principal DOUBLE NOT NULL,
                      amount_owed DOUBLE NOT NULL,
                      taken_at DATETIME NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(config.activeTable(mysql.mysqlTablePrefix())));
        }
    }

    public Optional<ActiveTownLoan> findByTown(String townName) throws SQLException {
        if (townName == null || townName.isBlank()) {
            return Optional.empty();
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT mayor_uuid, mayor_username, principal, amount_owed, taken_at FROM "
                                + config.activeTable(mysql.mysqlTablePrefix())
                                + " WHERE town_name = ? LIMIT 1")) {
            ps.setString(1, townName.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ActiveTownLoan(
                        townName.trim(),
                        UUID.fromString(rs.getString("mayor_uuid")),
                        rs.getString("mayor_username"),
                        rs.getDouble("principal"),
                        rs.getDouble("amount_owed"),
                        rs.getTimestamp("taken_at").toInstant()));
            }
        }
    }

    public void createLoan(
            String townName,
            UUID mayorUuid,
            String mayorUsername,
            double principal,
            double amountOwed) throws SQLException {
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO "
                                + config.activeTable(mysql.mysqlTablePrefix())
                                + " (town_name, mayor_uuid, mayor_username, principal, amount_owed, taken_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, townName.trim());
            ps.setString(2, mayorUuid.toString());
            ps.setString(3, mayorUsername);
            ps.setDouble(4, principal);
            ps.setDouble(5, amountOwed);
            ps.setTimestamp(6, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
    }

    public RepayResult repay(String townName, String mayorUsername, double payment) throws SQLException {
        if (payment <= 0) {
            return new RepayResult(0, Optional.empty());
        }
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                ActiveTownLoan loan;
                try (PreparedStatement s = c.prepareStatement(
                        "SELECT mayor_uuid, mayor_username, principal, amount_owed, taken_at FROM "
                                + config.activeTable(mysql.mysqlTablePrefix())
                                + " WHERE town_name = ? FOR UPDATE")) {
                    s.setString(1, townName.trim());
                    try (ResultSet rs = s.executeQuery()) {
                        if (!rs.next()) {
                            c.rollback();
                            return new RepayResult(0, Optional.empty());
                        }
                        loan = new ActiveTownLoan(
                                townName.trim(),
                                UUID.fromString(rs.getString("mayor_uuid")),
                                rs.getString("mayor_username"),
                                rs.getDouble("principal"),
                                rs.getDouble("amount_owed"),
                                rs.getTimestamp("taken_at").toInstant());
                    }
                }
                double applied = Math.min(payment, loan.amountOwed());
                double remaining = loan.amountOwed() - applied;
                if (remaining <= 0.0001d) {
                    try (PreparedStatement del = c.prepareStatement(
                            "DELETE FROM "
                                    + config.activeTable(mysql.mysqlTablePrefix())
                                    + " WHERE town_name = ?")) {
                        del.setString(1, townName.trim());
                        del.executeUpdate();
                    }
                    c.commit();
                    return new RepayResult(applied, Optional.of(PayoffResult.PAID_OFF));
                }
                try (PreparedStatement u = c.prepareStatement(
                        "UPDATE "
                                + config.activeTable(mysql.mysqlTablePrefix())
                                + " SET amount_owed = ?, mayor_username = ? WHERE town_name = ?")) {
                    u.setDouble(1, remaining);
                    u.setString(2, mayorUsername);
                    u.setString(3, townName.trim());
                    u.executeUpdate();
                }
                c.commit();
                return new RepayResult(applied, Optional.empty());
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public void accrueDailyTax(String townName, double taxAmount) throws SQLException {
        if (taxAmount <= 0 || townName == null || townName.isBlank()) {
            return;
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "UPDATE "
                                + config.activeTable(mysql.mysqlTablePrefix())
                                + " SET amount_owed = amount_owed + ? WHERE town_name = ?")) {
            ps.setDouble(1, taxAmount);
            ps.setString(2, townName.trim());
            ps.executeUpdate();
        }
    }

    public List<ActiveTownLoan> listActive() throws SQLException {
        List<ActiveTownLoan> rows = new ArrayList<>();
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT town_name, mayor_uuid, mayor_username, principal, amount_owed, taken_at FROM "
                                + config.activeTable(mysql.mysqlTablePrefix())
                                + " ORDER BY amount_owed DESC");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new ActiveTownLoan(
                        rs.getString("town_name"),
                        UUID.fromString(rs.getString("mayor_uuid")),
                        rs.getString("mayor_username"),
                        rs.getDouble("principal"),
                        rs.getDouble("amount_owed"),
                        rs.getTimestamp("taken_at").toInstant()));
            }
        }
        return rows;
    }

    private Connection open() throws SQLException {
        if (!mysql.mysqlEnabled()) {
            throw new SQLException("MySQL disabled in root-loans.yml");
        }
        String host = mysql.mysqlHost();
        if (host.isBlank()) {
            throw new SQLException("mysql.host not configured in root-loans.yml");
        }
        String url = "jdbc:mysql://" + host + ":" + mysql.mysqlPort() + "/" + mysql.mysqlDatabase()
                + "?" + mysql.mysqlJdbcParams();
        return DriverManager.getConnection(url, mysql.mysqlUsername(), mysql.mysqlPassword());
    }

    public record ActiveTownLoan(
            String townName,
            UUID mayorUuid,
            String mayorUsername,
            double principal,
            double amountOwed,
            Instant takenAt) {}

    public record RepayResult(double applied, Optional<PayoffResult> payoff) {}

    public record PayoffResult(boolean paidOff) {
        public static final PayoffResult PAID_OFF = new PayoffResult(true);
    }
}
