package com.rootrecord.minecraft.rootessentials.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MailStore {

    public record MailRow(long id, UUID fromUuid, String fromName, String subject, String body, boolean read, Instant createdAt) {}

    private final MySqlSupport db;
    private final String table;

    public MailStore(MySqlSupport db, String tablePrefix) {
        this.db = db;
        this.table = tablePrefix + "mail";
    }

    public void initSchema() throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + table + " (" +
                             "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                             "to_uuid VARCHAR(36) NOT NULL," +
                             "from_uuid VARCHAR(36) NOT NULL," +
                             "from_name VARCHAR(32) NOT NULL," +
                             "subject VARCHAR(64) NOT NULL," +
                             "body VARCHAR(512) NOT NULL," +
                             "is_read TINYINT(1) NOT NULL DEFAULT 0," +
                             "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                             "INDEX idx_mail_to (to_uuid)" +
                             ")")) {
            ps.executeUpdate();
        }
    }

    public void send(UUID to, UUID from, String fromName, String subject, String body) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO " + table + " (to_uuid, from_uuid, from_name, subject, body) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, to.toString());
            ps.setString(2, from.toString());
            ps.setString(3, fromName);
            ps.setString(4, subject);
            ps.setString(5, body);
            ps.executeUpdate();
        }
    }

    public List<MailRow> list(UUID to) throws SQLException {
        List<MailRow> rows = new ArrayList<>();
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, from_uuid, from_name, subject, body, is_read, created_at FROM " + table +
                             " WHERE to_uuid = ? ORDER BY id DESC LIMIT 25")) {
            ps.setString(1, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new MailRow(
                            rs.getLong("id"),
                            UUID.fromString(rs.getString("from_uuid")),
                            rs.getString("from_name"),
                            rs.getString("subject"),
                            rs.getString("body"),
                            rs.getBoolean("is_read"),
                            rs.getTimestamp("created_at").toInstant()));
                }
            }
        }
        return rows;
    }

    public MailRow read(UUID to, long id) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, from_uuid, from_name, subject, body, is_read, created_at FROM " + table +
                             " WHERE to_uuid = ? AND id = ? LIMIT 1")) {
            ps.setString(1, to.toString());
            ps.setLong(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                try (PreparedStatement mark = c.prepareStatement(
                        "UPDATE " + table + " SET is_read = 1 WHERE id = ?")) {
                    mark.setLong(1, id);
                    mark.executeUpdate();
                }
                return new MailRow(
                        rs.getLong("id"),
                        UUID.fromString(rs.getString("from_uuid")),
                        rs.getString("from_name"),
                        rs.getString("subject"),
                        rs.getString("body"),
                        true,
                        rs.getTimestamp("created_at").toInstant());
            }
        }
    }

    public int clear(UUID to) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement("DELETE FROM " + table + " WHERE to_uuid = ?")) {
            ps.setString(1, to.toString());
            return ps.executeUpdate();
        }
    }
}
