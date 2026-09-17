package com.rootrecord.minecraft.rootessentials.ender;

import com.rootrecord.minecraft.rootessentials.data.MySqlSupport;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Logger;

/** MySQL blob store for per-server 54-slot ender inventories. */
public final class DoubleEnderChestStore {

    private final MySqlSupport db;
    private final String table;
    private final String serverId;
    private final Logger log;

    public DoubleEnderChestStore(MySqlSupport db, String tablePrefix, String serverId, Logger log) {
        this.db = db;
        this.table = tablePrefix + "double_ender_chests";
        this.serverId = serverId == null || serverId.isBlank() ? "local" : serverId.trim().toLowerCase();
        this.log = log;
    }

    public String serverId() {
        return serverId;
    }

    public void initSchema() throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + table + " ("
                             + "server_id VARCHAR(64) NOT NULL,"
                             + "minecraft_uuid VARCHAR(36) NOT NULL,"
                             + "minecraft_username VARCHAR(32) NOT NULL DEFAULT '',"
                             + "contents_b64 MEDIUMTEXT NOT NULL,"
                             + "migrated_vanilla TINYINT(1) NOT NULL DEFAULT 0,"
                             + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                             + "PRIMARY KEY (server_id, minecraft_uuid)"
                             + ")")) {
            ps.executeUpdate();
        }
    }

    public record Row(ItemStack[] contents, boolean migratedVanilla) {}

    public Row load(UUID uuid) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT contents_b64, migrated_vanilla FROM " + table
                             + " WHERE server_id = ? AND minecraft_uuid = ? LIMIT 1")) {
            ps.setString(1, serverId);
            ps.setString(2, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                ItemStack[] contents = deserialize(rs.getString(1));
                boolean migrated = rs.getInt(2) != 0;
                return new Row(contents, migrated);
            }
        }
    }

    public void save(UUID uuid, String username, ItemStack[] contents, boolean migratedVanilla) throws SQLException {
        String b64 = serialize(contents);
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO " + table
                             + " (server_id, minecraft_uuid, minecraft_username, contents_b64, migrated_vanilla)"
                             + " VALUES (?, ?, ?, ?, ?)"
                             + " ON DUPLICATE KEY UPDATE minecraft_username = VALUES(minecraft_username),"
                             + " contents_b64 = VALUES(contents_b64),"
                             + " migrated_vanilla = GREATEST(migrated_vanilla, VALUES(migrated_vanilla))")) {
            ps.setString(1, serverId);
            ps.setString(2, uuid.toString());
            ps.setString(3, username == null ? "" : username);
            ps.setString(4, b64);
            ps.setInt(5, migratedVanilla ? 1 : 0);
            ps.executeUpdate();
        }
    }

    public static ItemStack[] emptyContents(int slots) {
        return new ItemStack[slots];
    }

    public static String serialize(ItemStack[] contents) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            BukkitObjectOutputStream oos = new BukkitObjectOutputStream(bos);
            int len = contents == null ? 0 : contents.length;
            oos.writeInt(len);
            for (int i = 0; i < len; i++) {
                oos.writeObject(contents[i]);
            }
            oos.close();
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception ex) {
            throw new IllegalStateException("ender serialize failed: " + ex.getMessage(), ex);
        }
    }

    public static ItemStack[] deserialize(String b64) {
        if (b64 == null || b64.isBlank()) {
            return emptyContents(54);
        }
        try {
            byte[] raw = Base64.getDecoder().decode(b64);
            BukkitObjectInputStream ois = new BukkitObjectInputStream(new ByteArrayInputStream(raw));
            int len = ois.readInt();
            ItemStack[] out = new ItemStack[Math.max(54, len)];
            for (int i = 0; i < len; i++) {
                Object obj = ois.readObject();
                if (i < out.length && obj instanceof ItemStack stack) {
                    out[i] = stack;
                }
            }
            ois.close();
            if (out.length != 54) {
                ItemStack[] fixed = new ItemStack[54];
                System.arraycopy(out, 0, fixed, 0, Math.min(54, out.length));
                return fixed;
            }
            return out;
        } catch (Exception ex) {
            return emptyContents(54);
        }
    }
}
