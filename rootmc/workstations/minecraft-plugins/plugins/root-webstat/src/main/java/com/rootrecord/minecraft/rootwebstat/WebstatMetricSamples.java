package com.rootrecord.minecraft.rootwebstat;

import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import com.rootrecord.minecraft.rootcore.api.RootCoreApi;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Persists metric snapshots on host MySQL so Webstat can compute window % changes. */
public final class WebstatMetricSamples {

    public static final String[] WINDOW_KEYS = {
        "1h", "8h", "12h", "24h", "48h", "7d", "1m", "year"
    };
    public static final long[] WINDOW_MS = {
        1L * 3600_000L,
        8L * 3600_000L,
        12L * 3600_000L,
        24L * 3600_000L,
        48L * 3600_000L,
        7L * 24L * 3600_000L,
        30L * 24L * 3600_000L,
        365L * 24L * 3600_000L
    };

    private WebstatMetricSamples() {}

    public static String tableName(WebstatConfig cfg) {
        String p = cfg.economyTablePrefix();
        if (p == null || p.isBlank()) {
            p = "root_";
        }
        return p + "webstat_metric_samples";
    }

    public static void ensureSchema(Connection c, WebstatConfig cfg) throws Exception {
        String table = tableName(cfg);
        try (PreparedStatement ps = c.prepareStatement(
                """
                CREATE TABLE IF NOT EXISTS %s (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  metric_id VARCHAR(64) NOT NULL,
                  sampled_at DATETIME(3) NOT NULL,
                  current_value DOUBLE NOT NULL DEFAULT 0,
                  average_value DOUBLE NOT NULL DEFAULT 0,
                  total_value DOUBLE NOT NULL DEFAULT 0,
                  sample_count INT NOT NULL DEFAULT 0,
                  PRIMARY KEY (id),
                  KEY idx_webstat_metric_ts (metric_id, sampled_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """
                        .formatted(table))) {
            ps.executeUpdate();
        }
    }

    public static void record(
            Connection c,
            WebstatConfig cfg,
            String metricId,
            double current,
            double average,
            double total,
            int count)
            throws Exception {
        String table = tableName(cfg);
        try (PreparedStatement ps = c.prepareStatement(
                """
                INSERT INTO %s (metric_id, sampled_at, current_value, average_value, total_value, sample_count)
                VALUES (?, ?, ?, ?, ?, ?)
                """
                        .formatted(table))) {
            ps.setString(1, metricId);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setDouble(3, current);
            ps.setDouble(4, average);
            ps.setDouble(5, total);
            ps.setInt(6, count);
            ps.executeUpdate();
        }
    }

    /** Nearest sample at or before (now - windowMs). */
    public static Double valueNear(
            Connection c, WebstatConfig cfg, String metricId, String field, long windowMs)
            throws Exception {
        String table = tableName(cfg);
        String col =
                switch (field) {
                    case "average" -> "average_value";
                    case "total" -> "total_value";
                    case "count" -> "sample_count";
                    default -> "current_value";
                };
        Instant target = Instant.now().minusMillis(windowMs);
        try (PreparedStatement ps = c.prepareStatement(
                """
                SELECT %s AS v FROM %s
                WHERE metric_id = ? AND sampled_at <= ?
                ORDER BY sampled_at DESC
                LIMIT 1
                """
                        .formatted(col, table))) {
            ps.setString(1, metricId);
            ps.setTimestamp(2, Timestamp.from(target));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double v = rs.getDouble("v");
                    return rs.wasNull() ? null : v;
                }
            }
        }
        return null;
    }

    public static Map<String, Double> percentChanges(
            Connection c, WebstatConfig cfg, String metricId, String field, double nowValue)
            throws Exception {
        Map<String, Double> out = new LinkedHashMap<>();
        for (int i = 0; i < WINDOW_KEYS.length; i++) {
            Double past = valueNear(c, cfg, metricId, field, WINDOW_MS[i]);
            out.put(WINDOW_KEYS[i], pct(nowValue, past));
        }
        return out;
    }

    public static Double pct(double now, Double past) {
        if (past == null || !Double.isFinite(past) || !Double.isFinite(now)) {
            return null;
        }
        if (Math.abs(past) < 1e-12) {
            return now == 0 ? 0.0 : null;
        }
        return ((now - past) / Math.abs(past)) * 100.0;
    }

    public static String pctJson(Map<String, Double> map) {
        StringBuilder sb = new StringBuilder(128);
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Double> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            Double v = e.getValue();
            if (v == null || !Double.isFinite(v)) {
                sb.append("null");
            } else {
                sb.append(String.format(Locale.US, "%.4f", v));
            }
        }
        sb.append('}');
        return sb.toString();
    }

    public static Connection open(RootCoreApi core) throws Exception {
        RootMcDatabaseConfig.DatabaseSettings db = core.databaseSettings();
        if (db == null || !db.isConfigured()) {
            return null;
        }
        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection(db.jdbcUrl(), db.username(), db.password());
    }
}
