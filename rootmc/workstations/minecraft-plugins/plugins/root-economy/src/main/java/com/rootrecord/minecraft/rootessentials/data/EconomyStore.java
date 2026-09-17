package com.rootrecord.minecraft.rootessentials.data;

import com.rootrecord.minecraft.rootessentials.towny.TownyEconomyAccounts;


import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.common.TreasuryReserveIncomeDispatcher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class EconomyStore {

    private final MySqlSupport db;
    private final String table;
    private final double startingBalance;

    public EconomyStore(MySqlSupport db, String tablePrefix, double startingBalance) {
        this.db = db;
        this.table = tablePrefix + "economy_balances";
        this.startingBalance = startingBalance;
    }

    public void initSchema() throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + table + " (" +
                             "minecraft_uuid VARCHAR(36) PRIMARY KEY," +
                             "minecraft_username VARCHAR(32) NOT NULL," +
                             "balance DOUBLE NOT NULL DEFAULT 0," +
                             "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                             ")")) {
            ps.executeUpdate();
        }
    }

    private UUID walletUuid(UUID uuid, String username) {
        return EconomySystemAccounts.resolveWalletUuid(uuid, username);
    }

    private String walletName(UUID uuid, String username) {
        return EconomySystemAccounts.resolveWalletUsername(uuid, username);
    }

    public double balance(UUID uuid, String username) throws SQLException {
        uuid = walletUuid(uuid, username);
        username = walletName(uuid, username);
        ensureRow(uuid, username);
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT balance FROM " + table + " WHERE minecraft_uuid = ? LIMIT 1")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : startingBalance;
            }
        }
    }

    public boolean transfer(UUID from, String fromName, UUID to, String toName, double amount) throws SQLException {
        if (amount <= 0) return false;
        from = walletUuid(from, fromName);
        fromName = walletName(from, fromName);
        to = walletUuid(to, toName);
        toName = walletName(to, toName);
        ensureRow(from, fromName);
        ensureRow(to, toName);
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try {
                double bal;
                try (PreparedStatement s = c.prepareStatement(
                        "SELECT balance FROM " + table + " WHERE minecraft_uuid = ? FOR UPDATE")) {
                    s.setString(1, from.toString());
                    try (ResultSet rs = s.executeQuery()) {
                        bal = rs.next() ? rs.getDouble(1) : startingBalance;
                    }
                }
                if (bal < amount) {
                    c.rollback();
                    return false;
                }
                try (PreparedStatement d = c.prepareStatement(
                        "UPDATE " + table + " SET balance = balance - ?, minecraft_username = ? WHERE minecraft_uuid = ?")) {
                    d.setDouble(1, amount);
                    d.setString(2, fromName);
                    d.setString(3, from.toString());
                    d.executeUpdate();
                }
                try (PreparedStatement a = c.prepareStatement(
                        "UPDATE " + table + " SET balance = balance + ?, minecraft_username = ? WHERE minecraft_uuid = ?")) {
                    a.setDouble(1, amount);
                    a.setString(2, toName);
                    a.setString(3, to.toString());
                    a.executeUpdate();
                }
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

    public boolean withdraw(UUID uuid, String username, double amount) throws SQLException {
        return withdraw(uuid, username, amount, false);
    }

    /** When {@code allowDebt} is true, balance may go negative (e.g. wilderness destroy fees). */
    public boolean withdraw(UUID uuid, String username, double amount, boolean allowDebt) throws SQLException {
        if (amount <= 0) return false;
        uuid = walletUuid(uuid, username);
        username = walletName(uuid, username);
        ensureRow(uuid, username);
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try {
                double bal;
                try (PreparedStatement s = c.prepareStatement(
                        "SELECT balance FROM " + table + " WHERE minecraft_uuid = ? FOR UPDATE")) {
                    s.setString(1, uuid.toString());
                    try (ResultSet rs = s.executeQuery()) {
                        bal = rs.next() ? rs.getDouble(1) : startingBalance;
                    }
                }
                if (!allowDebt
                        && bal < amount
                        && !EconomySystemAccounts.isTownyServerAccount(uuid, username)) {
                    c.rollback();
                    return false;
                }
                try (PreparedStatement u = c.prepareStatement(
                        "UPDATE " + table + " SET balance = balance - ?, minecraft_username = ? WHERE minecraft_uuid = ?")) {
                    u.setDouble(1, amount);
                    u.setString(2, username);
                    u.setString(3, uuid.toString());
                    u.executeUpdate();
                }
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

    public void deposit(UUID uuid, String username, double amount) throws SQLException {
        if (amount <= 0) return;
        uuid = walletUuid(uuid, username);
        username = walletName(uuid, username);
        ensureRow(uuid, username);
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE " + table + " SET balance = balance + ?, minecraft_username = ? WHERE minecraft_uuid = ?")) {
            ps.setDouble(1, amount);
            ps.setString(2, username);
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        }
    }

    public void setBalance(UUID uuid, String username, double amount) throws SQLException {
        uuid = walletUuid(uuid, username);
        username = walletName(uuid, username);
        ensureRow(uuid, username);
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE " + table + " SET balance = ?, minecraft_username = ? WHERE minecraft_uuid = ?")) {
            // Player wallets may go negative (wilderness fees). System/towny/claim banks keep the given amount.
            ps.setDouble(1, amount);
            ps.setString(2, username);
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        }
    }

    public void resetBalance(UUID uuid, String username) throws SQLException {
        setBalance(uuid, username, startingBalance);
    }

    public java.util.List<BalanceRow> topBalances(int limit) throws SQLException {
        return topBaltop(BaltopKind.PLAYERS, limit);
    }

    public enum BaltopKind {
        PLAYERS,
        TOWNS,
        NATIONS
    }

    public java.util.List<BalanceRow> topBaltop(BaltopKind kind, int limit) throws SQLException {
        if (kind == BaltopKind.TOWNS || kind == BaltopKind.NATIONS) {
            return topBaltopDeduped(kind, limit);
        }
        java.util.List<BalanceRow> rows = new java.util.ArrayList<>();
        String where = baltopWhereClause(kind);
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT minecraft_username, balance FROM " + table + " WHERE " + where
                             + " ORDER BY balance DESC LIMIT ?")) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String username = rs.getString(1);
                    rows.add(new BalanceRow(displayName(username, kind), rs.getDouble(2)));
                }
            }
        }
        return rows;
    }

    /** Collapse duplicate town/nation vault rows (space vs underscore, case) and hide empty banks. */
    private java.util.List<BalanceRow> topBaltopDeduped(BaltopKind kind, int limit) throws SQLException {
        int cap = Math.max(1, limit);
        int fetchLimit = Math.max(cap * 8, cap);
        String where = baltopWhereClause(kind) + " AND balance > 0.0001";
        java.util.Map<String, BalanceRow> bestByKey = new java.util.LinkedHashMap<>();
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT minecraft_username, balance FROM " + table + " WHERE " + where
                             + " ORDER BY balance DESC LIMIT ?")) {
            ps.setInt(1, fetchLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String username = rs.getString(1);
                    double balance = rs.getDouble(2);
                    String name = displayName(username, kind);
                    String key = EconomySystemAccounts.bankDisplayKey(name);
                    if (key.isBlank()) {
                        continue;
                    }
                    BalanceRow existing = bestByKey.get(key);
                    if (existing == null || balance > existing.balance()) {
                        bestByKey.put(key, new BalanceRow(name, balance));
                    }
                }
            }
        }
        return bestByKey.values().stream()
                .sorted((a, b) -> Double.compare(b.balance(), a.balance()))
                .limit(cap)
                .toList();
    }

    /** All non-system player wallets with balance &gt; 0 (for RootMC cloud sync). */
    public java.util.List<PlayerBalanceRow> allPlayerBalancesForSync() throws SQLException {
        java.util.List<PlayerBalanceRow> rows = new java.util.ArrayList<>();
        String where = baltopWhereClause(BaltopKind.PLAYERS) + " AND balance > 0.0001";
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT minecraft_uuid, minecraft_username, balance FROM " + table + " WHERE " + where
                             + " ORDER BY balance DESC")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new PlayerBalanceRow(
                            rs.getString(1),
                            rs.getString(2),
                            rs.getDouble(3)));
                }
            }
        }
        return rows;
    }

    /** Server Reserve, town banks, and nation banks (for gold supply dashboard sync). */
    public java.util.List<SystemBalanceRow> allSystemBalancesForSync() throws SQLException {
        java.util.List<SystemBalanceRow> rows = new java.util.ArrayList<>();
        try (Connection c = db.open();
             PreparedStatement reservePs = c.prepareStatement(
                     "SELECT minecraft_uuid, minecraft_username, balance FROM " + table
                             + " WHERE LOWER(minecraft_username) = 'towny-server'"
                             + " OR LOWER(minecraft_uuid) = '" + EconomySystemAccounts.townyServerUuid() + "'"
                             + " LIMIT 1");
             PreparedStatement groupPs = c.prepareStatement(
                     "SELECT minecraft_uuid, minecraft_username, balance FROM " + table
                             + " WHERE balance > 0.0001 AND ("
                             + "LOWER(minecraft_username) LIKE 'town-%'"
                             + " OR LOWER(minecraft_username) LIKE 'nation-%'"
                             + ") ORDER BY balance DESC")) {
            try (ResultSet rs = reservePs.executeQuery()) {
                if (rs.next()) {
                    String username = rs.getString(2);
                    rows.add(new SystemBalanceRow(rs.getString(1), username, rs.getDouble(3), "reserve"));
                }
            }
            try (ResultSet rs = groupPs.executeQuery()) {
                while (rs.next()) {
                    String username = rs.getString(2);
                    String type = systemAccountType(username, rs.getString(1));
                    rows.add(new SystemBalanceRow(rs.getString(1), username, rs.getDouble(3), type));
                }
            }
        }
        return rows;
    }

    private static String systemAccountType(String username, String uuid) {
        java.util.UUID parsed = null;
        if (uuid != null && !uuid.isBlank()) {
            try {
                parsed = java.util.UUID.fromString(uuid);
            } catch (IllegalArgumentException ignored) {
                parsed = null;
            }
        }
        if (EconomySystemAccounts.isTownyServerAccount(parsed, username)) {
            return "reserve";
        }
        if (EconomySystemAccounts.isTownBankAccount(username)) {
            return "town";
        }
        if (EconomySystemAccounts.isNationBankAccount(username)) {
            return "nation";
        }
        if (EconomySystemAccounts.isClaimBankAccount(username)) {
            return "claim";
        }
        return "system";
    }

    public record SystemBalanceRow(String minecraftUuid, String minecraftUsername, double balance, String accountType) {}

    /** Sum of all non-system player wallet balances (for economy totals). */
    public double sumPlayerWalletGold() throws SQLException {
        String where = baltopWhereClause(BaltopKind.PLAYERS);
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(balance), 0) FROM " + table + " WHERE " + where)) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? GoldMoney.round(rs.getDouble(1)) : 0;
            }
        }
    }

    /** Sum of town bank balances ({@code town-*} accounts). */
    public double sumTownBankGold() throws SQLException {
        return sumBaltopKind(BaltopKind.TOWNS);
    }

    /** Sum of nation bank balances ({@code nation-*} accounts). */
    public double sumNationBankGold() throws SQLException {
        return sumBaltopKind(BaltopKind.NATIONS);
    }

    /** Circulating Notes: player wallets + town + nation banks (not Server Reserve vault). */
    public double sumCirculatingNotes() throws SQLException {
        return GoldMoney.round(sumPlayerWalletGold() + sumTownBankGold() + sumNationBankGold());
    }

    private double sumBaltopKind(BaltopKind kind) throws SQLException {
        String where = baltopWhereClause(kind);
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(balance), 0) FROM " + table + " WHERE " + where)) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? GoldMoney.round(rs.getDouble(1)) : 0;
            }
        }
    }

    public java.util.Optional<RankedBalance> baltopRank(BaltopKind kind, String accountUsername) throws SQLException {
        if (accountUsername == null || accountUsername.isBlank()) {
            return java.util.Optional.empty();
        }
        String lookup = accountUsername;
        if (kind == BaltopKind.TOWNS && !EconomySystemAccounts.isTownBankAccount(lookup)) {
            lookup = EconomySystemAccounts.townBankUsername(lookup);
        } else if (kind == BaltopKind.NATIONS && !EconomySystemAccounts.isNationBankAccount(lookup)) {
            lookup = EconomySystemAccounts.nationBankUsername(lookup);
        }
        Double balance = balanceByUsername(lookup);
        if (balance == null) {
            return java.util.Optional.empty();
        }
        String where = baltopWhereClause(kind);
        int higher;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM " + table + " WHERE " + where + " AND balance > ?")) {
            ps.setDouble(1, balance);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                higher = rs.getInt(1);
            }
        }
        return java.util.Optional.of(new RankedBalance(
                higher + 1,
                displayName(lookup, kind),
                balance));
    }

    public java.util.Optional<RankedBalance> baltopRankForPlayer(UUID uuid, String username) throws SQLException {
        if (uuid == null
                || EconomySystemAccounts.isAvaReserveAlias(uuid, username)
                || EconomySystemAccounts.isTownyServerAccount(uuid, username)) {
            return java.util.Optional.empty();
        }
        double balance = balance(uuid, username);
        String where = baltopWhereClause(BaltopKind.PLAYERS);
        int higher;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM " + table + " WHERE " + where + " AND balance > ?")) {
            ps.setDouble(1, balance);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                higher = rs.getInt(1);
            }
        }
        return java.util.Optional.of(new RankedBalance(higher + 1, username, balance));
    }

    private Double balanceByUsername(String username) throws SQLException {
        if (username == null || username.isBlank()) {
            return null;
        }
        String primary = username.trim();
        String alt = null;
        if (EconomySystemAccounts.isTownBankAccount(primary)
                || EconomySystemAccounts.isNationBankAccount(primary)) {
            String leaf = EconomySystemAccounts.isTownBankAccount(primary)
                    ? EconomySystemAccounts.stripTownPrefix(primary)
                    : EconomySystemAccounts.stripNationPrefix(primary);
            String sanitized = EconomySystemAccounts.isTownBankAccount(primary)
                    ? EconomySystemAccounts.townBankUsername(leaf)
                    : EconomySystemAccounts.nationBankUsername(leaf);
            if (sanitized != null && !sanitized.equalsIgnoreCase(primary)) {
                alt = primary;
                primary = sanitized;
            }
        }
        Double hit = balanceByUsernameExact(primary);
        if (hit != null) {
            return hit;
        }
        return alt == null ? null : balanceByUsernameExact(alt);
    }

    private Double balanceByUsernameExact(String username) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT balance FROM " + table + " WHERE LOWER(minecraft_username) = ? LIMIT 1")) {
            ps.setString(1, username.toLowerCase(java.util.Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : null;
            }
        }
    }

    private static String baltopWhereClause(BaltopKind kind) {
        return switch (kind) {
            case PLAYERS -> "LOWER(minecraft_username) <> 'towny-server'"
                    + " AND LOWER(minecraft_username) <> 'server-reserve'"
                    + " AND LOWER(minecraft_username) <> 'ava_ivy'"
                    + " AND minecraft_uuid <> '" + EconomySystemAccounts.townyServerUuid() + "'"
                    + " AND minecraft_uuid <> '" + EconomySystemAccounts.AVA_IVY_UUID + "'"
                    + " AND LOWER(minecraft_username) NOT LIKE 'town-%'"
                    + " AND LOWER(minecraft_username) NOT LIKE 'nation-%'"
                    + " AND LOWER(minecraft_username) NOT LIKE 'claim-%'"
                    + " AND LOWER(minecraft_username) <> 'player'";
            case TOWNS -> "LOWER(minecraft_username) LIKE 'town-%'";
            case NATIONS -> "LOWER(minecraft_username) LIKE 'nation-%'";
        };
    }

    private static String displayName(String username, BaltopKind kind) {
        return switch (kind) {
            case TOWNS -> EconomySystemAccounts.stripTownPrefix(username);
            case NATIONS -> EconomySystemAccounts.stripNationPrefix(username);
            default -> username;
        };
    }

    public record RankedBalance(int rank, String displayName, double balance) {}

    public boolean withholdTransactionTax(
            UUID payer,
            String payerName,
            UUID treasuryUuid,
            String treasuryName,
            double gross,
            double tax,
            TreasuryLedgerStore ledger,
            com.rootrecord.minecraft.common.TreasuryLedgerType ledgerType,
            String channel) throws SQLException {
        if (gross <= 0 || tax < 0 || tax > gross) {
            return false;
        }
        ensureRow(payer, payerName);
        ensureRow(treasuryUuid, treasuryName);
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try {
                double bal;
                try (PreparedStatement s = c.prepareStatement(
                        "SELECT balance FROM " + table + " WHERE minecraft_uuid = ? FOR UPDATE")) {
                    s.setString(1, payer.toString());
                    try (ResultSet rs = s.executeQuery()) {
                        bal = rs.next() ? rs.getDouble(1) : startingBalance;
                    }
                }
                if (bal < gross) {
                    c.rollback();
                    return false;
                }
                try (PreparedStatement d = c.prepareStatement(
                        "UPDATE " + table + " SET balance = balance - ?, minecraft_username = ? WHERE minecraft_uuid = ?")) {
                    d.setDouble(1, gross);
                    d.setString(2, payerName);
                    d.setString(3, payer.toString());
                    d.executeUpdate();
                }
                if (tax > 0) {
                    try (PreparedStatement t = c.prepareStatement(
                            "UPDATE " + table + " SET balance = balance + ?, minecraft_username = ? WHERE minecraft_uuid = ?")) {
                        t.setDouble(1, tax);
                        t.setString(2, treasuryName);
                        t.setString(3, treasuryUuid.toString());
                        t.executeUpdate();
                    }
                    ledger.insert(c, ledgerType, tax, payer, treasuryUuid, channel);
                }
                c.commit();
                if (tax > 0) {
                    dispatchTreasuryIncome(tax, ledgerType, payer, channel);
                }
                return true;
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * Debit payer {@code gross}; {@code tax} is retired (destroyed) — not credited to treasury.
     * Used while Notes are over-issued so dynamic tax pays down unbacked supply.
     */
    public boolean withholdTransactionTaxBurn(
            UUID payer,
            String payerName,
            double gross,
            double tax,
            TreasuryLedgerStore ledger,
            String channel) throws SQLException {
        if (gross <= 0 || tax < 0 || tax > gross) {
            return false;
        }
        ensureRow(payer, payerName);
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try {
                double bal;
                try (PreparedStatement s = c.prepareStatement(
                        "SELECT balance FROM " + table + " WHERE minecraft_uuid = ? FOR UPDATE")) {
                    s.setString(1, payer.toString());
                    try (ResultSet rs = s.executeQuery()) {
                        bal = rs.next() ? rs.getDouble(1) : startingBalance;
                    }
                }
                if (bal < gross) {
                    c.rollback();
                    return false;
                }
                try (PreparedStatement d = c.prepareStatement(
                        "UPDATE " + table + " SET balance = balance - ?, minecraft_username = ? WHERE minecraft_uuid = ?")) {
                    d.setDouble(1, gross);
                    d.setString(2, payerName);
                    d.setString(3, payer.toString());
                    d.executeUpdate();
                }
                if (tax > 0) {
                    ledger.insert(
                            c,
                            com.rootrecord.minecraft.common.TreasuryLedgerType.NOTE_BURN,
                            tax,
                            payer,
                            null,
                            channel);
                }
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

    public void depositTreasury(
            UUID treasuryUuid,
            String treasuryName,
            double amount,
            TreasuryLedgerStore ledger,
            com.rootrecord.minecraft.common.TreasuryLedgerType type,
            UUID sourceUuid,
            String details) throws SQLException {
        if (amount <= 0) {
            return;
        }
        ensureRow(treasuryUuid, treasuryName);
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE " + table + " SET balance = balance + ?, minecraft_username = ? WHERE minecraft_uuid = ?")) {
                    ps.setDouble(1, amount);
                    ps.setString(2, treasuryName);
                    ps.setString(3, treasuryUuid.toString());
                    ps.executeUpdate();
                }
                ledger.insert(c, type, amount, sourceUuid, treasuryUuid, details);
                c.commit();
                dispatchTreasuryIncome(amount, type, sourceUuid, details);
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * /mint gold Ã¢â‚¬â€ redeems wallet Notes to physical gold (no reserve credit).
     * Logged as NOTE_BURN with mint:redeem details; reduces /mint backing via ledger audit.
     */
    public boolean burnMintRedemption(
            UUID playerUuid,
            String playerName,
            double amount,
            TreasuryLedgerStore ledger) throws SQLException {
        if (amount <= 0 || playerUuid == null) {
            return false;
        }
        ensureRow(playerUuid, playerName);
        String details = String.format(java.util.Locale.US, "mint:redeem=%.3f", amount);
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try {
                double bal;
                try (PreparedStatement s = c.prepareStatement(
                        "SELECT balance FROM " + table + " WHERE minecraft_uuid = ? FOR UPDATE")) {
                    s.setString(1, playerUuid.toString());
                    try (ResultSet rs = s.executeQuery()) {
                        bal = rs.next() ? rs.getDouble(1) : startingBalance;
                    }
                }
                if (bal + 1e-9 < amount) {
                    c.rollback();
                    return false;
                }
                try (PreparedStatement w = c.prepareStatement(
                        "UPDATE " + table + " SET balance = balance - ?, minecraft_username = ? WHERE minecraft_uuid = ?")) {
                    w.setDouble(1, amount);
                    w.setString(2, playerName);
                    w.setString(3, playerUuid.toString());
                    w.executeUpdate();
                }
                ledger.insert(
                        c,
                        com.rootrecord.minecraft.common.TreasuryLedgerType.NOTE_BURN,
                        amount,
                        playerUuid,
                        null,
                        details);
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

    /**
     * Voluntary /pay reserve — moves Notes from the donor wallet into the Server Reserve vault.
     * Logged as DONATION (reserve inflow).
     */
    public boolean creditReserveDonation(
            UUID donorUuid,
            String donorName,
            double amount,
            UUID treasuryUuid,
            String treasuryName,
            TreasuryLedgerStore ledger) throws SQLException {
        if (amount <= 0 || donorUuid == null || treasuryUuid == null) {
            return false;
        }
        ensureRow(donorUuid, donorName);
        ensureRow(treasuryUuid, treasuryName);
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try {
                double bal;
                try (PreparedStatement s = c.prepareStatement(
                        "SELECT balance FROM " + table + " WHERE minecraft_uuid = ? FOR UPDATE")) {
                    s.setString(1, donorUuid.toString());
                    try (ResultSet rs = s.executeQuery()) {
                        bal = rs.next() ? rs.getDouble(1) : startingBalance;
                    }
                }
                if (bal + 1e-9 < amount) {
                    c.rollback();
                    return false;
                }
                try (PreparedStatement w = c.prepareStatement(
                        "UPDATE " + table + " SET balance = balance - ?, minecraft_username = ? WHERE minecraft_uuid = ?")) {
                    w.setDouble(1, amount);
                    w.setString(2, donorName);
                    w.setString(3, donorUuid.toString());
                    w.executeUpdate();
                }
                try (PreparedStatement t = c.prepareStatement(
                        "UPDATE " + table + " SET balance = balance + ?, minecraft_username = ? WHERE minecraft_uuid = ?")) {
                    t.setDouble(1, amount);
                    t.setString(2, treasuryName);
                    t.setString(3, treasuryUuid.toString());
                    t.executeUpdate();
                }
                ledger.insert(
                        c,
                        com.rootrecord.minecraft.common.TreasuryLedgerType.DONATION,
                        amount,
                        donorUuid,
                        treasuryUuid,
                        "donation");
                c.commit();
                // Do not dispatchTreasuryIncome — /pay reserve is 100% Reserve, not bond coupon pool.
                return true;
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /** Ledger row only Ã¢â‚¬â€ balance already updated elsewhere (e.g. Towny closed economy via Vault). */
    public void recordTreasuryLedgerOnly(
            UUID treasuryUuid,
            TreasuryLedgerStore ledger,
            com.rootrecord.minecraft.common.TreasuryLedgerType type,
            double amount,
            UUID sourceUuid,
            String details) throws SQLException {
        if (amount <= 0) {
            return;
        }
        ensureRow(treasuryUuid, EconomySystemAccounts.townyServerUsername());
        try (Connection c = db.open()) {
            ledger.insert(c, type, amount, sourceUuid, treasuryUuid, details);
        }
    }

    public boolean debitTreasuryForPayout(
            UUID treasuryUuid,
            String treasuryName,
            UUID recipientUuid,
            String recipientName,
            double amount,
            TreasuryLedgerStore ledger,
            com.rootrecord.minecraft.common.TreasuryLedgerType type,
            UUID sourceUuid,
            String details) throws SQLException {
        if (amount <= 0) {
            return false;
        }
        ensureRow(treasuryUuid, treasuryName);
        ensureRow(recipientUuid, recipientName);
        String ledgerDetails = "operator=" + (sourceUuid == null ? "?" : sourceUuid) + ";" + (details == null ? "" : details);
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement lock = c.prepareStatement(
                        "SELECT balance FROM " + table + " WHERE minecraft_uuid = ? FOR UPDATE")) {
                    lock.setString(1, treasuryUuid.toString());
                    lock.executeQuery();
                }
                try (PreparedStatement w = c.prepareStatement(
                        "UPDATE " + table + " SET balance = balance - ?, minecraft_username = ? WHERE minecraft_uuid = ?")) {
                    w.setDouble(1, amount);
                    w.setString(2, treasuryName);
                    w.setString(3, treasuryUuid.toString());
                    w.executeUpdate();
                }
                ledger.insert(c, type, amount, treasuryUuid, recipientUuid, ledgerDetails);
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

    public boolean grantFromTreasury(
            UUID treasuryUuid,
            String treasuryName,
            UUID recipientUuid,
            String recipientName,
            double amount,
            TreasuryLedgerStore ledger,
            com.rootrecord.minecraft.common.TreasuryLedgerType type,
            UUID operatorUuid,
            String reason) throws SQLException {
        if (amount <= 0) {
            return false;
        }
        ensureRow(treasuryUuid, treasuryName);
        ensureRow(recipientUuid, recipientName);
        String details = "operator=" + (operatorUuid == null ? "?" : operatorUuid) + ";" + (reason == null ? "" : reason);
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try {
                // Always debit towny-server (treasury may go negative; never mint to recipient).
                try (PreparedStatement lock = c.prepareStatement(
                        "SELECT balance FROM " + table + " WHERE minecraft_uuid = ? FOR UPDATE")) {
                    lock.setString(1, treasuryUuid.toString());
                    lock.executeQuery();
                }
                try (PreparedStatement w = c.prepareStatement(
                        "UPDATE " + table + " SET balance = balance - ?, minecraft_username = ? WHERE minecraft_uuid = ?")) {
                    w.setDouble(1, amount);
                    w.setString(2, treasuryName);
                    w.setString(3, treasuryUuid.toString());
                    w.executeUpdate();
                }
                try (PreparedStatement d = c.prepareStatement(
                        "UPDATE " + table + " SET balance = balance + ?, minecraft_username = ? WHERE minecraft_uuid = ?")) {
                    d.setDouble(1, amount);
                    d.setString(2, recipientName);
                    d.setString(3, recipientUuid.toString());
                    d.executeUpdate();
                }
                ledger.insert(c, type, amount, treasuryUuid, recipientUuid, details);
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

    /** Debits reserve for physical Gold payout — no wallet credit (bond coupon / redemption). */
    public boolean debitTreasuryPhysical(
            UUID treasuryUuid,
            String treasuryName,
            UUID playerUuid,
            String playerName,
            double amount,
            TreasuryLedgerStore ledger,
            com.rootrecord.minecraft.common.TreasuryLedgerType type,
            String details) throws SQLException {
        if (amount <= 0 || playerUuid == null) {
            return false;
        }
        ensureRow(treasuryUuid, treasuryName);
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement lock = c.prepareStatement(
                        "SELECT balance FROM " + table + " WHERE minecraft_uuid = ? FOR UPDATE")) {
                    lock.setString(1, treasuryUuid.toString());
                    lock.executeQuery();
                }
                try (PreparedStatement w = c.prepareStatement(
                        "UPDATE " + table + " SET balance = balance - ?, minecraft_username = ? WHERE minecraft_uuid = ?")) {
                    w.setDouble(1, amount);
                    w.setString(2, treasuryName);
                    w.setString(3, treasuryUuid.toString());
                    w.executeUpdate();
                }
                ledger.insert(c, type, amount, treasuryUuid, playerUuid, details == null ? "" : details);
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

    public boolean applyLoanRepayment(
            UUID payerUuid,
            String payerName,
            UUID treasuryUuid,
            String treasuryName,
            double principalPart,
            double interestPart,
            TreasuryLedgerStore ledger,
            boolean withdrawFromPayer) throws SQLException {
        double principal = Math.max(0, principalPart);
        double interest = Math.max(0, interestPart);
        double total = roundMoney(principal + interest);
        if (total <= 0) {
            return true;
        }
        principal = roundMoney(principal);
        interest = roundMoney(interest);
        ensureRow(treasuryUuid, treasuryName);
        if (withdrawFromPayer) {
            ensureRow(payerUuid, payerName);
        }
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try {
                if (withdrawFromPayer) {
                    double bal;
                    try (PreparedStatement s = c.prepareStatement(
                            "SELECT balance FROM " + table + " WHERE minecraft_uuid = ? FOR UPDATE")) {
                        s.setString(1, payerUuid.toString());
                        try (ResultSet rs = s.executeQuery()) {
                            bal = rs.next() ? rs.getDouble(1) : startingBalance;
                        }
                    }
                    if (bal + 0.0001d < total) {
                        c.rollback();
                        return false;
                    }
                    try (PreparedStatement d = c.prepareStatement(
                            "UPDATE " + table + " SET balance = balance - ?, minecraft_username = ? WHERE minecraft_uuid = ?")) {
                        d.setDouble(1, total);
                        d.setString(2, payerName);
                        d.setString(3, payerUuid.toString());
                        d.executeUpdate();
                    }
                }
                if (principal > 0) {
                    creditTreasuryInTx(
                            c,
                            treasuryUuid,
                            treasuryName,
                            principal,
                            ledger,
                            com.rootrecord.minecraft.common.TreasuryLedgerType.LOAN_PRINCIPAL,
                            payerUuid,
                            "principal");
                }
                if (interest > 0) {
                    creditTreasuryInTx(
                            c,
                            treasuryUuid,
                            treasuryName,
                            interest,
                            ledger,
                            com.rootrecord.minecraft.common.TreasuryLedgerType.LOAN_INTEREST,
                            payerUuid,
                            "interest");
                }
                c.commit();
                if (principal > 0) {
                    dispatchTreasuryIncome(
                            principal,
                            com.rootrecord.minecraft.common.TreasuryLedgerType.LOAN_PRINCIPAL,
                            payerUuid,
                            "principal");
                }
                if (interest > 0) {
                    dispatchTreasuryIncome(
                            interest,
                            com.rootrecord.minecraft.common.TreasuryLedgerType.LOAN_INTEREST,
                            payerUuid,
                            "interest");
                }
                return true;
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private void creditTreasuryInTx(
            Connection c,
            UUID treasuryUuid,
            String treasuryName,
            double amount,
            TreasuryLedgerStore ledger,
            com.rootrecord.minecraft.common.TreasuryLedgerType type,
            UUID sourceUuid,
            String details) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE " + table + " SET balance = balance + ?, minecraft_username = ? WHERE minecraft_uuid = ?")) {
            ps.setDouble(1, amount);
            ps.setString(2, treasuryName);
            ps.setString(3, treasuryUuid.toString());
            ps.executeUpdate();
        }
        ledger.insert(c, type, amount, sourceUuid, treasuryUuid, details);
    }

    private static double roundMoney(double value) {
        return GoldMoney.round(value);
    }

    private static void dispatchTreasuryIncome(
            double amount,
            com.rootrecord.minecraft.common.TreasuryLedgerType type,
            UUID sourceUuid,
            String details) {
        TreasuryReserveIncomeDispatcher.dispatch(amount, type, sourceUuid, details);
    }

    public record PlayerBalanceRow(String minecraftUuid, String minecraftUsername, double balance) {}

    public record DeathFeeResult(double grossFee, double treasuryAmount, double killerAmount) {}

    /**
     * Atomic PvP death fee: victim debit, reserve credit (DEATH ledger), killer credit, killer audit row.
     */
    public DeathFeeResult applyDeathFee(
            UUID victimUuid,
            String victimName,
            UUID killerUuid,
            String killerName,
            double victimBalance,
            double victimBalancePercent,
            double treasuryShareOfFee,
            double minFeeGold,
            UUID treasuryUuid,
            String treasuryUsername,
            TreasuryLedgerStore ledger) throws SQLException {
        if (victimUuid == null || victimBalancePercent <= 0 || victimBalance <= 0) {
            return null;
        }
        double percentFee = roundMoney(victimBalance * victimBalancePercent);
        double fee = roundMoney(Math.max(minFeeGold, percentFee));
        fee = roundMoney(Math.min(fee, victimBalance));
        if (fee < GoldMoney.MIN_AMOUNT) {
            return null;
        }
        double treasuryAmount = roundMoney(fee * clamp01(treasuryShareOfFee));
        double killerAmount = roundMoney(fee - treasuryAmount);
        if (killerUuid == null || killerAmount < GoldMoney.MIN_AMOUNT) {
            killerAmount = 0;
            treasuryAmount = fee;
        }
        ensureRow(victimUuid, victimName);
        if (killerUuid != null && killerAmount > 0) {
            ensureRow(killerUuid, killerName);
        }
        ensureRow(treasuryUuid, treasuryUsername);
        String killerLabel = killerName == null || killerName.isBlank() ? "none" : killerName.trim();
        String treasuryPrefix = killerUuid == null ? "death" : "pvp";
        String treasuryDetails = String.format(
                java.util.Locale.US,
                "%s:gross=%.3f;killer=%s;killer_share=%.3f",
                treasuryPrefix,
                fee,
                killerLabel,
                killerAmount);
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement s = c.prepareStatement(
                        "SELECT balance FROM " + table + " WHERE minecraft_uuid = ? FOR UPDATE")) {
                    s.setString(1, victimUuid.toString());
                    try (ResultSet rs = s.executeQuery()) {
                        if (!rs.next() || rs.getDouble(1) + 1e-9 < fee) {
                            c.rollback();
                            return null;
                        }
                    }
                }
                try (PreparedStatement w = c.prepareStatement(
                        "UPDATE " + table + " SET balance = balance - ?, minecraft_username = ? WHERE minecraft_uuid = ?")) {
                    w.setDouble(1, fee);
                    w.setString(2, victimName);
                    w.setString(3, victimUuid.toString());
                    w.executeUpdate();
                }
                if (treasuryAmount > 0) {
                    creditTreasuryInTx(
                            c,
                            treasuryUuid,
                            treasuryUsername,
                            treasuryAmount,
                            ledger,
                            com.rootrecord.minecraft.common.TreasuryLedgerType.DEATH,
                            victimUuid,
                            treasuryDetails);
                }
                if (killerUuid != null && killerAmount > 0) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE " + table + " SET balance = balance + ?, minecraft_username = ? WHERE minecraft_uuid = ?")) {
                        ps.setDouble(1, killerAmount);
                        ps.setString(2, killerName);
                        ps.setString(3, killerUuid.toString());
                        ps.executeUpdate();
                    }
                    ledger.insert(
                            c,
                            com.rootrecord.minecraft.common.TreasuryLedgerType.OTHER,
                            killerAmount,
                            victimUuid,
                            killerUuid,
                            String.format(java.util.Locale.US, "death:killer_payout;gross=%.3f", fee));
                }
                c.commit();
                if (treasuryAmount > 0) {
                    dispatchTreasuryIncome(
                            treasuryAmount,
                            com.rootrecord.minecraft.common.TreasuryLedgerType.DEATH,
                            victimUuid,
                            treasuryDetails);
                }
                return new DeathFeeResult(fee, treasuryAmount, killerAmount);
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    public record BalanceRow(String username, double balance) {}

    /**
     * Repair town/nation vault rows saved under placeholder {@code player} (Towny UUID, null name).
     * Returns rows updated.
     */
    public int reconcileMislabeledHolderAccounts() throws SQLException {
        int updated = 0;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT minecraft_uuid, minecraft_username FROM " + table + " WHERE balance > 0.0001");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString(1));
                String current = rs.getString(2);
                java.util.Optional<TownyEconomyAccounts.VaultAccount> holder =
                        TownyEconomyAccounts.holderByUuid(uuid);
                if (holder.isEmpty()) {
                    continue;
                }
                String canonical = holder.get().username();
                if (canonical.equalsIgnoreCase(current)) {
                    continue;
                }
                if (!"player".equalsIgnoreCase(current)
                        && !EconomySystemAccounts.isTownBankAccount(canonical)
                        && !EconomySystemAccounts.isNationBankAccount(canonical)) {
                    continue;
                }
                try (PreparedStatement u = c.prepareStatement(
                        "UPDATE " + table + " SET minecraft_username = ? WHERE minecraft_uuid = ?")) {
                    u.setString(1, canonical);
                    u.setString(2, uuid.toString());
                    updated += u.executeUpdate();
                }
            }
        }
        return updated;
    }

    private void ensureRow(UUID uuid, String username) throws SQLException {
        String incoming = TownyEconomyAccounts.canonicalStoredUsername(uuid, username);
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO " + table + " (minecraft_uuid, minecraft_username, balance) VALUES (?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE minecraft_username = IF("
                             + "LOWER(VALUES(minecraft_username)) = 'player' "
                             + "OR ("
                             + "(LOWER(minecraft_username) LIKE 'town-%' OR LOWER(minecraft_username) LIKE 'nation-%' "
                             + "OR LOWER(minecraft_username) = 'towny-server') "
                             + "AND LOWER(VALUES(minecraft_username)) NOT LIKE 'town-%' "
                             + "AND LOWER(VALUES(minecraft_username)) NOT LIKE 'nation-%' "
                             + "AND LOWER(VALUES(minecraft_username)) <> 'towny-server'"
                             + "), "
                             + "minecraft_username, VALUES(minecraft_username))")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, incoming);
            ps.setDouble(3, startingBalance);
            ps.executeUpdate();
        }
    }
}
