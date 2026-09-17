package com.rootrecord.minecraft.rootstat.mysql;

import com.rootrecord.minecraft.rootstat.config.RootStatConfig;
import com.rootrecord.minecraft.rootstat.model.McMMOPlayerSnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Reads Root-Skills levels from {@code root_skills_users} + {@code root_skills_skills}
 * (normalized {@code skill_id} rows). Builds the same {@link McMMOPlayerSnapshot} shape
 * used by cloud {@code /mcmmo/sync} until that endpoint is renamed.
 */
public final class RootSkillsStatsReader {

    /** Canonical skill ids — power = sum of levels. */
    public static final String[] SKILL_IDS = {
        "mining",
        "woodcutting",
        "herbalism",
        "excavation",
        "fishing",
        "repair",
        "salvage",
        "smelting",
        "alchemy",
        "taming",
        "acrobatics",
        "unarmed",
        "swords",
        "axes",
        "archery",
        "crossbows",
        "tridents",
        "maces",
        "spears",
        "defense",
        "elytra",
    };

    private final RootStatConfig config;
    private final Supplier<Connection> connectionSupplier;

    public RootSkillsStatsReader(RootStatConfig config, Supplier<Connection> connectionSupplier) {
        this.config = config;
        this.connectionSupplier = connectionSupplier;
    }

    public List<McMMOPlayerSnapshot> readAll() throws SQLException {
        String users = config.skillsTablePrefix() + "users";
        String skills = config.skillsTablePrefix() + "skills";
        String sql =
                "SELECT u.uuid, u.username, s.skill_id, s.level FROM "
                        + users
                        + " u LEFT JOIN "
                        + skills
                        + " s ON u.uuid = s.uuid WHERE u.uuid IS NOT NULL AND u.uuid <> ''";

        Map<String, Acc> byUuid = new LinkedHashMap<>();
        try (Connection c = connectionSupplier.get();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String uuid = normalizeUuid(rs.getString("uuid"));
                if (uuid == null) {
                    continue;
                }
                Acc acc = byUuid.computeIfAbsent(uuid, Acc::new);
                String username = rs.getString("username");
                if (username != null && !username.isBlank()) {
                    acc.username = username;
                }
                String skillId = rs.getString("skill_id");
                if (skillId == null || skillId.isBlank()) {
                    continue;
                }
                String key = skillId.trim().toLowerCase(Locale.ROOT);
                int level = Math.max(0, rs.getInt("level"));
                acc.skills.put(key, level);
            }
        }

        List<McMMOPlayerSnapshot> out = new ArrayList<>(byUuid.size());
        for (Acc acc : byUuid.values()) {
            ensureAllSkills(acc.skills);
            int power = 0;
            for (int level : acc.skills.values()) {
                power += level;
            }
            out.add(new McMMOPlayerSnapshot(acc.uuid, acc.username, power, acc.skills));
        }
        return out;
    }

    public Optional<Integer> powerByUsername(String username) throws SQLException {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        String users = config.skillsTablePrefix() + "users";
        String skills = config.skillsTablePrefix() + "skills";
        String sql =
                "SELECT COALESCE(SUM(s.level), 0) AS power FROM "
                        + users
                        + " u LEFT JOIN "
                        + skills
                        + " s ON u.uuid = s.uuid WHERE LOWER(u.username) = LOWER(?) LIMIT 1";
        try (Connection c = connectionSupplier.get();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                // SUM with no matching user returns no row; with user but no skills returns 0
                return Optional.of(Math.max(0, rs.getInt("power")));
            }
        }
    }

    /**
     * Level for one skill, or empty if the player has no root-skills user row.
     */
    public Optional<Integer> levelByUsername(String username, String skillId) throws SQLException {
        if (username == null || username.isBlank() || skillId == null || skillId.isBlank()) {
            return Optional.empty();
        }
        String users = config.skillsTablePrefix() + "users";
        String skills = config.skillsTablePrefix() + "skills";
        String sql =
                "SELECT s.level FROM "
                        + users
                        + " u LEFT JOIN "
                        + skills
                        + " s ON u.uuid = s.uuid AND LOWER(s.skill_id) = LOWER(?) "
                        + "WHERE LOWER(u.username) = LOWER(?) LIMIT 1";
        try (Connection c = connectionSupplier.get();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, skillId.trim());
            ps.setString(2, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                int level = rs.getInt("level");
                if (rs.wasNull()) {
                    return Optional.of(0);
                }
                return Optional.of(Math.max(0, level));
            }
        }
    }

    /** True when at least one user row exists (used to decide mcMMO fallback). */
    public boolean hasAnyUsers() throws SQLException {
        String users = config.skillsTablePrefix() + "users";
        String sql = "SELECT 1 FROM " + users + " LIMIT 1";
        try (Connection c = connectionSupplier.get();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            return rs.next();
        } catch (SQLException ex) {
            // Missing tables → treat as empty so mcMMO fallback can run.
            return false;
        }
    }

    private static void ensureAllSkills(Map<String, Integer> skills) {
        for (String id : SKILL_IDS) {
            skills.putIfAbsent(id, 0);
        }
    }

    private static String normalizeUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String uuid = raw.trim().toLowerCase(Locale.ROOT);
        if (!uuid.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
            return null;
        }
        return uuid;
    }

    private static final class Acc {
        final String uuid;
        String username = "";
        final Map<String, Integer> skills = new LinkedHashMap<>();

        Acc(String uuid) {
            this.uuid = uuid;
        }
    }
}
