package com.rootrecord.minecraft.rootessentials.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class PlayerPrefsStore {

    private final MySqlSupport db;
    private final String table;

    public PlayerPrefsStore(MySqlSupport db, String tablePrefix) {
        this.db = db;
        this.table = tablePrefix + "player_prefs";
    }

    public void initSchema() throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + table + " (" +
                             "minecraft_uuid VARCHAR(36) PRIMARY KEY," +
                             "pay_enabled TINYINT(1) NOT NULL DEFAULT 0" +
                             ")")) {
            ps.executeUpdate();
        }
    }

    public boolean acceptsPay(UUID uuid) throws SQLException {
        ensureRow(uuid);
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT pay_enabled FROM " + table + " WHERE minecraft_uuid = ? LIMIT 1")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    public boolean togglePay(UUID uuid) throws SQLException {
        ensureRow(uuid);
        boolean next = !acceptsPay(uuid);
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE " + table + " SET pay_enabled = ? WHERE minecraft_uuid = ?")) {
            ps.setBoolean(1, next);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
        return next;
    }

    private void ensureRow(UUID uuid) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT IGNORE INTO " + table + " (minecraft_uuid, pay_enabled) VALUES (?, 0)")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }
}
