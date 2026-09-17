package com.rootrecord.minecraft.rootessentials.data;


import com.rootrecord.minecraft.common.GoldMoney;

import com.rootrecord.minecraft.rootessentials.goldfound.GoldFoundSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class GoldFoundStore {

    private final MySqlSupport db;
    private final String table;

    public GoldFoundStore(MySqlSupport db, String tablePrefix) {
        this.db = db;
        this.table = tablePrefix + "gold_found";
    }

    public void initSchema() throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + table + " ("
                             + "minecraft_uuid VARCHAR(36) PRIMARY KEY,"
                             + "minecraft_username VARCHAR(32) NOT NULL,"
                             + "total_gold_g DOUBLE NOT NULL DEFAULT 0,"
                             + "mined_ore_g DOUBLE NOT NULL DEFAULT 0,"
                             + "mined_block_g DOUBLE NOT NULL DEFAULT 0,"
                             + "loot_chest_g DOUBLE NOT NULL DEFAULT 0,"
                             + "loot_mob_g DOUBLE NOT NULL DEFAULT 0,"
                             + "pickup_g DOUBLE NOT NULL DEFAULT 0,"
                             + "find_events INT NOT NULL DEFAULT 0,"
                             + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                             + ")")) {
            ps.executeUpdate();
        }
    }

    public void record(UUID uuid, String username, double goldG, GoldFoundSource source) throws SQLException {
        if (uuid == null || goldG <= 0 || source == null) {
            return;
        }
        String name = username == null || username.isBlank() ? "Unknown" : username;
        String column = switch (source) {
            case MINED_ORE, SMELT -> "mined_ore_g";
            case MINED_BLOCK -> "mined_block_g";
            case LOOT_CHEST, LOOT_GENERATE -> "loot_chest_g";
            case LOOT_MOB, BARTER -> "loot_mob_g";
            case PICKUP, TRADE, FISHING -> "pickup_g";
        };
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO " + table + " (minecraft_uuid, minecraft_username, total_gold_g, "
                             + column + ", find_events) VALUES (?, ?, ?, ?, 1) "
                             + "ON DUPLICATE KEY UPDATE "
                             + "minecraft_username = VALUES(minecraft_username), "
                             + "total_gold_g = total_gold_g + VALUES(total_gold_g), "
                             + column + " = " + column + " + VALUES(" + column + "), "
                             + "find_events = find_events + 1")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setDouble(3, round(goldG));
            ps.setDouble(4, round(goldG));
            ps.executeUpdate();
        }
    }

    public List<GoldFoundRow> allForSync() throws SQLException {
        List<GoldFoundRow> out = new ArrayList<>();
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT minecraft_uuid, minecraft_username, total_gold_g, mined_ore_g, mined_block_g,"
                             + " loot_chest_g, loot_mob_g, pickup_g, find_events FROM " + table
                             + " WHERE total_gold_g > 0 ORDER BY total_gold_g DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new GoldFoundRow(
                        rs.getString("minecraft_uuid"),
                        rs.getString("minecraft_username"),
                        rs.getDouble("total_gold_g"),
                        rs.getDouble("mined_ore_g"),
                        rs.getDouble("mined_block_g"),
                        rs.getDouble("loot_chest_g"),
                        rs.getDouble("loot_mob_g"),
                        rs.getDouble("pickup_g"),
                        rs.getInt("find_events")));
            }
        }
        return out;
    }

    /** All-time gold-found sum across players (list totals snapshot). */
    public double sumTotalGoldFound() throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(total_gold_g), 0) FROM " + table);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? round(rs.getDouble(1)) : 0;
        }
    }

    private static double round(double value) {
        return GoldMoney.round(value);
    }

    public record GoldFoundRow(
            String uuid,
            String username,
            double totalGoldG,
            double minedOreG,
            double minedBlockG,
            double lootChestG,
            double lootMobG,
            double pickupG,
            int findEvents) {}
}
