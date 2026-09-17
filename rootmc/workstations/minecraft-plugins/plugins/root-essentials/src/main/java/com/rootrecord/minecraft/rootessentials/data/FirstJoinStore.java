package com.rootrecord.minecraft.rootessentials.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class FirstJoinStore {

    private final MySqlSupport db;
    private final String table;

    public FirstJoinStore(MySqlSupport db, String tablePrefix) {
        this.db = db;
        this.table = tablePrefix + "player_first_join";
    }

    public void initSchema() throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + table + " (" +
                             "minecraft_uuid VARCHAR(36) PRIMARY KEY," +
                             "first_join_ms BIGINT NOT NULL" +
                             ")")) {
            ps.executeUpdate();
        }
    }

    /** Returns stored first-join epoch ms, recording now when missing. */
    public long ensureFirstJoinMs(UUID uuid) throws SQLException {
        long now = System.currentTimeMillis();
        try (Connection c = db.open();
             PreparedStatement insert = c.prepareStatement(
                     "INSERT IGNORE INTO " + table + " (minecraft_uuid, first_join_ms) VALUES (?, ?)")) {
            insert.setString(1, uuid.toString());
            insert.setLong(2, now);
            insert.executeUpdate();
        }
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT first_join_ms FROM " + table + " WHERE minecraft_uuid = ? LIMIT 1")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return now;
    }

    public long firstJoinMs(UUID uuid) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT first_join_ms FROM " + table + " WHERE minecraft_uuid = ? LIMIT 1")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }
}
