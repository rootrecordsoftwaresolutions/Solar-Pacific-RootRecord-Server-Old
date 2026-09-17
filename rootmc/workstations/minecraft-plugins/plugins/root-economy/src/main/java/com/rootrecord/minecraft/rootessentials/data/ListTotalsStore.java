package com.rootrecord.minecraft.rootessentials.data;

import com.rootrecord.minecraft.common.GoldMoney;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Precomputed economy list totals (MySQL). Commands and sync read this — no live ledger re-sum. */
public final class ListTotalsStore {

    public record Row(
            String scope,
            String groupKey,
            String category,
            String label,
            double amountG,
            Instant computedAt) {}

    private final MySqlSupport db;
    private final String table;

    public ListTotalsStore(MySqlSupport db, String tablePrefix) {
        this.db = db;
        this.table = tablePrefix + "list_totals";
    }

    public void initSchema() throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + table + " ("
                             + "scope VARCHAR(32) NOT NULL,"
                             + "group_key VARCHAR(64) NOT NULL,"
                             + "category VARCHAR(64) NOT NULL,"
                             + "label VARCHAR(255) NOT NULL DEFAULT '',"
                             + "amount_g DOUBLE NOT NULL DEFAULT 0,"
                             + "meta_json TEXT NULL,"
                             + "computed_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),"
                             + "PRIMARY KEY (scope, group_key, category),"
                             + "KEY idx_list_totals_scope (scope, group_key)"
                             + ")")) {
            ps.executeUpdate();
        }
    }

    public Instant latestComputedAt(String scope) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT MAX(computed_at) FROM " + table + " WHERE scope = ?")) {
            ps.setString(1, scope);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Timestamp ts = rs.getTimestamp(1);
                return ts == null ? null : ts.toInstant();
            }
        }
    }

    public void upsert(
            String scope,
            String groupKey,
            String category,
            String label,
            double amountG,
            Instant computedAt) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO " + table
                             + " (scope, group_key, category, label, amount_g, meta_json, computed_at)"
                             + " VALUES (?, ?, ?, ?, ?, NULL, ?)"
                             + " ON DUPLICATE KEY UPDATE"
                             + " label = VALUES(label),"
                             + " amount_g = VALUES(amount_g),"
                             + " computed_at = VALUES(computed_at)")) {
            ps.setString(1, scope);
            ps.setString(2, groupKey);
            ps.setString(3, category);
            ps.setString(4, label == null ? "" : label);
            ps.setDouble(5, GoldMoney.round(amountG));
            ps.setTimestamp(6, Timestamp.from(computedAt == null ? Instant.now() : computedAt));
            ps.executeUpdate();
        }
    }

    public List<Row> readAll(String scope) throws SQLException {
        List<Row> out = new ArrayList<>();
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT scope, group_key, category, label, amount_g, computed_at FROM " + table
                             + " WHERE scope = ? ORDER BY group_key, category")) {
            ps.setString(1, scope);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("computed_at");
                    out.add(new Row(
                            rs.getString("scope"),
                            rs.getString("group_key"),
                            rs.getString("category"),
                            rs.getString("label"),
                            rs.getDouble("amount_g"),
                            ts == null ? Instant.EPOCH : ts.toInstant()));
                }
            }
        }
        return out;
    }
}
