package com.rootrecord.minecraft.rootessentials.data;


import com.rootrecord.minecraft.common.GoldMoney;

import com.rootrecord.minecraft.rootessentials.goldfound.GoldItemEventType;
import org.bukkit.Location;
import org.bukkit.Material;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class GoldItemEventStore {

    private final MySqlSupport db;
    private final String table;

    public GoldItemEventStore(MySqlSupport db, String tablePrefix) {
        this.db = db;
        this.table = tablePrefix + "gold_item_events";
    }

    public void initSchema() throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + table + " ("
                             + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                             + "minecraft_uuid VARCHAR(36) NOT NULL,"
                             + "minecraft_username VARCHAR(32) NOT NULL,"
                             + "event_type VARCHAR(24) NOT NULL,"
                             + "obtained_via VARCHAR(32) NOT NULL,"
                             + "material VARCHAR(64) NOT NULL,"
                             + "stack_amount INT NOT NULL,"
                             + "gold_g DOUBLE NOT NULL,"
                             + "world VARCHAR(64) NULL,"
                             + "block_x INT NULL,"
                             + "block_y INT NULL,"
                             + "block_z INT NULL,"
                             + "context_json TEXT NULL,"
                             + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                             + "INDEX idx_uuid_time (minecraft_uuid, created_at),"
                             + "INDEX idx_id (id)"
                             + ")")) {
            ps.executeUpdate();
        }
    }

    public long record(
            UUID uuid,
            String username,
            GoldItemEventType eventType,
            String obtainedVia,
            Material material,
            int stackAmount,
            double goldG,
            Location location,
            String contextJson) throws SQLException {
        if (uuid == null || eventType == null || material == null || stackAmount <= 0) {
            return -1;
        }
        String name = username == null || username.isBlank() ? "Unknown" : username;
        String via = obtainedVia == null || obtainedVia.isBlank()
                ? eventType.name()
                : obtainedVia.toUpperCase(Locale.ROOT);
        String world = location != null && location.getWorld() != null ? location.getWorld().getName() : null;
        Integer x = location != null ? location.getBlockX() : null;
        Integer y = location != null ? location.getBlockY() : null;
        Integer z = location != null ? location.getBlockZ() : null;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO " + table + " ("
                             + "minecraft_uuid, minecraft_username, event_type, obtained_via,"
                             + "material, stack_amount, gold_g, world, block_x, block_y, block_z, context_json"
                             + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, eventType.name());
            ps.setString(4, via);
            ps.setString(5, material.name());
            ps.setInt(6, stackAmount);
            ps.setDouble(7, round(goldG));
            if (world != null) {
                ps.setString(8, world);
            } else {
                ps.setNull(8, java.sql.Types.VARCHAR);
            }
            setNullableInt(ps, 9, x);
            setNullableInt(ps, 10, y);
            setNullableInt(ps, 11, z);
            if (contextJson != null && !contextJson.isBlank()) {
                ps.setString(12, contextJson);
            } else {
                ps.setNull(12, java.sql.Types.LONGVARCHAR);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return -1;
    }

    public List<EventRow> eventsAfter(long afterId, int limit) throws SQLException {
        int capped = Math.min(500, Math.max(1, limit));
        List<EventRow> out = new ArrayList<>();
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, minecraft_uuid, minecraft_username, event_type, obtained_via,"
                             + " material, stack_amount, gold_g, world, block_x, block_y, block_z,"
                             + " context_json, created_at"
                             + " FROM " + table
                             + " WHERE id > ? ORDER BY id ASC LIMIT ?")) {
            ps.setLong(1, Math.max(0, afterId));
            ps.setInt(2, capped);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new EventRow(
                            rs.getLong("id"),
                            rs.getString("minecraft_uuid"),
                            rs.getString("minecraft_username"),
                            rs.getString("event_type"),
                            rs.getString("obtained_via"),
                            rs.getString("material"),
                            rs.getInt("stack_amount"),
                            rs.getDouble("gold_g"),
                            rs.getString("world"),
                            (Integer) rs.getObject("block_x"),
                            (Integer) rs.getObject("block_y"),
                            (Integer) rs.getObject("block_z"),
                            rs.getString("context_json"),
                            formatTimestamp(rs.getTimestamp("created_at"))));
                }
            }
        }
        return out;
    }

    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static String formatTimestamp(Timestamp ts) {
        if (ts == null) {
            return Instant.now().toString();
        }
        return ts.toInstant().toString();
    }

    private static double round(double value) {
        return GoldMoney.round(value);
    }

    public record EventRow(
            long eventId,
            String uuid,
            String username,
            String eventType,
            String obtainedVia,
            String material,
            int stackAmount,
            double goldG,
            String world,
            Integer blockX,
            Integer blockY,
            Integer blockZ,
            String contextJson,
            String createdAt) {}
}
