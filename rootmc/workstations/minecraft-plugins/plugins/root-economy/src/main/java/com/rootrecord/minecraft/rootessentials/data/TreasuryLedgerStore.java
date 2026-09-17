package com.rootrecord.minecraft.rootessentials.data;


import com.rootrecord.minecraft.common.GoldMoney;

import com.rootrecord.minecraft.common.TreasuryLedgerType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TreasuryLedgerStore {

    private final MySqlSupport db;
    private final String table;

    public TreasuryLedgerStore(MySqlSupport db, String tablePrefix) {
        this.db = db;
        this.table = tablePrefix + "treasury_ledger";
    }

    public void initSchema() throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + table + " ("
                             + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                             + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                             + "entry_type VARCHAR(32) NOT NULL,"
                             + "amount DOUBLE NOT NULL,"
                             + "from_uuid VARCHAR(36) NULL,"
                             + "to_uuid VARCHAR(36) NULL,"
                             + "details VARCHAR(512) NULL,"
                             + "INDEX idx_treasury_type_time (entry_type, created_at),"
                             + "INDEX idx_treasury_from (from_uuid),"
                             + "INDEX idx_treasury_to (to_uuid)"
                             + ")")) {
            ps.executeUpdate();
        }
    }

    /** Audit-only row  -  does not change vault balance (closed-loop gross already settled separately). */
    public void insertAudit(
            TreasuryLedgerType type,
            double amount,
            UUID fromUuid,
            UUID toUuid,
            String details) throws SQLException {
        try (Connection c = db.open()) {
            insert(c, type, amount, fromUuid, toUuid, details);
        }
    }

    public void insert(
            Connection c,
            TreasuryLedgerType type,
            double amount,
            UUID fromUuid,
            UUID toUuid,
            String details) throws SQLException {
        if (amount < 0) {
            return;
        }
        if (amount == 0 && !isMintTrailDetail(details)) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + table + " (entry_type, amount, from_uuid, to_uuid, details) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, type.name());
            ps.setDouble(2, amount);
            ps.setString(3, fromUuid == null ? null : fromUuid.toString());
            ps.setString(4, toUuid == null ? null : toUuid.toString());
            ps.setString(5, truncate(details, 512));
            ps.executeUpdate();
        }
    }

    /** Audit-only /mint conversion marker (no vault movement when amount is 0). */
    public void insertMintTrail(UUID fromUuid, String details) throws SQLException {
        try (Connection c = db.open()) {
            insert(c, TreasuryLedgerType.TAX, 0, fromUuid, null, details);
        }
    }

    private static boolean isMintTrailDetail(String details) {
        if (details == null) {
            return false;
        }
        return details.startsWith("mint:gross=") || details.startsWith("mint:redeem=");
    }

    private static String normalizeMintDetail(String details) {
        if (details == null) {
            return "";
        }
        String prefix = "correction:tax_reclass:";
        return details.startsWith(prefix) ? details.substring(prefix.length()) : details;
    }

    private static double parseMintGrossDetail(String details) {
        String d = normalizeMintDetail(details);
        if (d.startsWith("mint:gross=")) {
            try {
                return roundMoney(Double.parseDouble(d.substring("mint:gross=".length())));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        if (d.startsWith("mint:redeem=")) {
            try {
                return roundMoney(Double.parseDouble(d.substring("mint:redeem=".length())));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        if ("mint".equals(d)) {
            return -1;
        }
        return 0;
    }

    public List<LedgerRow> listAfterId(long afterId, int limit) throws SQLException {
        List<LedgerRow> rows = new ArrayList<>();
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, entry_type, amount, from_uuid, to_uuid, details, created_at FROM "
                             + table
                             + " WHERE id > ? ORDER BY id ASC LIMIT ?")) {
            ps.setLong(1, Math.max(0, afterId));
            ps.setInt(2, Math.max(1, Math.min(limit, 500)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new LedgerRow(
                            rs.getLong("id"),
                            rs.getString("entry_type"),
                            rs.getDouble("amount"),
                            parseUuid(rs.getString("from_uuid")),
                            parseUuid(rs.getString("to_uuid")),
                            rs.getString("details"),
                            rs.getTimestamp("created_at")));
                }
            }
        }
        return rows;
    }

    private static final TreasuryLedgerType[] INFLOW_TYPES = {
            TreasuryLedgerType.OPENING,
            TreasuryLedgerType.TAX,
            TreasuryLedgerType.DEATH,
            TreasuryLedgerType.TOWNY_SINK,
            TreasuryLedgerType.LOAN_PRINCIPAL,
            TreasuryLedgerType.LOAN_INTEREST,
            TreasuryLedgerType.BOND_ISSUE,
            TreasuryLedgerType.BOND_COUPON_FORFEIT,
            TreasuryLedgerType.DONATION
    };

    /**
     * Legacy /pay reserve rows logged as NOTE_BURN before donations credited the reserve.
     * New donations use {@link TreasuryLedgerType#DONATION} and are reserve inflows.
     */
    public double totalDonationBurnRetired() throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(amount), 0) FROM " + table
                             + " WHERE entry_type = ? AND (details = 'donation'"
                             + " OR details LIKE 'correction:donation_reclass:%')")) {
            ps.setString(1, TreasuryLedgerType.NOTE_BURN.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? roundMoney(rs.getDouble(1)) : 0;
            }
        }
    }

    public static boolean isMintRedeemBurnDetail(String details) {
        if (details == null) {
            return false;
        }
        String normalized = normalizeMintDetail(details);
        return normalized.startsWith("mint:redeem=");
    }

    /** All-time unbacked Notes destroyed via dynamic tax (NOTE_BURN ledger, excl. /mint gold redeems). */
    public double totalNoteBurnRetired() throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(amount), 0) FROM " + table
                             + " WHERE entry_type = ?"
                             + " AND details NOT LIKE 'mint:redeem=%'"
                             + " AND details NOT LIKE 'correction:tax_reclass:mint:redeem=%'")) {
            ps.setString(1, TreasuryLedgerType.NOTE_BURN.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? roundMoney(rs.getDouble(1)) : 0;
            }
        }
    }

    /** Ledger settlement of the /mint over-issue shortfall (debt_repayment:over_issue_shortfall). */
    public double totalOverIssueShortfallRepaid() throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(amount), 0) FROM " + table
                             + " WHERE entry_type = ? AND LOWER(details) LIKE ?")) {
            ps.setString(1, TreasuryLedgerType.NOTE_BURN.name());
            ps.setString(2, "debt_repayment:over_issue_shortfall%");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? roundMoney(rs.getDouble(1)) : 0;
            }
        }
    }

    /** Post-July TAX still credited to reserve (pending NOTE_BURN correction). */
    public double totalTaxMiscreditedSinceJuly() throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(amount), 0) FROM " + table
                             + " WHERE entry_type = ? AND amount > 0 AND created_at >= ?")) {
            ps.setString(1, TreasuryLedgerType.TAX.name());
            ps.setString(2, "2026-07-01 00:00:00");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? roundMoney(rs.getDouble(1)) : 0;
            }
        }
    }

    private static final TreasuryLedgerType[] OUTFLOW_TYPES = {
            TreasuryLedgerType.GRANT,
            TreasuryLedgerType.DIVIDEND,
            TreasuryLedgerType.LOAN_DISBURSE,
            TreasuryLedgerType.VOTE,
            TreasuryLedgerType.PLAYTIME,
            TreasuryLedgerType.BOND_REDEEM,
            TreasuryLedgerType.BOND_COUPON
    };

    public LedgerTotals totalsBetween(Instant startInclusive, Instant endExclusive) throws SQLException {
        Map<TreasuryLedgerType, Double> byType = new EnumMap<>(TreasuryLedgerType.class);
        double inflow = 0;
        double outflow = 0;
        Timestamp start = Timestamp.from(startInclusive);
        Timestamp end = Timestamp.from(endExclusive);
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT entry_type, COALESCE(SUM(amount), 0) AS total FROM " + table
                             + " WHERE created_at >= ? AND created_at < ? GROUP BY entry_type")) {
            ps.setTimestamp(1, start);
            ps.setTimestamp(2, end);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String typeName = rs.getString(1);
                    double total = rs.getDouble(2);
                    try {
                        TreasuryLedgerType type = TreasuryLedgerType.valueOf(typeName);
                        byType.put(type, total);
                        if (isInflow(type)) {
                            inflow += total;
                        } else if (isOutflow(type)) {
                            outflow += total;
                        }
                    } catch (IllegalArgumentException ignored) {
                        // skip unknown
                    }
                }
            }
        }
        return new LedgerTotals(byType, roundMoney(inflow), roundMoney(outflow), roundMoney(inflow - outflow));
    }

    public LedgerTotals totalsAllTime() throws SQLException {
        Map<TreasuryLedgerType, Double> byType = new EnumMap<>(TreasuryLedgerType.class);
        double inflow = 0;
        double outflow = 0;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT entry_type, COALESCE(SUM(amount), 0) AS total FROM " + table + " GROUP BY entry_type");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String typeName = rs.getString(1);
                double total = rs.getDouble(2);
                try {
                    TreasuryLedgerType type = TreasuryLedgerType.valueOf(typeName);
                    byType.put(type, total);
                    if (isInflow(type)) {
                        inflow += total;
                    } else if (isOutflow(type)) {
                        outflow += total;
                    }
                } catch (IllegalArgumentException ignored) {
                    // skip unknown
                }
            }
        }
        return new LedgerTotals(byType, roundMoney(inflow), roundMoney(outflow), roundMoney(inflow - outflow));
    }

    /** Net physical gold converted via /mint minus /mint gold redemptions (post-reset audit). */
    public double totalGoldMinedGross() throws SQLException {
        double minted = 0;
        double redeemed = 0;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT amount, details FROM " + table
                             + " WHERE entry_type IN (?, ?) AND (details = 'mint' OR details LIKE '%mint:gross=%' OR details LIKE '%mint:redeem=%')")) {
            ps.setString(1, TreasuryLedgerType.TAX.name());
            ps.setString(2, TreasuryLedgerType.NOTE_BURN.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String details = rs.getString("details");
                    String normalized = normalizeMintDetail(details);
                    if (normalized.startsWith("mint:redeem=")) {
                        double gross = parseMintGrossDetail(details);
                        if (gross > 0) {
                            redeemed += gross;
                        }
                        continue;
                    }
                    double gross = parseMintGrossDetail(details);
                    if (gross > 0) {
                        minted += gross;
                        continue;
                    }
                    if ("mint".equals(normalized)) {
                        double tax = rs.getDouble("amount");
                        if (tax > 0) {
                            minted += tax / 0.001;
                        }
                    }
                }
            }
        }
        return roundMoney(Math.max(0, minted - redeemed));
    }

    /** Physical gold withdrawn via /mint gold (redeem trail rows). */
    public double totalMintRedeemGross() throws SQLException {
        double redeemed = 0;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT amount, details FROM " + table
                             + " WHERE entry_type IN (?, ?) AND (details LIKE '%mint:redeem=%')")) {
            ps.setString(1, TreasuryLedgerType.TAX.name());
            ps.setString(2, TreasuryLedgerType.NOTE_BURN.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String details = rs.getString("details");
                    String normalized = normalizeMintDetail(details);
                    if (normalized.startsWith("mint:redeem=")) {
                        double gross = parseMintGrossDetail(details);
                        if (gross > 0) {
                            redeemed += gross;
                        }
                    }
                }
            }
        }
        return roundMoney(redeemed);
    }

    /** @deprecated Use {@link #totalGoldMinedGross()}. */
    public double totalGoldMinted() throws SQLException {
        return totalGoldMinedGross();
    }

    /** Treasury GRANT outflows  -  over-printed wallet G (not from /mint). */
    public double totalGrantOutflows() throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(amount), 0) FROM " + table + " WHERE entry_type = ?")) {
            ps.setString(1, TreasuryLedgerType.GRANT.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? roundMoney(rs.getDouble(1)) : 0;
            }
        }
    }

    /** All-time loan principal + interest repaid to the Server Reserve. */
    public double totalLoanRepaymentsAllTime() throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(amount), 0) FROM " + table
                             + " WHERE entry_type IN (?, ?)")) {
            ps.setString(1, TreasuryLedgerType.LOAN_PRINCIPAL.name());
            ps.setString(2, TreasuryLedgerType.LOAN_INTEREST.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? roundMoney(rs.getDouble(1)) : 0;
            }
        }
    }

    public double averageMonthlyNetPool() throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT AVG(month_net) FROM ("
                             + "  SELECT DATE_FORMAT(CONVERT_TZ(created_at, '+00:00', '-10:00'), '%Y-%m') AS month_key,"
                             + "         SUM(CASE WHEN entry_type IN ('TAX','DEATH','LOAN_PRINCIPAL','LOAN_INTEREST','BOND_ISSUE','BOND_COUPON_FORFEIT','DONATION','TOWNY_SINK','OPENING')"
                             + "                  THEN amount ELSE 0 END)"
                             + "              - SUM(CASE WHEN entry_type IN ('GRANT','DIVIDEND','LOAN_DISBURSE','VOTE','PLAYTIME','BOND_REDEEM','BOND_COUPON')"
                             + "                  THEN amount ELSE 0 END) AS month_net"
                             + "  FROM " + table
                             + "  GROUP BY month_key"
                             + ") m"
                             + " WHERE month_key < DATE_FORMAT(CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '-10:00'), '%Y-%m')");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return roundMoney(rs.getDouble(1));
            }
        }
        return 0;
    }

    public double playerTaxPaidSince(UUID playerUuid, Instant since) throws SQLException {
        if (playerUuid == null) {
            return 0;
        }
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(amount), 0) FROM " + table
                             + " WHERE entry_type = ? AND from_uuid = ? AND created_at >= ?")) {
            ps.setString(1, TreasuryLedgerType.TAX.name());
            ps.setString(2, playerUuid.toString());
            ps.setTimestamp(3, Timestamp.from(since));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? roundMoney(rs.getDouble(1)) : 0;
            }
        }
    }

    public java.util.Optional<DividendPayout> lastDividendTo(UUID recipientUuid) throws SQLException {
        if (recipientUuid == null) {
            return java.util.Optional.empty();
        }
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT amount, details, created_at FROM " + table
                             + " WHERE entry_type = ? AND to_uuid = ? ORDER BY created_at DESC LIMIT 1")) {
            ps.setString(1, TreasuryLedgerType.DIVIDEND.name());
            ps.setString(2, recipientUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return java.util.Optional.empty();
                }
                return java.util.Optional.of(new DividendPayout(
                        rs.getDouble(1),
                        rs.getString(2),
                        rs.getTimestamp(3)));
            }
        }
    }

    public static boolean isInflow(TreasuryLedgerType type) {
        for (TreasuryLedgerType in : INFLOW_TYPES) {
            if (in == type) {
                return true;
            }
        }
        return false;
    }

    public static boolean isOutflow(TreasuryLedgerType type) {
        for (TreasuryLedgerType out : OUTFLOW_TYPES) {
            if (out == type) {
                return true;
            }
        }
        return false;
    }

    private static double roundMoney(double value) {
        return GoldMoney.round(value);
    }

    public record LedgerTotals(
            Map<TreasuryLedgerType, Double> byType,
            double totalInflow,
            double totalOutflow,
            double net) {

        public double amount(TreasuryLedgerType type) {
            return byType.getOrDefault(type, 0.0);
        }
    }

    public record DividendPayout(double amount, String details, Timestamp paidAt) {}

    public record LedgerRow(
            long id,
            String entryType,
            double amount,
            UUID fromUuid,
            UUID toUuid,
            String details,
            Timestamp createdAt) {}

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
