package com.rootrecord.minecraft.rootessentials.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class KitClaimStore {

    private final MySqlSupport db;
    private final String table;

    public KitClaimStore(MySqlSupport db, String tablePrefix) {
        this.db = db;
        this.table = tablePrefix + "kit_claims";
    }

    public void initSchema() throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + table + " (" +
                             "minecraft_uuid VARCHAR(36) NOT NULL," +
                             "kit_name VARCHAR(32) NOT NULL," +
                             "claimed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                             "PRIMARY KEY (minecraft_uuid, kit_name)" +
                             ")")) {
            ps.executeUpdate();
        }
    }

    public boolean hasClaimed(UUID uuid, String kit) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM " + table + " WHERE minecraft_uuid = ? AND kit_name = ? LIMIT 1")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kit.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void markClaimed(UUID uuid, String kit) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO " + table + " (minecraft_uuid, kit_name) VALUES (?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kit.toLowerCase());
            ps.executeUpdate();
        }
    }

    public boolean clearClaim(UUID uuid, String kit) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM " + table + " WHERE minecraft_uuid = ? AND kit_name = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kit.toLowerCase());
            return ps.executeUpdate() > 0;
        }
    }
}
