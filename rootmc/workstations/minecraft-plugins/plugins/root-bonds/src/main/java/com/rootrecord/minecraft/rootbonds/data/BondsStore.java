package com.rootrecord.minecraft.rootbonds.data;

import com.rootrecord.minecraft.rootbonds.config.BondsConfig;
import com.rootrecord.minecraft.rootbonds.service.BondPrincipalResolver;

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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class BondsStore {

    private final BondsConfig config;

    public BondsStore(BondsConfig config) {
        this.config = config;
    }

    public void initSchema() throws SQLException {
        if (!config.mysqlEnabled()) {
            return;
        }
        try (Connection c = open(); Statement st = c.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      id CHAR(36) PRIMARY KEY,
                      owner_uuid CHAR(36) NOT NULL,
                      owner_name VARCHAR(32) NOT NULL,
                      display_name VARCHAR(64) NOT NULL,
                      principal DOUBLE NOT NULL,
                      issued_at DATETIME NOT NULL,
                      redeemed_at DATETIME NULL,
                      INDEX idx_bonds_owner (owner_uuid),
                      INDEX idx_bonds_active (redeemed_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(config.bondsTable()));
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      owner_uuid CHAR(36) PRIMARY KEY,
                      accrued_g DOUBLE NOT NULL DEFAULT 0,
                      lifetime_earned_g DOUBLE NOT NULL DEFAULT 0,
                      updated_at DATETIME NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(config.accruedTable()));
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      mc_day_id BIGINT PRIMARY KEY,
                      gross_inflow_g DOUBLE NOT NULL DEFAULT 0
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(config.dayInflowTable()));
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      mc_day_id BIGINT PRIMARY KEY,
                      gross_inflow_g DOUBLE NOT NULL,
                      bond_pool_g DOUBLE NOT NULL,
                      total_principal_g DOUBLE NOT NULL,
                      active_bonds INT NOT NULL,
                      settled_at DATETIME NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(config.dailyTable()));
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      mc_day_id BIGINT NOT NULL,
                      owner_uuid CHAR(36) NOT NULL,
                      owner_name VARCHAR(32) NOT NULL,
                      amount_g DOUBLE NOT NULL,
                      weight_pct DOUBLE NOT NULL,
                      principal_g DOUBLE NOT NULL,
                      settled_at DATETIME NOT NULL,
                      INDEX idx_bonds_payout_day (mc_day_id),
                      INDEX idx_bonds_payout_owner (owner_uuid, settled_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(config.dailyPayoutTable()));
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      owner_uuid CHAR(36) NOT NULL,
                      amount_g DOUBLE NOT NULL,
                      remaining_g DOUBLE NOT NULL,
                      accrued_at DATETIME NOT NULL,
                      expires_at DATETIME NOT NULL,
                      forfeited_at DATETIME NULL,
                      INDEX idx_bonds_coupon_owner (owner_uuid),
                      INDEX idx_bonds_coupon_expires (expires_at, forfeited_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(config.couponLotsTable()));
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      leader_uuid CHAR(36) NOT NULL,
                      kind VARCHAR(8) NOT NULL,
                      display_name VARCHAR(64) NOT NULL,
                      amount_g DOUBLE NOT NULL DEFAULT 0,
                      updated_at DATETIME NOT NULL,
                      PRIMARY KEY (leader_uuid, kind, display_name)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(config.notifyPendingTable()));
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS %s (
                      account_uuid CHAR(36) PRIMARY KEY,
                      kind VARCHAR(8) NOT NULL,
                      display_name VARCHAR(64) NOT NULL,
                      auto_bond_enabled TINYINT(1) NOT NULL DEFAULT 1,
                      updated_at DATETIME NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """.formatted(config.governmentSettingsTable()));
            migrateLegacyCouponLots(st);
        }
    }

    private void migrateLegacyCouponLots(Statement st) throws SQLException {
        st.executeUpdate("""
                INSERT INTO %s (owner_uuid, amount_g, remaining_g, accrued_at, expires_at)
                SELECT a.owner_uuid, a.accrued_g, a.accrued_g, a.updated_at,
                       DATE_ADD(UTC_TIMESTAMP(), INTERVAL %d HOUR)
                FROM %s a
                WHERE a.accrued_g > 0
                  AND NOT EXISTS (
                    SELECT 1 FROM %s l WHERE l.owner_uuid = a.owner_uuid LIMIT 1
                  )
                """.formatted(
                config.couponLotsTable(),
                config.claimExpiryHours(),
                config.accruedTable(),
                config.couponLotsTable()));
    }

    public record BondRow(
            UUID id,
            UUID ownerUuid,
            String ownerName,
            String displayName,
            double principal,
            Instant issuedAt) {}

    public record PendingGovNotifyRow(String kind, String displayName, double amountG) {}

    public record AccruedRow(double accruedG, double lifetimeEarnedG, Instant nextClaimDeadline) {}

    public record MarketTotals(double totalPrincipal, int activeCount) {}

    /**
     * Realized average yield from settled MC days: each day {@code bond_pool / total_principal},
     * then averaged. This is the historical return rate per 1 G of principal per MC day.
     */
    public record YieldStats(double avgYieldPctPerMcDay, double avgGPerGPerMcDay, int sampleDays) {
        public static YieldStats empty() {
            return new YieldStats(0, 0, 0);
        }

        public boolean hasData() {
            return sampleDays > 0;
        }
    }

    public record DailySettlementRow(
            long mcDayId,
            double grossInflowG,
            double bondPoolG,
            double totalPrincipalG,
            int activeBonds,
            Instant settledAt) {}

    public record DailyPayoutRow(
            long mcDayId,
            UUID ownerUuid,
            String ownerName,
            double amountG,
            double weightPct,
            double principalG,
            Instant settledAt) {}

    public record PlayerStatsRow(
            UUID ownerUuid,
            String ownerName,
            int activeBonds,
            double principalG,
            double uncollectedG,
            double lifetimeEarnedG,
            double weightPct,
            double avg24hG) {}

    public record SettlementResult(
            DailySettlementRow settlement,
            List<DailyPayoutRow> payouts,
            List<PlayerWalletPayoutRow> playerWalletPayouts,
            List<GovernmentPayoutRow> governmentPayouts) {}

    public record PlayerWalletPayoutRow(UUID ownerUuid, String ownerName, double amountG) {}

    public record GovernmentPayoutRow(
            BondPrincipalResolver.Kind kind,
            UUID accountUuid,
            String accountName,
            String displayName,
            double amountG) {}

    public record GovernmentStatsRow(
            BondPrincipalResolver.Kind kind,
            UUID accountUuid,
            String accountName,
            String displayName,
            double principalG,
            double lifetimeEarnedG,
            double weightPct) {}

    public record ForfeitRow(long lotId, UUID ownerUuid, double amountG) {}

    public void insertBond(BondRow bond) throws SQLException {
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO " + config.bondsTable()
                                + " (id, owner_uuid, owner_name, display_name, principal, issued_at) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, bond.id().toString());
            ps.setString(2, bond.ownerUuid().toString());
            ps.setString(3, bond.ownerName());
            ps.setString(4, bond.displayName());
            ps.setDouble(5, bond.principal());
            ps.setTimestamp(6, Timestamp.from(bond.issuedAt()));
            ps.executeUpdate();
        }
    }

    public Optional<BondRow> findBond(UUID bondId) throws SQLException {
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT owner_uuid, owner_name, display_name, principal, issued_at, redeemed_at FROM "
                                + config.bondsTable() + " WHERE id = ? LIMIT 1")) {
            ps.setString(1, bondId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getTimestamp("redeemed_at") != null) {
                    return Optional.empty();
                }
                return Optional.of(new BondRow(
                        bondId,
                        UUID.fromString(rs.getString("owner_uuid")),
                        rs.getString("owner_name"),
                        rs.getString("display_name"),
                        rs.getDouble("principal"),
                        rs.getTimestamp("issued_at").toInstant()));
            }
        }
    }

    public List<BondRow> listActiveForOwner(UUID ownerUuid) throws SQLException {
        List<BondRow> out = new ArrayList<>();
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT id, owner_uuid, owner_name, display_name, principal, issued_at FROM "
                                + config.bondsTable()
                                + " WHERE owner_uuid = ? AND redeemed_at IS NULL ORDER BY issued_at ASC")) {
            ps.setString(1, ownerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(readBondRow(rs));
                }
            }
        }
        return out;
    }

    public List<BondRow> listAllActive() throws SQLException {
        List<BondRow> out = new ArrayList<>();
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT id, owner_uuid, owner_name, display_name, principal, issued_at FROM "
                                + config.bondsTable()
                                + " WHERE redeemed_at IS NULL ORDER BY issued_at ASC")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(readBondRow(rs));
                }
            }
        }
        return out;
    }

    private static BondRow readBondRow(ResultSet rs) throws SQLException {
        return new BondRow(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("owner_name"),
                rs.getString("display_name"),
                rs.getDouble("principal"),
                rs.getTimestamp("issued_at").toInstant());
    }

    public MarketTotals marketTotals() throws SQLException {
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT COALESCE(SUM(principal),0), COUNT(*) FROM "
                                + config.bondsTable() + " WHERE redeemed_at IS NULL")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new MarketTotals(rs.getDouble(1), rs.getInt(2));
            }
        }
    }

    public double ownerPrincipal(UUID ownerUuid) throws SQLException {
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT COALESCE(SUM(principal),0) FROM "
                                + config.bondsTable()
                                + " WHERE owner_uuid = ? AND redeemed_at IS NULL")) {
            ps.setString(1, ownerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getDouble(1);
            }
        }
    }

    public void markRedeemed(UUID bondId) throws SQLException {
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "UPDATE " + config.bondsTable() + " SET redeemed_at = ? WHERE id = ? AND redeemed_at IS NULL")) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setString(2, bondId.toString());
            ps.executeUpdate();
        }
    }

    /** @return true when ownership changed */
    public boolean transferOwner(UUID bondId, UUID newOwnerUuid, String newOwnerName) throws SQLException {
        if (bondId == null || newOwnerUuid == null || newOwnerName == null || newOwnerName.isBlank()) {
            return false;
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "UPDATE " + config.bondsTable()
                                + " SET owner_uuid = ?, owner_name = ? WHERE id = ? AND redeemed_at IS NULL AND owner_uuid <> ?")) {
            ps.setString(1, newOwnerUuid.toString());
            ps.setString(2, newOwnerName.trim());
            ps.setString(3, bondId.toString());
            ps.setString(4, newOwnerUuid.toString());
            return ps.executeUpdate() > 0;
        }
    }

    public AccruedRow accrued(UUID ownerUuid) throws SQLException {
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT accrued_g, lifetime_earned_g FROM "
                                + config.accruedTable() + " WHERE owner_uuid = ? LIMIT 1")) {
            ps.setString(1, ownerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                double accrued = 0;
                double lifetime = 0;
                if (rs.next()) {
                    accrued = rs.getDouble("accrued_g");
                    lifetime = rs.getDouble("lifetime_earned_g");
                }
                return new AccruedRow(accrued, lifetime, nextClaimDeadline(c, ownerUuid));
            }
        }
    }

    private Instant nextClaimDeadline(Connection c, UUID ownerUuid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT MIN(expires_at) FROM " + config.couponLotsTable()
                        + " WHERE owner_uuid = ? AND remaining_g > 0 AND forfeited_at IS NULL AND expires_at > UTC_TIMESTAMP()")) {
            ps.setString(1, ownerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getTimestamp(1) == null) {
                    return null;
                }
                return rs.getTimestamp(1).toInstant();
            }
        }
    }

    public void addCouponLot(UUID ownerUuid, double amount, int claimExpiryHours) throws SQLException {
        if (amount <= 0) {
            return;
        }
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(claimExpiryHours * 3600L);
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                addCouponLot(c, ownerUuid, amount, now, expires);
                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /** @deprecated use {@link #addCouponLot(UUID, double, int)} */
    public void addAccrued(UUID ownerUuid, double delta) throws SQLException {
        addCouponLot(ownerUuid, delta, config.claimExpiryHours());
    }

    public boolean takeAccrued(UUID ownerUuid, double amount) throws SQLException {
        if (amount <= 0) {
            return true;
        }
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                double taken = takeFromCouponLots(c, ownerUuid, amount);
                if (taken + 1e-9 < amount) {
                    c.rollback();
                    return false;
                }
                bumpLifetimeEarned(c, ownerUuid, taken);
                c.commit();
                return true;
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    public List<ForfeitRow> forfeitExpiredLots() throws SQLException {
        List<ForfeitRow> out = new ArrayList<>();
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT id, owner_uuid, remaining_g FROM " + config.couponLotsTable()
                                + " WHERE remaining_g > 0 AND forfeited_at IS NULL AND expires_at <= UTC_TIMESTAMP() FOR UPDATE")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            long lotId = rs.getLong("id");
                            UUID owner = UUID.fromString(rs.getString("owner_uuid"));
                            double remaining = rs.getDouble("remaining_g");
                            if (remaining <= 0) {
                                continue;
                            }
                            try (PreparedStatement mark = c.prepareStatement(
                                    "UPDATE " + config.couponLotsTable()
                                            + " SET remaining_g = 0, forfeited_at = UTC_TIMESTAMP() WHERE id = ?")) {
                                mark.setLong(1, lotId);
                                mark.executeUpdate();
                            }
                            reduceAccrued(c, owner, remaining);
                            out.add(new ForfeitRow(lotId, owner, remaining));
                        }
                    }
                }
                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
        return out;
    }

    public boolean hasClaimableCoupons(UUID ownerUuid) throws SQLException {
        if (ownerUuid == null) {
            return false;
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) AS c FROM " + config.couponLotsTable()
                                + " WHERE owner_uuid = ? AND remaining_g > 0 AND forfeited_at IS NULL"
                                + " AND expires_at > UTC_TIMESTAMP()")) {
            ps.setString(1, ownerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt("c") > 0;
            }
        }
    }

    /** Backdate last login so Root-Upkeep inactivity tax applies on the next real-day cycle. */
    public void beginInactivityTaxWindow(UUID ownerUuid, int graceDays) throws SQLException {
        if (ownerUuid == null) {
            return;
        }
        int idleDays = Math.max(1, graceDays + 1);
        String table = config.playtimeTable();
        try (Connection c = open()) {
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE " + table
                                + " SET last_login_at = DATE_SUB(UTC_TIMESTAMP(), INTERVAL ? DAY),"
                                + " updated_at = UTC_TIMESTAMP() WHERE uuid = ? AND scope = '*'")) {
                    ps.setInt(1, idleDays);
                    ps.setString(2, ownerUuid.toString());
                    ps.executeUpdate();
                }
            } catch (SQLException ex) {
                if (!isUnknownColumn(ex, "scope")) {
                    throw ex;
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE " + table
                                + " SET last_login_at = DATE_SUB(UTC_TIMESTAMP(), INTERVAL ? DAY),"
                                + " updated_at = UTC_TIMESTAMP() WHERE uuid = ?")) {
                    ps.setInt(1, idleDays);
                    ps.setString(2, ownerUuid.toString());
                    ps.executeUpdate();
                }
            }
        }
    }

    private static boolean isUnknownColumn(SQLException ex, String column) {
        String msg = ex.getMessage();
        return msg != null
                && msg.toLowerCase().contains("unknown column")
                && msg.toLowerCase().contains(column.toLowerCase());
    }

    public List<UUID> listActiveBondOwnerUuids() throws SQLException {
        List<UUID> out = new ArrayList<>();
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT DISTINCT owner_uuid FROM " + config.bondsTable() + " WHERE redeemed_at IS NULL")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(UUID.fromString(rs.getString("owner_uuid")));
                }
            }
        }
        return out;
    }

    public Instant lastLoginAt(UUID ownerUuid) throws SQLException {
        if (ownerUuid == null) {
            return null;
        }
        String table = config.playtimeTable();
        try (Connection c = open()) {
            try {
                return queryLastLogin(c, "SELECT last_login_at FROM " + table
                        + " WHERE uuid = ? AND scope = '*' LIMIT 1", ownerUuid);
            } catch (SQLException ex) {
                if (!isUnknownColumn(ex, "scope")) {
                    throw ex;
                }
                return queryLastLogin(c, "SELECT last_login_at FROM " + table
                        + " WHERE uuid = ? LIMIT 1", ownerUuid);
            }
        }
    }

    private static Instant queryLastLogin(Connection c, String sql, UUID ownerUuid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ownerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp ts = rs.getTimestamp("last_login_at");
                    return ts == null ? null : ts.toInstant();
                }
            }
        }
        return null;
    }

    public boolean hasBondPosition(UUID ownerUuid) throws SQLException {
        if (ownerUuid == null) {
            return false;
        }
        if (ownerPrincipal(ownerUuid) >= com.rootrecord.minecraft.common.GoldMoney.MIN_AMOUNT) {
            return true;
        }
        AccruedRow accrued = accrued(ownerUuid);
        return accrued != null && accrued.accruedG() >= com.rootrecord.minecraft.common.GoldMoney.MIN_AMOUNT;
    }

    /** Forfeits legacy coupon lots + accrued balance for one owner; returns total G returned to reserve. */
    public double forfeitAllUnclaimedForOwner(UUID ownerUuid) throws SQLException {
        if (ownerUuid == null) {
            return 0;
        }
        double total = 0;
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT id, remaining_g FROM " + config.couponLotsTable()
                                + " WHERE owner_uuid = ? AND remaining_g > 0 AND forfeited_at IS NULL FOR UPDATE")) {
                    ps.setString(1, ownerUuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            long lotId = rs.getLong("id");
                            double remaining = rs.getDouble("remaining_g");
                            if (remaining <= 0) {
                                continue;
                            }
                            try (PreparedStatement mark = c.prepareStatement(
                                    "UPDATE " + config.couponLotsTable()
                                            + " SET remaining_g = 0, forfeited_at = UTC_TIMESTAMP() WHERE id = ?")) {
                                mark.setLong(1, lotId);
                                mark.executeUpdate();
                            }
                            total += remaining;
                        }
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT accrued_g FROM " + config.accruedTable() + " WHERE owner_uuid = ? FOR UPDATE")) {
                    ps.setString(1, ownerUuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            double accruedLeft = rs.getDouble("accrued_g");
                            if (accruedLeft > 0) {
                                total += accruedLeft;
                                reduceAccrued(c, ownerUuid, accruedLeft);
                            }
                        }
                    }
                }
                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
        return com.rootrecord.minecraft.common.GoldMoney.round(total);
    }

    private void ensureAccruedRow(Connection c, UUID ownerUuid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT IGNORE INTO " + config.accruedTable() + " (owner_uuid, accrued_g, lifetime_earned_g, updated_at) VALUES (?,?,0,?)")) {
            ps.setString(1, ownerUuid.toString());
            ps.setDouble(2, 0);
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
    }

    /** Accumulates a government bond deposit for an offline leader — delivered on next login. */
    public void addPendingGovNotify(UUID leaderUuid, String kind, String displayName, double amountG) throws SQLException {
        if (leaderUuid == null || amountG <= 0) {
            return;
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO " + config.notifyPendingTable()
                                + " (leader_uuid, kind, display_name, amount_g, updated_at) VALUES (?,?,?,?,?)"
                                + " ON DUPLICATE KEY UPDATE amount_g = amount_g + VALUES(amount_g), updated_at = VALUES(updated_at)")) {
            ps.setString(1, leaderUuid.toString());
            ps.setString(2, kind);
            ps.setString(3, displayName);
            ps.setDouble(4, amountG);
            ps.setTimestamp(5, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
    }

    /** Reads and clears pending government deposit notifications for a leader. */
    public List<PendingGovNotifyRow> takePendingGovNotify(UUID leaderUuid) throws SQLException {
        List<PendingGovNotifyRow> out = new ArrayList<>();
        if (leaderUuid == null) {
            return out;
        }
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT kind, display_name, amount_g FROM " + config.notifyPendingTable()
                                + " WHERE leader_uuid = ? FOR UPDATE")) {
                    ps.setString(1, leaderUuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            double amount = rs.getDouble("amount_g");
                            if (amount > 0) {
                                out.add(new PendingGovNotifyRow(
                                        rs.getString("kind"),
                                        rs.getString("display_name"),
                                        amount));
                            }
                        }
                    }
                }
                try (PreparedStatement del = c.prepareStatement(
                        "DELETE FROM " + config.notifyPendingTable() + " WHERE leader_uuid = ?")) {
                    del.setString(1, leaderUuid.toString());
                    del.executeUpdate();
                }
                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
        return out;
    }

    public boolean isGovernmentAutoBondEnabled(UUID accountUuid, boolean defaultEnabled) throws SQLException {
        if (accountUuid == null) {
            return defaultEnabled;
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT auto_bond_enabled FROM " + config.governmentSettingsTable()
                                + " WHERE account_uuid = ? LIMIT 1")) {
            ps.setString(1, accountUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return defaultEnabled;
                }
                return rs.getInt("auto_bond_enabled") != 0;
            }
        }
    }

    public void setGovernmentAutoBondEnabled(
            UUID accountUuid,
            String kind,
            String displayName,
            boolean enabled) throws SQLException {
        if (accountUuid == null || kind == null || displayName == null) {
            return;
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO " + config.governmentSettingsTable()
                                + " (account_uuid, kind, display_name, auto_bond_enabled, updated_at)"
                                + " VALUES (?,?,?,?,?)"
                                + " ON DUPLICATE KEY UPDATE kind = VALUES(kind), display_name = VALUES(display_name),"
                                + " auto_bond_enabled = VALUES(auto_bond_enabled), updated_at = VALUES(updated_at)")) {
            ps.setString(1, accountUuid.toString());
            ps.setString(2, kind);
            ps.setString(3, displayName);
            ps.setInt(4, enabled ? 1 : 0);
            ps.setTimestamp(5, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
    }

    public void addDayInflow(long mcDayId, double amount) throws SQLException {
        if (amount <= 0 || mcDayId < 0) {
            return;
        }
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO " + config.dayInflowTable()
                                + " (mc_day_id, gross_inflow_g) VALUES (?, ?) ON DUPLICATE KEY UPDATE gross_inflow_g = gross_inflow_g + VALUES(gross_inflow_g)")) {
            ps.setLong(1, mcDayId);
            ps.setDouble(2, amount);
            ps.executeUpdate();
        }
    }

    public boolean isDaySettled(long mcDayId) throws SQLException {
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT 1 FROM " + config.dailyTable() + " WHERE mc_day_id = ? LIMIT 1")) {
            ps.setLong(1, mcDayId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Highest MC day id recorded in daily settlements, or {@code -1} if none. */
    public long maxSettledMcDayId() throws SQLException {
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT COALESCE(MAX(mc_day_id), -1) FROM " + config.dailyTable())) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1L;
            }
        }
    }

    /**
     * Mark a closed MC-day range as settled with zero payouts so catch-up does not replay
     * thousands of days after long downtime / table gaps.
     *
     * @return rows inserted (already-settled days skipped via INSERT IGNORE)
     */
    public int markDaysSkipped(long fromInclusive, long toInclusive) throws SQLException {
        if (toInclusive < fromInclusive) {
            return 0;
        }
        Timestamp now = Timestamp.from(Instant.now());
        int inserted = 0;
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT IGNORE INTO " + config.dailyTable()
                                + " (mc_day_id, gross_inflow_g, bond_pool_g, total_principal_g, active_bonds, settled_at)"
                                + " VALUES (?,?,?,?,?,?)")) {
            for (long day = fromInclusive; day <= toInclusive; day++) {
                ps.setLong(1, day);
                ps.setDouble(2, 0);
                ps.setDouble(3, 0);
                ps.setDouble(4, 0);
                ps.setInt(5, 0);
                ps.setTimestamp(6, now);
                ps.addBatch();
                if ((day - fromInclusive + 1) % 500 == 0) {
                    inserted += sumBatch(ps.executeBatch());
                }
            }
            inserted += sumBatch(ps.executeBatch());
        }
        return inserted;
    }

    private static int sumBatch(int[] counts) {
        int n = 0;
        for (int c : counts) {
            if (c > 0) {
                n += c;
            }
        }
        return n;
    }

    public double dayGrossInflow(long mcDayId) throws SQLException {
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT gross_inflow_g FROM " + config.dayInflowTable() + " WHERE mc_day_id = ?")) {
            ps.setLong(1, mcDayId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        }
    }

    public Optional<SettlementResult> settleMcDay(
            long mcDayId,
            double incomeShare,
            int claimExpiryHours,
            boolean autoPayEarningsWallet,
            List<BondPrincipalResolver.Holder> principals,
            boolean distributePayouts) throws SQLException {
        if (mcDayId < 0) {
            return Optional.empty();
        }
        if (principals == null) {
            principals = List.of();
        }
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                if (isDaySettled(c, mcDayId)) {
                    c.rollback();
                    return Optional.empty();
                }
                double gross = 0;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT gross_inflow_g FROM " + config.dayInflowTable() + " WHERE mc_day_id = ? FOR UPDATE")) {
                    ps.setLong(1, mcDayId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            gross = rs.getDouble(1);
                        }
                    }
                }
                double totalPrincipal = 0;
                for (BondPrincipalResolver.Holder holder : principals) {
                    totalPrincipal += holder.principalG();
                }
                Instant settledAt = Instant.now();
                Instant couponExpires = settledAt.plusSeconds(claimExpiryHours * 3600L);
                double pool = !distributePayouts || principals.isEmpty() || totalPrincipal <= 0
                        ? 0
                        : com.rootrecord.minecraft.common.GoldMoney.round(gross * incomeShare);
                List<DailyPayoutRow> payouts = new ArrayList<>();
                List<PlayerWalletPayoutRow> playerWalletPayouts = new ArrayList<>();
                List<GovernmentPayoutRow> governmentPayouts = new ArrayList<>();
                if (distributePayouts
                        && pool >= com.rootrecord.minecraft.common.GoldMoney.MIN_AMOUNT
                        && totalPrincipal >= com.rootrecord.minecraft.common.GoldMoney.MIN_AMOUNT) {
                    for (BondPrincipalResolver.Holder holder : principals) {
                        double share = com.rootrecord.minecraft.common.GoldMoney.round(
                                pool * (holder.principalG() / totalPrincipal));
                        if (share < com.rootrecord.minecraft.common.GoldMoney.MIN_AMOUNT) {
                            continue;
                        }
                        double weightPct = totalPrincipal <= 0 ? 0 : (holder.principalG() / totalPrincipal) * 100.0;
                        if (holder.kind() == BondPrincipalResolver.Kind.PLAYER) {
                            if (autoPayEarningsWallet) {
                                bumpLifetimeEarned(c, holder.accountUuid(), share);
                                playerWalletPayouts.add(new PlayerWalletPayoutRow(
                                        holder.accountUuid(),
                                        payoutOwnerName(holder),
                                        share));
                            } else {
                                addCouponLot(c, holder.accountUuid(), share, settledAt, couponExpires);
                            }
                        } else {
                            bumpLifetimeEarned(c, holder.accountUuid(), share);
                            governmentPayouts.add(new GovernmentPayoutRow(
                                    holder.kind(),
                                    holder.accountUuid(),
                                    holder.accountName(),
                                    holder.displayName(),
                                    share));
                        }
                        payouts.add(new DailyPayoutRow(
                                mcDayId,
                                holder.accountUuid(),
                                payoutOwnerName(holder),
                                share,
                                weightPct,
                                holder.principalG(),
                                settledAt));
                        insertDailyPayout(
                                c,
                                mcDayId,
                                holder.accountUuid(),
                                payoutOwnerName(holder),
                                share,
                                weightPct,
                                holder.principalG(),
                                settledAt);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO " + config.dailyTable()
                                + " (mc_day_id, gross_inflow_g, bond_pool_g, total_principal_g, active_bonds, settled_at) VALUES (?,?,?,?,?,?)")) {
                    ps.setLong(1, mcDayId);
                    ps.setDouble(2, gross);
                    ps.setDouble(3, pool);
                    ps.setDouble(4, totalPrincipal);
                    ps.setInt(5, principals.size());
                    ps.setTimestamp(6, Timestamp.from(settledAt));
                    ps.executeUpdate();
                }
                try (PreparedStatement del = c.prepareStatement("DELETE FROM " + config.dayInflowTable() + " WHERE mc_day_id = ?")) {
                    del.setLong(1, mcDayId);
                    del.executeUpdate();
                }
                c.commit();
                return Optional.of(new SettlementResult(
                        new DailySettlementRow(mcDayId, gross, pool, totalPrincipal, principals.size(), settledAt),
                        payouts,
                        playerWalletPayouts,
                        governmentPayouts));
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private static String payoutOwnerName(BondPrincipalResolver.Holder holder) {
        return holder.accountName();
    }

    private boolean isDaySettled(Connection c, long mcDayId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM " + config.dailyTable() + " WHERE mc_day_id = ? LIMIT 1")) {
            ps.setLong(1, mcDayId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private List<BondRow> listAllActive(Connection c) throws SQLException {
        List<BondRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, owner_uuid, owner_name, display_name, principal, issued_at FROM "
                        + config.bondsTable()
                        + " WHERE redeemed_at IS NULL ORDER BY issued_at ASC")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(readBondRow(rs));
                }
            }
        }
        return out;
    }

    private void addCouponLot(Connection c, UUID ownerUuid, double amount, Instant accruedAt, Instant expiresAt) throws SQLException {
        if (amount <= 0) {
            return;
        }
        ensureAccruedRow(c, ownerUuid);
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + config.couponLotsTable()
                        + " (owner_uuid, amount_g, remaining_g, accrued_at, expires_at) VALUES (?,?,?,?,?)")) {
            ps.setString(1, ownerUuid.toString());
            ps.setDouble(2, amount);
            ps.setDouble(3, amount);
            ps.setTimestamp(4, Timestamp.from(accruedAt));
            ps.setTimestamp(5, Timestamp.from(expiresAt));
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE " + config.accruedTable()
                        + " SET accrued_g = accrued_g + ?, updated_at = ? WHERE owner_uuid = ?")) {
            ps.setDouble(1, amount);
            ps.setTimestamp(2, Timestamp.from(accruedAt));
            ps.setString(3, ownerUuid.toString());
            ps.executeUpdate();
        }
    }

    private double takeFromCouponLots(Connection c, UUID ownerUuid, double amount) throws SQLException {
        double remaining = amount;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, remaining_g FROM " + config.couponLotsTable()
                        + " WHERE owner_uuid = ? AND remaining_g > 0 AND forfeited_at IS NULL AND expires_at > UTC_TIMESTAMP()"
                        + " ORDER BY accrued_at ASC, id ASC FOR UPDATE")) {
            ps.setString(1, ownerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next() && remaining > com.rootrecord.minecraft.common.GoldMoney.MIN_AMOUNT - 1e-9) {
                    long lotId = rs.getLong("id");
                    double lotRemaining = rs.getDouble("remaining_g");
                    double take = Math.min(lotRemaining, remaining);
                    if (take <= 0) {
                        continue;
                    }
                    try (PreparedStatement u = c.prepareStatement(
                            "UPDATE " + config.couponLotsTable() + " SET remaining_g = remaining_g - ? WHERE id = ?")) {
                        u.setDouble(1, take);
                        u.setLong(2, lotId);
                        u.executeUpdate();
                    }
                    remaining -= take;
                }
            }
        }
        double claimed = amount - remaining;
        if (claimed > 0) {
            reduceAccrued(c, ownerUuid, claimed);
        }
        return claimed;
    }

    private void reduceAccrued(Connection c, UUID ownerUuid, double amount) throws SQLException {
        if (amount <= 0) {
            return;
        }
        ensureAccruedRow(c, ownerUuid);
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE " + config.accruedTable()
                        + " SET accrued_g = GREATEST(0, accrued_g - ?), updated_at = UTC_TIMESTAMP() WHERE owner_uuid = ?")) {
            ps.setDouble(1, amount);
            ps.setString(2, ownerUuid.toString());
            ps.executeUpdate();
        }
    }

    private void bumpLifetimeEarned(Connection c, UUID ownerUuid, double amount) throws SQLException {
        if (amount <= 0) {
            return;
        }
        ensureAccruedRow(c, ownerUuid);
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE " + config.accruedTable()
                        + " SET lifetime_earned_g = lifetime_earned_g + ?, updated_at = UTC_TIMESTAMP() WHERE owner_uuid = ?")) {
            ps.setDouble(1, amount);
            ps.setString(2, ownerUuid.toString());
            ps.executeUpdate();
        }
    }

    private void addAccrued(Connection c, UUID ownerUuid, double delta) throws SQLException {
        addCouponLot(c, ownerUuid, delta, Instant.now(), Instant.now().plusSeconds(config.claimExpiryHours() * 3600L));
    }

    private void insertDailyPayout(
            Connection c,
            long mcDayId,
            UUID ownerUuid,
            String ownerName,
            double amountG,
            double weightPct,
            double principalG,
            Instant settledAt) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + config.dailyPayoutTable()
                        + " (mc_day_id, owner_uuid, owner_name, amount_g, weight_pct, principal_g, settled_at) VALUES (?,?,?,?,?,?,?)")) {
            ps.setLong(1, mcDayId);
            ps.setString(2, ownerUuid.toString());
            ps.setString(3, ownerName);
            ps.setDouble(4, amountG);
            ps.setDouble(5, weightPct);
            ps.setDouble(6, principalG);
            ps.setTimestamp(7, Timestamp.from(settledAt));
            ps.executeUpdate();
        }
    }

    public List<BondRow> listAllBondsIncludingRedeemed(int limit) throws SQLException {
        List<BondRow> out = new ArrayList<>();
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT id, owner_uuid, owner_name, display_name, principal, issued_at FROM "
                                + config.bondsTable()
                                + " WHERE redeemed_at IS NULL ORDER BY issued_at DESC LIMIT ?")) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(readBondRow(rs));
                }
            }
        }
        return out;
    }

    public List<DailySettlementRow> listRecentDaily(int limit) throws SQLException {
        List<DailySettlementRow> out = new ArrayList<>();
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT mc_day_id, gross_inflow_g, bond_pool_g, total_principal_g, active_bonds, settled_at FROM "
                                + config.dailyTable() + " ORDER BY mc_day_id DESC LIMIT ?")) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new DailySettlementRow(
                            rs.getLong("mc_day_id"),
                            rs.getDouble("gross_inflow_g"),
                            rs.getDouble("bond_pool_g"),
                            rs.getDouble("total_principal_g"),
                            rs.getInt("active_bonds"),
                            rs.getTimestamp("settled_at").toInstant()));
                }
            }
        }
        return out;
    }

    /** Average realized % return per G of principal per MC day over recent settlements. */
    public YieldStats averageDailyYield(int lookbackMcDays) throws SQLException {
        List<DailySettlementRow> days = listRecentDaily(Math.max(1, lookbackMcDays));
        double sumPct = 0;
        int n = 0;
        for (DailySettlementRow day : days) {
            if (day.totalPrincipalG() < com.rootrecord.minecraft.common.GoldMoney.MIN_AMOUNT) {
                continue;
            }
            sumPct += (day.bondPoolG() / day.totalPrincipalG()) * 100.0;
            n++;
        }
        if (n <= 0) {
            return YieldStats.empty();
        }
        double avgPct = sumPct / n;
        return new YieldStats(avgPct, avgPct / 100.0, n);
    }

    public List<DailyPayoutRow> listRecentPayouts(int limit) throws SQLException {
        List<DailyPayoutRow> out = new ArrayList<>();
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT mc_day_id, owner_uuid, owner_name, amount_g, weight_pct, principal_g, settled_at FROM "
                                + config.dailyPayoutTable() + " ORDER BY settled_at DESC LIMIT ?")) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new DailyPayoutRow(
                            rs.getLong("mc_day_id"),
                            UUID.fromString(rs.getString("owner_uuid")),
                            rs.getString("owner_name"),
                            rs.getDouble("amount_g"),
                            rs.getDouble("weight_pct"),
                            rs.getDouble("principal_g"),
                            rs.getTimestamp("settled_at").toInstant()));
                }
            }
        }
        return out;
    }

    public List<PlayerStatsRow> listPlayerStats() throws SQLException {
        return listPlayerStats(marketTotals().totalPrincipal());
    }

    public List<PlayerStatsRow> listPlayerStats(double totalMarketPrincipal) throws SQLException {
        double totalPrincipal = totalMarketPrincipal > 0 ? totalMarketPrincipal : marketTotals().totalPrincipal();
        List<PlayerStatsRow> out = new ArrayList<>();
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        """
                                SELECT b.owner_uuid, MAX(b.owner_name) AS owner_name, COUNT(*) AS active_bonds, COALESCE(SUM(b.principal),0) AS principal_g,
                                       COALESCE(a.accrued_g,0) AS uncollected_g, COALESCE(a.lifetime_earned_g,0) AS lifetime_earned_g
                                FROM %s b
                                LEFT JOIN %s a ON a.owner_uuid = b.owner_uuid
                                WHERE b.redeemed_at IS NULL
                                GROUP BY b.owner_uuid, a.accrued_g, a.lifetime_earned_g
                                ORDER BY principal_g DESC
                                """.formatted(config.bondsTable(), config.accruedTable()))) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID ownerUuid = UUID.fromString(rs.getString("owner_uuid"));
                    double principal = rs.getDouble("principal_g");
                    double weightPct = totalPrincipal <= 0 ? 0 : (principal / totalPrincipal) * 100.0;
                    double avg24h = avgPayoutLastHours(c, ownerUuid, 24);
                    out.add(new PlayerStatsRow(
                            ownerUuid,
                            rs.getString("owner_name"),
                            rs.getInt("active_bonds"),
                            principal,
                            rs.getDouble("uncollected_g"),
                            rs.getDouble("lifetime_earned_g"),
                            weightPct,
                            avg24h));
                }
            }
        }
        return out;
    }

    public List<GovernmentStatsRow> listGovernmentStats(
            List<BondPrincipalResolver.Holder> holders,
            double totalMarketPrincipal) throws SQLException {
        List<GovernmentStatsRow> out = new ArrayList<>();
        double totalPrincipal = totalMarketPrincipal > 0 ? totalMarketPrincipal : 0;
        for (BondPrincipalResolver.Holder holder : holders) {
            if (holder.kind() == BondPrincipalResolver.Kind.PLAYER) {
                continue;
            }
            double lifetime = 0;
            try (Connection c = open();
                    PreparedStatement ps = c.prepareStatement(
                            "SELECT lifetime_earned_g FROM " + config.accruedTable() + " WHERE owner_uuid = ? LIMIT 1")) {
                ps.setString(1, holder.accountUuid().toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        lifetime = rs.getDouble("lifetime_earned_g");
                    }
                }
            }
            double weightPct = totalPrincipal <= 0 ? 0 : (holder.principalG() / totalPrincipal) * 100.0;
            out.add(new GovernmentStatsRow(
                    holder.kind(),
                    holder.accountUuid(),
                    holder.accountName(),
                    holder.displayName(),
                    holder.principalG(),
                    lifetime,
                    weightPct));
        }
        return out;
    }

    private double avgPayoutLastHours(Connection c, UUID ownerUuid, int hours) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(SUM(amount_g),0) FROM " + config.dailyPayoutTable()
                        + " WHERE owner_uuid = ? AND settled_at >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL ? HOUR)")) {
            ps.setString(1, ownerUuid.toString());
            ps.setInt(2, hours);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getDouble(1);
            }
        }
    }

    private Connection open() throws SQLException {
        String url = "jdbc:mysql://" + config.mysqlHost() + ":" + config.mysqlPort() + "/"
                + config.mysqlDatabase();
        if (!config.mysqlJdbcParams().isBlank()) {
            url += "?" + config.mysqlJdbcParams();
        }
        return DriverManager.getConnection(url, config.mysqlUsername(), config.mysqlPassword());
    }
}
