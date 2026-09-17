package com.rootrecord.minecraft.rootessentials.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WarpStore {

    private final MySqlSupport db;
    private final String table;

    public WarpStore(MySqlSupport db, String tablePrefix) {
        this.db = db;
        this.table = tablePrefix + "warps";
    }

    public void initSchema() throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + table + " (" +
                             "warp_name VARCHAR(32) PRIMARY KEY," +
                             "world_name VARCHAR(64) NOT NULL," +
                             "x DOUBLE NOT NULL," +
                             "y DOUBLE NOT NULL," +
                             "z DOUBLE NOT NULL," +
                             "yaw FLOAT NOT NULL," +
                             "pitch FLOAT NOT NULL," +
                             "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                             ")")) {
            ps.executeUpdate();
        }
    }

    public void upsert(String name, Location loc) throws SQLException {
        String key = name.toLowerCase(Locale.ROOT);
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO " + table + " (warp_name, world_name, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                             "ON DUPLICATE KEY UPDATE world_name=VALUES(world_name), x=VALUES(x), y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch)")) {
            ps.setString(1, key);
            ps.setString(2, loc.getWorld() == null ? "world" : loc.getWorld().getName());
            ps.setDouble(3, loc.getX());
            ps.setDouble(4, loc.getY());
            ps.setDouble(5, loc.getZ());
            ps.setFloat(6, loc.getYaw());
            ps.setFloat(7, loc.getPitch());
            ps.executeUpdate();
        }
    }

    public boolean delete(String name) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement("DELETE FROM " + table + " WHERE warp_name = ?")) {
            ps.setString(1, name.toLowerCase(Locale.ROOT));
            return ps.executeUpdate() > 0;
        }
    }

    public Location get(String name) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT world_name, x, y, z, yaw, pitch FROM " + table + " WHERE warp_name = ? LIMIT 1")) {
            ps.setString(1, name.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                var world = Bukkit.getWorld(rs.getString("world_name"));
                if (world == null) return null;
                return new Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        rs.getFloat("yaw"), rs.getFloat("pitch"));
            }
        }
    }

    public List<String> names() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement("SELECT warp_name FROM " + table + " ORDER BY warp_name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(rs.getString(1));
        }
        return out;
    }
}
