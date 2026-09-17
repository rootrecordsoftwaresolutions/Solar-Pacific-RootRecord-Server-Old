package com.rootrecord.minecraft.rootessentials.data;

import com.rootrecord.minecraft.common.GoldMoney;
import org.bukkit.Material;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Lifetime per-player inventory of every gold-related material obtained
 * (ores at 0 G; raw/ingot/nugget/block at mint peg).
 */
public final class GoldMaterialObtainedStore {

    private final MySqlSupport db;
    private final String table;
    private final String serverTable;

    public GoldMaterialObtainedStore(MySqlSupport db, String tablePrefix) {
        this.db = db;
        this.table = tablePrefix + "gold_material_obtained";
        this.serverTable = tablePrefix + "gold_material_server";
    }

    public void initSchema() throws SQLException {
        try (Connection c = db.open()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + table + " ("
                            + "minecraft_uuid VARCHAR(36) NOT NULL,"
                            + "minecraft_username VARCHAR(32) NOT NULL,"
                            + "material VARCHAR(64) NOT NULL,"
                            + "items_amount BIGINT NOT NULL DEFAULT 0,"
                            + "gold_g DOUBLE NOT NULL DEFAULT 0,"
                            + "events_count INT NOT NULL DEFAULT 0,"
                            + "valued TINYINT(1) NOT NULL DEFAULT 0,"
                            + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                            + "PRIMARY KEY (minecraft_uuid, material),"
                            + "INDEX idx_material (material),"
                            + "INDEX idx_gold (gold_g)"
                            + ")")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + serverTable + " ("
                            + "material VARCHAR(64) NOT NULL PRIMARY KEY,"
                            + "items_amount BIGINT NOT NULL DEFAULT 0,"
                            + "gold_g DOUBLE NOT NULL DEFAULT 0,"
                            + "events_count INT NOT NULL DEFAULT 0,"
                            + "valued TINYINT(1) NOT NULL DEFAULT 0,"
                            + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                            + ")")) {
                ps.executeUpdate();
            }
        }
    }

    /**
     * @param goldG mint-peg G for this stack (0 for ores / non-valued forms)
     */
    public void record(
            UUID uuid,
            String username,
            Material material,
            int stackAmount,
            double goldG) throws SQLException {
        if (uuid == null || material == null || stackAmount <= 0) {
            return;
        }
        double g = round(Math.max(0, goldG));
        if (g <= 0) {
            return;
        }
        String name = username == null || username.isBlank() ? "Unknown" : username;
        String mat = material.name();
        boolean valued = true;
        try (Connection c = db.open()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + table + " (minecraft_uuid, minecraft_username, material,"
                            + " items_amount, gold_g, events_count, valued) VALUES (?, ?, ?, ?, ?, 1, ?) "
                            + "ON DUPLICATE KEY UPDATE "
                            + "minecraft_username = VALUES(minecraft_username), "
                            + "items_amount = items_amount + VALUES(items_amount), "
                            + "gold_g = gold_g + VALUES(gold_g), "
                            + "events_count = events_count + 1, "
                            + "valued = GREATEST(valued, VALUES(valued))")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setString(3, mat);
                ps.setLong(4, stackAmount);
                ps.setDouble(5, g);
                ps.setInt(6, valued ? 1 : 0);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + serverTable + " (material, items_amount, gold_g, events_count, valued)"
                            + " VALUES (?, ?, ?, 1, ?) "
                            + "ON DUPLICATE KEY UPDATE "
                            + "items_amount = items_amount + VALUES(items_amount), "
                            + "gold_g = gold_g + VALUES(gold_g), "
                            + "events_count = events_count + 1, "
                            + "valued = GREATEST(valued, VALUES(valued))")) {
                ps.setString(1, mat);
                ps.setLong(2, stackAmount);
                ps.setDouble(3, g);
                ps.setInt(4, valued ? 1 : 0);
                ps.executeUpdate();
            }
        }
    }

    public List<PlayerMaterialRow> allPlayersForSync() throws SQLException {
        List<PlayerMaterialRow> out = new ArrayList<>();
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT minecraft_uuid, minecraft_username, material, items_amount, gold_g,"
                             + " events_count, valued FROM " + table
                             + " WHERE gold_g > 0 OR valued = 1"
                             + " ORDER BY minecraft_uuid, material");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new PlayerMaterialRow(
                        rs.getString("minecraft_uuid"),
                        rs.getString("minecraft_username"),
                        rs.getString("material"),
                        rs.getLong("items_amount"),
                        rs.getDouble("gold_g"),
                        rs.getInt("events_count"),
                        rs.getInt("valued") != 0));
            }
        }
        return out;
    }

    public List<ServerMaterialRow> serverTotals() throws SQLException {
        List<ServerMaterialRow> out = new ArrayList<>();
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT material, items_amount, gold_g, events_count, valued FROM " + serverTable
                             + " WHERE gold_g > 0 OR valued = 1"
                             + " ORDER BY material");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new ServerMaterialRow(
                        rs.getString("material"),
                        rs.getLong("items_amount"),
                        rs.getDouble("gold_g"),
                        rs.getInt("events_count"),
                        rs.getInt("valued") != 0));
            }
        }
        return out;
    }

    public PlayerTotals playerTotals(UUID uuid) throws SQLException {
        if (uuid == null) {
            return new PlayerTotals(0, 0, 0, 0);
        }
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(items_amount),0), COALESCE(SUM(gold_g),0),"
                             + " COALESCE(SUM(events_count),0),"
                             + " COALESCE(SUM(CASE WHEN valued=1 THEN gold_g ELSE 0 END),0)"
                             + " FROM " + table + " WHERE minecraft_uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlayerTotals(
                            rs.getLong(1),
                            rs.getDouble(2),
                            rs.getInt(3),
                            rs.getDouble(4));
                }
            }
        }
        return new PlayerTotals(0, 0, 0, 0);
    }

    private static double round(double value) {
        return GoldMoney.round(value);
    }

    public record PlayerMaterialRow(
            String uuid,
            String username,
            String material,
            long itemsAmount,
            double goldG,
            int eventsCount,
            boolean valued) {}

    public record ServerMaterialRow(
            String material,
            long itemsAmount,
            double goldG,
            int eventsCount,
            boolean valued) {}

    public record PlayerTotals(
            long totalItems,
            double totalGoldG,
            int eventsCount,
            double valuedGoldG) {}
}
