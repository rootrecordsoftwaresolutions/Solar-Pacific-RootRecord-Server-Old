package com.rootrecord.minecraft.rootskills.storage;

import com.rootrecord.minecraft.rootskills.RootSkillsPlugin;
import com.rootrecord.minecraft.rootskills.api.SkillId;
import com.rootrecord.minecraft.rootskills.model.PlayerSkillsProfile;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Migrates classic mcMMO tables into Root-Skills with GREATEST merge (never decrease).
 */
public final class McMmoMigrator {

    private static final int BATCH = 500;

    /** mcMMO column / skill name → Root SkillId (defense/elytra intentionally absent → 0). */
    private static final Map<String, SkillId> COLUMN_MAP = new LinkedHashMap<>();

    static {
        map("mining", SkillId.MINING);
        map("woodcutting", SkillId.WOODCUTTING);
        map("herbalism", SkillId.HERBALISM);
        map("excavation", SkillId.EXCAVATION);
        map("fishing", SkillId.FISHING);
        map("repair", SkillId.REPAIR);
        map("salvage", SkillId.SALVAGE);
        map("smelting", SkillId.SMELTING);
        map("alchemy", SkillId.ALCHEMY);
        map("taming", SkillId.TAMING);
        map("acrobatics", SkillId.ACROBATICS);
        map("unarmed", SkillId.UNARMED);
        map("swords", SkillId.SWORDS);
        map("axes", SkillId.AXES);
        map("archery", SkillId.ARCHERY);
        map("crossbows", SkillId.CROSSBOWS);
        map("tridents", SkillId.TRIDENTS);
        map("maces", SkillId.MACES);
        map("spears", SkillId.SPEARS);
    }

    private static void map(String key, SkillId id) {
        COLUMN_MAP.put(key.toLowerCase(Locale.ROOT), id);
    }

    private final RootSkillsPlugin plugin;

