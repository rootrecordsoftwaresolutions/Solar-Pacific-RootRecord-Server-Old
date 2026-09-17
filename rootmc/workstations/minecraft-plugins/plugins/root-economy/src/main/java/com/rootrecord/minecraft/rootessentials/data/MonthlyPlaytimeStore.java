package com.rootrecord.minecraft.rootessentials.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;

/**
 * Read-only monthly playtime for /reserve (written by Root-Times / RootMC).
 *
 * <p>Supports scoped schema ({@code seconds} + {@code scope}) and legacy
 * ({@code playtime_seconds}, no scope).
 */
public final class MonthlyPlaytimeStore {

    private static final String SCOPE_GLOBAL = "*";

    private final MySqlSupport db;
    private final String table;

    public MonthlyPlaytimeStore(MySqlSupport db, String tablePrefix) {
        this.db = db;
        this.table = tablePrefix + "rootmc_playtime_monthly";
    }

    public long monthlySeconds(UUID uuid, String monthKey) throws SQLException {
        if (uuid == null || monthKey == null || monthKey.isBlank()) {
            return 0;
        }
        try (Connection c = db.open()) {
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT seconds FROM " + table
                                + " WHERE uuid = ? AND scope = ? AND month_key = ? LIMIT 1")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, SCOPE_GLOBAL);
                    ps.setString(3, monthKey);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? Math.max(0, rs.getLong(1)) : 0;
                    }
                }
            } catch (SQLException ex) {
                if (!isUnknownColumn(ex)) {
                    throw ex;
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT playtime_seconds FROM " + table
                            + " WHERE uuid = ? AND month_key = ? LIMIT 1")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, monthKey);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Math.max(0, rs.getLong(1)) : 0;
                }
            }
        }
    }

    public long totalEligibleSeconds(String monthKey, long minSeconds) throws SQLException {
        if (monthKey == null || monthKey.isBlank()) {
            return 0;
        }
        long min = Math.max(0, minSeconds);
        try (Connection c = db.open()) {
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COALESCE(SUM(seconds), 0) FROM " + table
                                + " WHERE scope = ? AND month_key = ? AND seconds >= ?")) {
                    ps.setString(1, SCOPE_GLOBAL);
                    ps.setString(2, monthKey);
                    ps.setLong(3, min);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? Math.max(0, rs.getLong(1)) : 0;
                    }
                }
            } catch (SQLException ex) {
                if (!isUnknownColumn(ex)) {
                    throw ex;
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COALESCE(SUM(playtime_seconds), 0) FROM " + table
                            + " WHERE month_key = ? AND playtime_seconds >= ?")) {
                ps.setString(1, monthKey);
                ps.setLong(2, min);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Math.max(0, rs.getLong(1)) : 0;
                }
            }
        }
    }

    private static boolean isUnknownColumn(SQLException ex) {
        // MySQL ER_BAD_FIELD_ERROR = 1054; SQLState 42S22
        if (ex.getErrorCode() == 1054) {
            return true;
        }
        String state = ex.getSQLState();
        if (state != null && state.startsWith("42S")) {
            return true;
        }
        String msg = ex.getMessage();
        return msg != null && msg.toLowerCase(Locale.ROOT).contains("unknown column");
    }
}
