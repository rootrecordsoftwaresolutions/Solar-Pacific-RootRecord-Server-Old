package com.rootrecord.minecraft.rootessentials.data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class ModerationStore {

    public record Punishment(String username, String reason, String actor, Instant expiresAt, String bannedIp) {
        public Punishment(String username, String reason, String actor, Instant expiresAt) {
            this(username, reason, actor, expiresAt, null);
        }
    }

    private final MySqlSupport db;
    private final String bans;
    private final String mutes;
    private final String ipBans;
    private final String lastIps;

    public ModerationStore(MySqlSupport db, String tablePrefix) {
        this.db = db;
        this.bans = tablePrefix + "bans";
        this.mutes = tablePrefix + "mutes";
        this.ipBans = tablePrefix + "ip_bans";
        this.lastIps = tablePrefix + "player_last_ip";
    }

    public void initSchema() throws SQLException {
        try (Connection c = db.open()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + bans + " (" +
                            "minecraft_uuid VARCHAR(36) PRIMARY KEY," +
                            "minecraft_username VARCHAR(32) NOT NULL," +
                            "reason VARCHAR(255) NOT NULL," +
                            "banned_by VARCHAR(32) NOT NULL," +
                            "banned_ip VARCHAR(45) NULL," +
                            "expires_at TIMESTAMP NULL," +
                            "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                            ")")) {
                ps.executeUpdate();
            }
            ensureColumn(c, bans, "banned_ip", "VARCHAR(45) NULL");
            try (PreparedStatement ps = c.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + mutes + " (" +
                            "minecraft_uuid VARCHAR(36) PRIMARY KEY," +
                            "minecraft_username VARCHAR(32) NOT NULL," +
                            "reason VARCHAR(255) NOT NULL," +
                            "muted_by VARCHAR(32) NOT NULL," +
                            "expires_at TIMESTAMP NULL," +
                            "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                            ")")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + ipBans + " (" +
                            "ip_address VARCHAR(45) PRIMARY KEY," +
                            "minecraft_uuid VARCHAR(36) NULL," +
                            "minecraft_username VARCHAR(32) NULL," +
                            "reason VARCHAR(255) NOT NULL," +
                            "banned_by VARCHAR(32) NOT NULL," +
                            "expires_at TIMESTAMP NULL," +
                            "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                            ")")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + lastIps + " (" +
                            "minecraft_uuid VARCHAR(36) PRIMARY KEY," +
                            "ip_address VARCHAR(45) NOT NULL," +
                            "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                            ")")) {
                ps.executeUpdate();
            }
        }
    }

    private static void ensureColumn(Connection c, String table, String column, String definition) {
        try (PreparedStatement ps = c.prepareStatement(
                "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition)) {
            ps.executeUpdate();
        } catch (SQLException ignored) {
            // column already exists
        }
    }

    public void recordLastIp(UUID uuid, String ip) throws SQLException {
        if (uuid == null || ip == null || ip.isBlank()) {
            return;
        }
        String normalized = normalizeIp(ip);
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO " + lastIps + " (minecraft_uuid, ip_address) VALUES (?, ?) " +
                             "ON DUPLICATE KEY UPDATE ip_address=VALUES(ip_address)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, normalized);
            ps.executeUpdate();
        }
    }

    public Optional<String> lastIp(UUID uuid) throws SQLException {
        if (uuid == null) {
            return Optional.empty();
        }
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT ip_address FROM " + lastIps + " WHERE minecraft_uuid = ? LIMIT 1")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String ip = rs.getString(1);
                return ip == null || ip.isBlank() ? Optional.empty() : Optional.of(ip);
            }
        }
    }

    public void ban(UUID uuid, String username, String reason, String actor, Instant expiresAt) throws SQLException {
        ban(uuid, username, reason, actor, expiresAt, null);
    }

    public void ban(UUID uuid, String username, String reason, String actor, Instant expiresAt, String ip)
            throws SQLException {
        String normalizedIp = normalizeIp(ip);
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO " + bans
                             + " (minecraft_uuid, minecraft_username, reason, banned_by, banned_ip, expires_at) VALUES (?, ?, ?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE minecraft_username=VALUES(minecraft_username), reason=VALUES(reason), "
                             + "banned_by=VALUES(banned_by), banned_ip=VALUES(banned_ip), expires_at=VALUES(expires_at)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, username);
            ps.setString(3, reason);
            ps.setString(4, actor);
            ps.setString(5, normalizedIp);
            ps.setTimestamp(6, expiresAt == null ? null : Timestamp.from(expiresAt));
            ps.executeUpdate();
        }
        if (normalizedIp != null) {
            banIp(normalizedIp, uuid, username, reason, actor, expiresAt);
        }
    }

    public void banIp(String ip, UUID uuid, String username, String reason, String actor, Instant expiresAt)
            throws SQLException {
        String normalizedIp = normalizeIp(ip);
        if (normalizedIp == null) {
            return;
        }
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO " + ipBans
                             + " (ip_address, minecraft_uuid, minecraft_username, reason, banned_by, expires_at) VALUES (?, ?, ?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE minecraft_uuid=VALUES(minecraft_uuid), minecraft_username=VALUES(minecraft_username), "
                             + "reason=VALUES(reason), banned_by=VALUES(banned_by), expires_at=VALUES(expires_at)")) {
            ps.setString(1, normalizedIp);
            ps.setString(2, uuid == null ? null : uuid.toString());
            ps.setString(3, username);
            ps.setString(4, reason);
            ps.setString(5, actor);
            ps.setTimestamp(6, expiresAt == null ? null : Timestamp.from(expiresAt));
            ps.executeUpdate();
        }
    }

    public void unban(UUID uuid) throws SQLException {
        String ip = null;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT banned_ip FROM " + bans + " WHERE minecraft_uuid = ? LIMIT 1")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ip = rs.getString(1);
                }
            }
        }
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement("DELETE FROM " + bans + " WHERE minecraft_uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
        if (ip != null && !ip.isBlank()) {
            unbanIp(ip);
        }
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement("DELETE FROM " + ipBans + " WHERE minecraft_uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    public void unbanIp(String ip) throws SQLException {
        String normalizedIp = normalizeIp(ip);
        if (normalizedIp == null) {
            return;
        }
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement("DELETE FROM " + ipBans + " WHERE ip_address = ?")) {
            ps.setString(1, normalizedIp);
            ps.executeUpdate();
        }
    }

    public Optional<Punishment> activeBan(UUID uuid) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT minecraft_username, reason, banned_by, expires_at, banned_ip FROM " + bans
                             + " WHERE minecraft_uuid = ? LIMIT 1")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Instant expires = rs.getTimestamp("expires_at") == null
                        ? null
                        : rs.getTimestamp("expires_at").toInstant();
                if (expires != null && expires.isBefore(Instant.now())) {
                    unban(uuid);
                    return Optional.empty();
                }
                return Optional.of(new Punishment(
                        rs.getString("minecraft_username"),
                        rs.getString("reason"),
                        rs.getString("banned_by"),
                        expires,
                        rs.getString("banned_ip")));
            }
        }
    }

    public Optional<Punishment> activeIpBan(String ip) throws SQLException {
        String normalizedIp = normalizeIp(ip);
        if (normalizedIp == null) {
            return Optional.empty();
        }
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT minecraft_username, reason, banned_by, expires_at, ip_address FROM " + ipBans
                             + " WHERE ip_address = ? LIMIT 1")) {
            ps.setString(1, normalizedIp);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Instant expires = rs.getTimestamp("expires_at") == null
                        ? null
                        : rs.getTimestamp("expires_at").toInstant();
                if (expires != null && expires.isBefore(Instant.now())) {
                    unbanIp(normalizedIp);
                    return Optional.empty();
                }
                return Optional.of(new Punishment(
                        rs.getString("minecraft_username"),
                        rs.getString("reason"),
                        rs.getString("banned_by"),
                        expires,
                        rs.getString("ip_address")));
            }
        }
    }

    public void mute(UUID uuid, String username, String reason, String actor, Instant expiresAt) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO " + mutes + " (minecraft_uuid, minecraft_username, reason, muted_by, expires_at) VALUES (?, ?, ?, ?, ?) " +
                             "ON DUPLICATE KEY UPDATE minecraft_username=VALUES(minecraft_username), reason=VALUES(reason), muted_by=VALUES(muted_by), expires_at=VALUES(expires_at)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, username);
            ps.setString(3, reason);
            ps.setString(4, actor);
            ps.setTimestamp(5, expiresAt == null ? null : Timestamp.from(expiresAt));
            ps.executeUpdate();
        }
    }

    public void unmute(UUID uuid) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement("DELETE FROM " + mutes + " WHERE minecraft_uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    public Optional<Punishment> activeMute(UUID uuid) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT minecraft_username, reason, muted_by, expires_at FROM " + mutes + " WHERE minecraft_uuid = ? LIMIT 1")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Instant expires = rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant();
                if (expires != null && expires.isBefore(Instant.now())) {
                    unmute(uuid);
                    return Optional.empty();
                }
                return Optional.of(new Punishment(
                        rs.getString("minecraft_username"),
                        rs.getString("reason"),
                        rs.getString("muted_by"),
                        expires));
            }
        }
    }

    public static String normalizeIp(String ip) {
        if (ip == null) {
            return null;
        }
        String trimmed = ip.trim();
        if (trimmed.isEmpty() || "unknown".equalsIgnoreCase(trimmed)) {
            return null;
        }
        if (trimmed.startsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(1);
        }
        int pct = trimmed.indexOf('%');
        if (pct > 0) {
            trimmed = trimmed.substring(0, pct);
        }
        return trimmed;
    }
}