    public McMmoMigrator(RootSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    public void migrateAll() {
        migrate(Bukkit.getConsoleSender(), false, null);
    }

    public void migrate(CommandSender sender, boolean dryRun, UUID onlyPlayer) {
        Database db = plugin.database();
        if (db == null || !db.available()) {
            sender.sendMessage(plugin.msg("admin.migrate-no-db"));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String runId = UUID.randomUUID().toString();
            long started = System.currentTimeMillis();
            int rowsSeen = 0;
            int rowsWritten = 0;
            int conflicts = 0;
            String detail;
            try {
                Result r = runMigration(db, dryRun, onlyPlayer);
                rowsSeen = r.rowsSeen;
                rowsWritten = r.rowsWritten;
                conflicts = r.conflicts;
                detail = r.detail;
                writeLog(db, runId, started, System.currentTimeMillis(), rowsSeen, rowsWritten, conflicts, dryRun,
                        checksum(detail), detail);
                String summary = "mcMMO migrate " + (dryRun ? "DRY-RUN " : "")
                        + "seen=" + rowsSeen + " written=" + rowsWritten + " conflicts=" + conflicts;
                plugin.getLogger().info(summary);
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(plugin.colorize("&a" + summary)));
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "mcMMO migration failed", ex);
                writeLog(db, runId, started, System.currentTimeMillis(), rowsSeen, rowsWritten, conflicts, dryRun,
                        null, "ERROR: " + ex.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(plugin.colorize("&cmcMMO migration failed: " + ex.getMessage())));
            }
        });
    }

    private Result runMigration(Database db, boolean dryRun, UUID onlyPlayer) throws Exception {
        try (Connection c = db.connection()) {
            if (!tableExists(c, "mcmmo_users")) {
                return new Result(0, 0, 0, "mcmmo_users missing");
            }
            boolean hasSkills = tableExists(c, "mcmmo_skills");
            boolean hasXp = tableExists(c, "mcmmo_experience");
            if (!hasSkills && !hasXp) {
                return new Result(0, 0, 0, "mcmmo_skills / mcmmo_experience missing");
            }

            List<String> skillCols = filterExistingColumns(c, hasSkills ? "mcmmo_skills" : "mcmmo_experience",
                    new ArrayList<>(COLUMN_MAP.keySet()));
            List<String> xpCols = hasXp
                    ? filterExistingColumns(c, "mcmmo_experience", new ArrayList<>(COLUMN_MAP.keySet()))
                    : List.of();

            boolean usersHaveUuid = columnExists(c, "mcmmo_users", "uuid");
            String userSql = "SELECT id, user" + (usersHaveUuid ? ", uuid" : "") + " FROM mcmmo_users";
            if (onlyPlayer != null && usersHaveUuid) {
                userSql += " WHERE uuid=?";
            }

            int rowsSeen = 0;
            int rowsWritten = 0;
            int conflicts = 0;
            StringBuilder detail = new StringBuilder();

            try (PreparedStatement userPs = c.prepareStatement(userSql)) {
                if (onlyPlayer != null && usersHaveUuid) {
                    userPs.setString(1, onlyPlayer.toString());
                }
                try (ResultSet users = userPs.executeQuery()) {
                    List<UserRow> batch = new ArrayList<>(BATCH);
                    while (users.next()) {
                        int id = users.getInt("id");
                        String name = users.getString("user");
                        UUID uuid = resolveUuid(users, usersHaveUuid, name);
                        if (uuid == null) {
                            continue;
                        }
                        if (onlyPlayer != null && !onlyPlayer.equals(uuid)) {
                            continue;
                        }
                        batch.add(new UserRow(id, name, uuid));
                        if (batch.size() >= BATCH) {
                            Counts cnt = processBatch(c, batch, hasSkills, hasXp, skillCols, xpCols, dryRun);
                            rowsSeen += cnt.seen;
                            rowsWritten += cnt.written;
                            conflicts += cnt.conflicts;
                            batch.clear();
                        }
                    }
                    if (!batch.isEmpty()) {
                        Counts cnt = processBatch(c, batch, hasSkills, hasXp, skillCols, xpCols, dryRun);
                        rowsSeen += cnt.seen;
                        rowsWritten += cnt.written;
                        conflicts += cnt.conflicts;
                    }
                }
            }
            detail.append("skillsCols=").append(skillCols.size())
                    .append(";xpCols=").append(xpCols.size())
                    .append(";dryRun=").append(dryRun);
            return new Result(rowsSeen, rowsWritten, conflicts, detail.toString());
        }
    }

    private Counts processBatch(
            Connection c,
            List<UserRow> batch,
            boolean hasSkills,
            boolean hasXp,
            List<String> skillCols,
            List<String> xpCols,
            boolean dryRun) throws Exception {
        int seen = 0;
        int written = 0;
        int conflicts = 0;
        for (UserRow row : batch) {
            seen++;
            Map<SkillId, Integer> levels = new LinkedHashMap<>();
            Map<SkillId, Long> xps = new LinkedHashMap<>();
            if (hasSkills && !skillCols.isEmpty()) {
                readWideRow(c, "mcmmo_skills", row.id, skillCols, levels, null);
            }
            if (hasXp && !xpCols.isEmpty()) {
                readWideRow(c, "mcmmo_experience", row.id, xpCols, null, xps);
            }
            // Also try vertical layout: skill / level columns
            if (levels.isEmpty()) {
                readVertical(c, row.id, levels, xps);
            }

            PlayerSkillsProfile existing = plugin.repository().loadSync(row.uuid);
            if (existing == null) {
                existing = new PlayerSkillsProfile(row.uuid);
            }
            existing.setUsername(row.name);
            boolean changed = false;
            for (Map.Entry<SkillId, Integer> e : levels.entrySet()) {
                int before = existing.skill(e.getKey()).level();
                long xp = xps.getOrDefault(e.getKey(), 0L);
                plugin.repository().mergeSkillGreatest(existing, e.getKey(), e.getValue(), xp);
                int after = existing.skill(e.getKey()).level();
                if (after != before || existing.dirty()) {
                    changed = true;
                    if (before > 0 && after == before && xp > 0) {
                        // same level higher xp still counts as write, not conflict
                    } else if (before > e.getValue()) {
                        conflicts++;
                    }
                }
            }
            // defense / elytra stay 0 unless already present
            if (changed) {
                existing.setMigratedFrom("mcmmo");
                existing.setMigratedAt(System.currentTimeMillis());
                if (!dryRun) {
                    plugin.repository().saveSync(existing);
                    written++;
                } else {
                    written++; // counted as would-write
                }
            }
        }
        return new Counts(seen, written, conflicts);
    }

    private void readWideRow(
            Connection c,
            String table,
            int userId,
            List<String> cols,
            Map<SkillId, Integer> levels,
            Map<SkillId, Long> xps) throws Exception {
        StringBuilder sb = new StringBuilder("SELECT ");
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('`').append(cols.get(i)).append('`');
        }
        sb.append(" FROM ").append(table).append(" WHERE user_id=?");
        try (PreparedStatement ps = c.prepareStatement(sb.toString())) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return;
                }
                for (String col : cols) {
                    SkillId skill = COLUMN_MAP.get(col.toLowerCase(Locale.ROOT));
                    if (skill == null) {
                        continue;
                    }
                    if (levels != null) {
                        levels.put(skill, rs.getInt(col));
                    }
                    if (xps != null) {
                        xps.put(skill, rs.getLong(col));
                    }
                }
            }
        } catch (Exception ex) {
            // try alternate PK column name
            try (PreparedStatement ps = c.prepareStatement(
                    sb.toString().replace("user_id=?", "id=?"))) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return;
                    }
                    for (String col : cols) {
                        SkillId skill = COLUMN_MAP.get(col.toLowerCase(Locale.ROOT));
                        if (skill == null) {
                            continue;
                        }
                        if (levels != null) {
                            levels.put(skill, rs.getInt(col));
                        }
                        if (xps != null) {
                            xps.put(skill, rs.getLong(col));
                        }
                    }
                }
            }
        }
    }

    private void readVertical(Connection c, int userId, Map<SkillId, Integer> levels, Map<SkillId, Long> xps)
            throws Exception {
        if (!tableExists(c, "mcmmo_skills")) {
            return;
        }
        // Some forks: user_id, skill, skill_value / xp
        if (!columnExists(c, "mcmmo_skills", "skill")) {
            return;
        }
        String valueCol = columnExists(c, "mcmmo_skills", "skill_value") ? "skill_value"
                : (columnExists(c, "mcmmo_skills", "level") ? "level" : null);
        if (valueCol == null) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT skill, " + valueCol + " FROM mcmmo_skills WHERE user_id=?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SkillId skill = COLUMN_MAP.get(rs.getString("skill").toLowerCase(Locale.ROOT));
                    if (skill != null) {
                        levels.put(skill, rs.getInt(2));
                    }
                }
            }
        }
    }

    private UUID resolveUuid(ResultSet users, boolean hasUuid, String name) throws Exception {
        if (hasUuid) {
            String raw = users.getString("uuid");
            if (raw != null && !raw.isBlank()) {
                try {
                    return UUID.fromString(raw.contains("-") ? raw
                            : raw.replaceFirst(
                            "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                            "$1-$2-$3-$4-$5"));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (name == null || name.isBlank()) {
            return null;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.getUniqueId();
    }

    private void writeLog(
            Database db,
            String runId,
            long started,
            long finished,
            int seen,
            int written,
            int conflicts,
            boolean dryRun,
            String checksum,
            String detail) {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO " + plugin.schemaManager().table("migration_log")
                             + " (run_id, started_at, finished_at, rows_seen, rows_written, conflicts, dry_run, checksum, detail)"
                             + " VALUES (?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, runId);
            ps.setLong(2, started);
            ps.setLong(3, finished);
            ps.setInt(4, seen);
            ps.setInt(5, written);
            ps.setInt(6, conflicts);
            ps.setBoolean(7, dryRun);
            ps.setString(8, checksum);
            ps.setString(9, detail);
            ps.executeUpdate();
        } catch (Exception ex) {
            plugin.getLogger().warning("migration_log write failed: " + ex.getMessage());
        }
    }

    private static boolean tableExists(Connection c, String table) throws Exception {
        DatabaseMetaData md = c.getMetaData();
        try (ResultSet rs = md.getTables(c.getCatalog(), null, table, null)) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = md.getTables(c.getCatalog(), null, table.toUpperCase(Locale.ROOT), null)) {
            return rs.next();
        }
    }

    private static boolean columnExists(Connection c, String table, String column) throws Exception {
        DatabaseMetaData md = c.getMetaData();
        try (ResultSet rs = md.getColumns(c.getCatalog(), null, table, column)) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = md.getColumns(c.getCatalog(), null, table, column.toUpperCase(Locale.ROOT))) {
            return rs.next();
        }
    }

    private static List<String> filterExistingColumns(Connection c, String table, List<String> wanted)
            throws Exception {
        List<String> out = new ArrayList<>();
        for (String col : wanted) {
            if (columnExists(c, table, col)) {
                out.add(col);
            }
        }
        return out;
    }

    private static String checksum(String detail) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest((detail == null ? "" : detail).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig).substring(0, 32);
        } catch (Exception ex) {
            return null;
        }
    }

    private record UserRow(int id, String name, UUID uuid) {}

    private record Counts(int seen, int written, int conflicts) {}

    private record Result(int rowsSeen, int rowsWritten, int conflicts, String detail) {}
}
