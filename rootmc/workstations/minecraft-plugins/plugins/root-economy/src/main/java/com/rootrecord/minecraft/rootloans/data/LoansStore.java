package com.rootrecord.minecraft.rootloans.data;

import com.rootrecord.minecraft.rootloans.config.LoansConfig;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class LoansStore {

    private final LoansConfig config;

    public LoansStore(LoansConfig config) {
        this.config = config;
    }

    public void initSchema() throws SQLException {
        if (!config.mysqlEnabled()) {
            return;
        }
        try (Connection c = open(); Statement st = c.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      minecraft_uuid CHAR(36) NOT NULL,
                      minecraft_username VARCHAR(32) NOT NULL,
                      principal DOUBLE NOT NULL,
                      amount_owed DOUBLE NOT NULL,
                      taken_at DATETIME NOT NULL,
                      INDEX idx_loans_active_uuid (minecraft_uuid)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(config.activeTable()));
            migrateActiveTableToMultiLoan(c);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      minecraft_uuid CHAR(36) PRIMARY KEY,
                      max_loan DOUBLE NOT NULL,
                      successful_repayments INT NOT NULL DEFAULT 0,
                      updated_at DATETIME NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(config.creditTable()));
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      minecraft_uuid CHAR(36) NOT NULL,
                      taken_at DATETIME NOT NULL,
                      INDEX idx_loans_take_uuid_time (minecraft_uuid, taken_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(config.takeLogTable()));
        }
    }

    /** Upgrades legacy UUID-PK active table to id-PK (multiple loans per player). */
    private void migrateActiveTableToMultiLoan(Connection c) throws SQLException {
        String table = config.activeTable();
        if (hasColumn(c, table, "id")) {
            ensureUuidIndex(c, table);
            return;
        }
        try (Statement st = c.createStatement()) {
            st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT UNIQUE FIRST");
            try {
                st.executeUpdate("ALTER TABLE " + table + " DROP PRIMARY KEY");
            } catch (SQLException ignored) {
                // already dropped or never uuid-pk
            }
            st.executeUpdate("ALTER TABLE " + table + " ADD PRIMARY KEY (id)");
            ensureUuidIndex(c, table);
        }
    }

    private static void ensureUuidIndex(Connection c, String table) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("CREATE INDEX idx_loans_active_uuid ON " + table + " (minecraft_uuid)");
        } catch (SQLException ignored) {
            // index already exists
        }
    }

    private static boolean hasColumn(Connection c, String table, String column) throws SQLException {
        DatabaseMetaData meta = c.getMetaData();
        String catalog = c.getCatalog();
        try (ResultSet rs = meta.getColumns(catalog, null, table, column)) {
            if (rs.next()) {
                return true;
            }
        }
        // Some drivers need bare table name without case sensitivity
        try (ResultSet rs = meta.getColumns(catalog, null, table.toLowerCase(), column)) {
            return rs.next();
        }
    }

    /** Highest-balance active loan, if any. */
    public Optional<ActiveLoan> findActive(UUID uuid) throws SQLException {
        List<ActiveLoan> all = findActiveAll(uuid);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    /** Active loans for a player, highest amount_owed first. */
    public List<ActiveLoan> findActiveAll(UUID uuid) throws SQLException {
        List<ActiveLoan> rows = new ArrayList<>();
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT id, minecraft_username, principal, amount_owed, taken_at FROM "
                                + config.activeTable()
                                + " WHERE minecraft_uuid = ? ORDER BY amount_owed DESC, id ASC")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(readLoan(uuid, rs));
                }
            }
        }
        return rows;
    }

    public int countActive(UUID uuid) throws SQLException {
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM " + config.activeTable() + " WHERE minecraft_uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public double sumOwed(UUID uuid) throws SQLException {
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT COALESCE(SUM(amount_owed), 0) FROM " + config.activeTable()
                                + " WHERE minecraft_uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        }
    }

    public int countTakesInRolling24h(UUID uuid) throws SQLException {
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM " + config.takeLogTable()
                                + " WHERE minecraft_uuid = ? AND taken_at >= ?")) {
            ps.setString(1, uuid.toString());
            ps.setTimestamp(2, Timestamp.from(cutoff));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public Optional<Instant> oldestTakeInRolling24h(UUID uuid) throws SQLException {
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT taken_at FROM " + config.takeLogTable()
                                + " WHERE minecraft_uuid = ? AND taken_at >= ? ORDER BY taken_at ASC LIMIT 1")) {
            ps.setString(1, uuid.toString());
            ps.setTimestamp(2, Timestamp.from(cutoff));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getTimestamp(1).toInstant());
                }
            }
        }
        return Optional.empty();
    }

    public void createLoan(UUID uuid, String username, double principal, double amountOwed) throws SQLException {
        Instant now = Instant.now();
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO " + config.activeTable()
                                + " (minecraft_uuid, minecraft_username, principal, amount_owed, taken_at) "
                                + "VALUES (?, ?, ?, ?, ?)")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, username);
                    ps.setDouble(3, principal);
                    ps.setDouble(4, amountOwed);
                    ps.setTimestamp(5, Timestamp.from(now));
                    ps.executeUpdate();
                }
                try (PreparedStatement log = c.prepareStatement(
                        "INSERT INTO " + config.takeLogTable() + " (minecraft_uuid, taken_at) VALUES (?, ?)")) {
                    log.setString(1, uuid.toString());
                    log.setTimestamp(2, Timestamp.from(now));
                    log.executeUpdate();
                }
                ensureCreditRow(c, uuid);
                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * Applies payment across active loans, highest balance first.
     * Returns how much was applied and total remaining owed.
     */
    public RepayResult repay(UUID uuid, String username, double payment) throws SQLException {
        if (payment <= 0) {
            return new RepayResult(0, 0, Optional.empty());
        }
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                List<ActiveLoan> loans = new ArrayList<>();
                try (PreparedStatement s = c.prepareStatement(
                        "SELECT id, minecraft_username, principal, amount_owed, taken_at FROM "
                                + config.activeTable()
                                + " WHERE minecraft_uuid = ? ORDER BY amount_owed DESC, id ASC FOR UPDATE")) {
                    s.setString(1, uuid.toString());
                    try (ResultSet rs = s.executeQuery()) {
                        while (rs.next()) {
                            loans.add(readLoan(uuid, rs));
                        }
                    }
                }
                if (loans.isEmpty()) {
                    c.rollback();
                    return new RepayResult(0, 0, Optional.empty());
                }
                double remainingPayment = payment;
                double appliedTotal = 0;
                int paidOffCount = 0;
                for (ActiveLoan loan : loans) {
                    if (remainingPayment <= 0.0001d) {
                        break;
                    }
                    double chunk = Math.min(remainingPayment, loan.amountOwed());
                    if (chunk <= 0) {
                        continue;
                    }
                    double remaining = loan.amountOwed() - chunk;
                    if (remaining <= 0.0001d) {
                        try (PreparedStatement del = c.prepareStatement(
                                "DELETE FROM " + config.activeTable() + " WHERE id = ?")) {
                            del.setLong(1, loan.id());
                            del.executeUpdate();
                        }
                        paidOffCount++;
                    } else {
                        try (PreparedStatement u = c.prepareStatement(
                                "UPDATE " + config.activeTable()
                                        + " SET amount_owed = ?, minecraft_username = ? WHERE id = ?")) {
                            u.setDouble(1, remaining);
                            u.setString(2, username);
                            u.setLong(3, loan.id());
                            u.executeUpdate();
                        }
                    }
                    appliedTotal += chunk;
                    remainingPayment -= chunk;
                }
                if (paidOffCount > 0) {
                    for (int i = 0; i < paidOffCount; i++) {
                        recordSuccessfulRepayment(c, uuid);
                    }
                }
                double remainingTotal = 0;
                try (PreparedStatement sum = c.prepareStatement(
                        "SELECT COALESCE(SUM(amount_owed), 0) FROM " + config.activeTable()
                                + " WHERE minecraft_uuid = ?")) {
                    sum.setString(1, uuid.toString());
                    try (ResultSet rs = sum.executeQuery()) {
                        if (rs.next()) {
                            remainingTotal = rs.getDouble(1);
                        }
                    }
                }
                c.commit();
                Optional<PayoffResult> payoff = remainingTotal <= 0.0001d
                        ? Optional.of(PayoffResult.PAID_OFF)
                        : Optional.empty();
                return new RepayResult(appliedTotal, remainingTotal, payoff);
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public void accrueDailyTax(long loanId, double taxAmount) throws SQLException {
        if (taxAmount <= 0) {
            return;
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "UPDATE " + config.activeTable()
                                + " SET amount_owed = amount_owed + ? WHERE id = ?")) {
            ps.setDouble(1, taxAmount);
            ps.setLong(2, loanId);
            ps.executeUpdate();
        }
    }

    public List<ActiveLoan> listActive() throws SQLException {
        List<ActiveLoan> rows = new ArrayList<>();
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT id, minecraft_uuid, minecraft_username, principal, amount_owed, taken_at FROM "
                                + config.activeTable() + " ORDER BY amount_owed DESC, id ASC");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new ActiveLoan(
                        rs.getLong("id"),
                        UUID.fromString(rs.getString("minecraft_uuid")),
                        rs.getString("minecraft_username"),
                        rs.getDouble("principal"),
                        rs.getDouble("amount_owed"),
                        rs.getTimestamp("taken_at").toInstant()));
            }
        }
        return rows;
    }

    private static ActiveLoan readLoan(UUID uuid, ResultSet rs) throws SQLException {
        return new ActiveLoan(
                rs.getLong("id"),
                uuid,
                rs.getString("minecraft_username"),
                rs.getDouble("principal"),
                rs.getDouble("amount_owed"),
                rs.getTimestamp("taken_at").toInstant());
    }

    private void recordSuccessfulRepayment(Connection c, UUID uuid) throws SQLException {
        ensureCreditRow(c, uuid);
        try (PreparedStatement u = c.prepareStatement(
                "UPDATE " + config.creditTable()
                        + " SET successful_repayments = successful_repayments + 1, updated_at = ? "
                        + "WHERE minecraft_uuid = ?")) {
            u.setTimestamp(1, Timestamp.from(Instant.now()));
            u.setString(2, uuid.toString());
            u.executeUpdate();
        }
    }

    private void ensureCreditRow(Connection c, UUID uuid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + config.creditTable()
                        + " (minecraft_uuid, max_loan, successful_repayments, updated_at) VALUES (?, ?, 0, ?) "
                        + "ON DUPLICATE KEY UPDATE minecraft_uuid = minecraft_uuid")) {
            ps.setString(1, uuid.toString());
            ps.setDouble(2, config.defaultMaxLoan());
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
    }

    private Connection open() throws SQLException {
        if (!config.mysqlEnabled()) {
            throw new SQLException("MySQL disabled in root-loans.yml");
        }
        String host = config.mysqlHost();
        if (host.isBlank()) {
            throw new SQLException("mysql.host not configured in root-loans.yml");
        }
        String url = "jdbc:mysql://" + host + ":" + config.mysqlPort() + "/" + config.mysqlDatabase()
                + "?" + config.mysqlJdbcParams();
        return DriverManager.getConnection(url, config.mysqlUsername(), config.mysqlPassword());
    }

    public record ActiveLoan(
            long id, UUID uuid, String username, double principal, double amountOwed, Instant takenAt) {}

    public record RepayResult(double applied, double remainingTotal, Optional<PayoffResult> payoff) {
        public RepayResult(double applied, Optional<PayoffResult> payoff) {
            this(applied, 0, payoff);
        }
    }

    public record PayoffResult(boolean paidOff) {
        public static final PayoffResult PAID_OFF = new PayoffResult(true);
    }
}
