package com.rootrecord.minecraft.rootessentials.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SpawnStore {

    private final MySqlSupport db;
    private final String table;

    public SpawnStore(MySqlSupport db, String tablePrefix) {
        this.db = db;
        this.table = tablePrefix + "spawn";
    }

    public void initSchema() throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + table + " (" +
                             "id TINYINT PRIMARY KEY," +
                             "world_name VARCHAR(64) NOT NULL," +
                             "x DOUBLE NOT NULL," +
                             "y DOUBLE NOT NULL," +
                             "z DOUBLE NOT NULL," +
                             "yaw FLOAT NOT NULL," +
                             "pitch FLOAT NOT NULL" +
                             ")")) {
            ps.executeUpdate();
        }
    }

    public void set(Location loc) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO " + table + " (id, world_name, x, y, z, yaw, pitch) VALUES (1, ?, ?, ?, ?, ?, ?) " +
                             "ON DUPLICATE KEY UPDATE world_name=VALUES(world_name), x=VALUES(x), y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch)")) {
            ps.setString(1, loc.getWorld() == null ? "world" : loc.getWorld().getName());
            ps.setDouble(2, loc.getX());
            ps.setDouble(3, loc.getY());
            ps.setDouble(4, loc.getZ());
            ps.setFloat(5, loc.getYaw());
            ps.setFloat(6, loc.getPitch());
            ps.executeUpdate();
        }
    }

    public Location get() throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT world_name, x, y, z, yaw, pitch FROM " + table + " WHERE id = 1 LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                var world = Bukkit.getWorld(rs.getString("world_name"));
                if (world == null) {
                    return null;
                }
                return new Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        rs.getFloat("yaw"), rs.getFloat("pitch"));
            }
        }
    }
}
