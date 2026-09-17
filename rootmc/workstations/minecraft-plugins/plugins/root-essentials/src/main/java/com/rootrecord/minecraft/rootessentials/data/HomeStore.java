package com.rootrecord.minecraft.rootessentials.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;

public final class HomeStore {

    private final MySqlSupport db;
    private final String table;

    public HomeStore(MySqlSupport db, String tablePrefix) {
        this.db = db;
        this.table = tablePrefix + "homes";
    }

    public void initSchema() throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + table + " (" +
                             "minecraft_uuid VARCHAR(36) NOT NULL," +
                             "home_name VARCHAR(32) NOT NULL," +
                             "world_name VARCHAR(64) NOT NULL," +
                             "x DOUBLE NOT NULL," +
                             "y DOUBLE NOT NULL," +
                             "z DOUBLE NOT NULL," +
                             "yaw FLOAT NOT NULL," +
                             "pitch FLOAT NOT NULL," +
                             "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                             "PRIMARY KEY (minecraft_uuid, home_name)" +
                             ")")) {
            ps.executeUpdate();
        }
    }

    public int count(UUID uuid) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM " + table + " WHERE minecraft_uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void upsert(UUID uuid, String name, Location loc) throws SQLException {
        String key = name.toLowerCase(Locale.ROOT);
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO " + table + " " +
                             "(minecraft_uuid, home_name, world_name, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                             "ON DUPLICATE KEY UPDATE world_name=VALUES(world_name), x=VALUES(x), y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, key);
            ps.setString(3, loc.getWorld() == null ? "world" : loc.getWorld().getName());
            ps.setDouble(4, loc.getX());
            ps.setDouble(5, loc.getY());
            ps.setDouble(6, loc.getZ());
            ps.setFloat(7, loc.getYaw());
            ps.setFloat(8, loc.getPitch());
            ps.executeUpdate();
        }
    }

    public boolean delete(UUID uuid, String name) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM " + table + " WHERE minecraft_uuid = ? AND home_name = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name.toLowerCase(Locale.ROOT));
            return ps.executeUpdate() > 0;
        }
    }

    public boolean rename(UUID uuid, String from, String to) throws SQLException {
        String oldKey = from.toLowerCase(Locale.ROOT);
        String newKey = to.toLowerCase(Locale.ROOT);
        if (get(uuid, newKey) != null) {
            return false;
        }
        Location loc = get(uuid, oldKey);
        if (loc == null) {
            return false;
        }
        delete(uuid, oldKey);
        upsert(uuid, newKey, loc);
        return true;
    }

    public java.util.List<String> listNames(UUID uuid) throws SQLException {
        java.util.List<String> names = new java.util.ArrayList<>();
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT home_name FROM " + table + " WHERE minecraft_uuid = ? ORDER BY home_name")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString(1));
            }
        }
        return names;
    }

    public Location get(UUID uuid, String name) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT world_name, x, y, z, yaw, pitch FROM " + table + " WHERE minecraft_uuid = ? AND home_name = ? LIMIT 1")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                World world = Bukkit.getWorld(rs.getString("world_name"));
                if (world == null) return null;
                return new Location(world,
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getFloat("yaw"),
                        rs.getFloat("pitch"));
            }
        }
    }
}
