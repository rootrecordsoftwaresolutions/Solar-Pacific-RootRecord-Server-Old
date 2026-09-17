package com.rootrecord.minecraft.rootupkeep.data;

import com.rootrecord.minecraft.rootupkeep.config.UpkeepConfig;

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
import java.util.UUID;

/** Accumulates inactivity tax debits to deliver as a while-away summary on next login. */
public final class InactivityTaxPendingStore {

    public record PendingRow(String kind, String groupName, double amountG) {}

    private final UpkeepConfig config;

    public InactivityTaxPendingStore(UpkeepConfig config) {
        this.config = config;
    }

    public String tableName() {
        return config.mysqlTablePrefix() + "upkeep_tax_pending";
    }

    public void initSchema() throws SQLException {
        if (!config.mysqlConfigured()) {
            return;
        }
        try (Connection c = open(); Statement st = c.createStatement()) {
            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + tableName() + " ("
                            + "notify_uuid CHAR(36) NOT NULL,"
                            + "kind VARCHAR(16) NOT NULL,"
                            + "group_name VARCHAR(64) NOT NULL DEFAULT '',"
                            + "amount_g DOUBLE NOT NULL DEFAULT 0,"
                            + "updated_at DATETIME NOT NULL,"
                            + "PRIMARY KEY (notify_uuid, kind, group_name)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    public void addAccrual(UUID notifyUuid, String kind, String groupName, double amountG) throws SQLException {
        if (notifyUuid == null || amountG <= 0 || kind == null || kind.isBlank()) {
            return;
        }
        String group = groupName == null ? "" : groupName.trim();
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO " + tableName()
                                + " (notify_uuid, kind, group_name, amount_g, updated_at) VALUES (?,?,?,?,?)"
                                + " ON DUPLICATE KEY UPDATE amount_g = amount_g + VALUES(amount_g),"
                                + " updated_at = VALUES(updated_at)")) {
            ps.setString(1, notifyUuid.toString());
            ps.setString(2, kind);
            ps.setString(3, group);
            ps.setDouble(4, amountG);
            ps.setTimestamp(5, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
    }

    public List<PendingRow> takePending(UUID notifyUuid) throws SQLException {
        List<PendingRow> out = new ArrayList<>();
        if (notifyUuid == null) {
            return out;
        }
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT kind, group_name, amount_g FROM " + tableName() + " WHERE notify_uuid = ? FOR UPDATE")) {
                    ps.setString(1, notifyUuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            double amount = rs.getDouble("amount_g");
                            if (amount > 0) {
                                out.add(new PendingRow(
                                        rs.getString("kind"),
                                        rs.getString("group_name"),
                                        amount));
                            }
                        }
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM " + tableName() + " WHERE notify_uuid = ?")) {
                    ps.setString(1, notifyUuid.toString());
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
        return out;
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(
                config.jdbcUrl(), config.mysqlUsername(), config.mysqlPassword());
    }
}
