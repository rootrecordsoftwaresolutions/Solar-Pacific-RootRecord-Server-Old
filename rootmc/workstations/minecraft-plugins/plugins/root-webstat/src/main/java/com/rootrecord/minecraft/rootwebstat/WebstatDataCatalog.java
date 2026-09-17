package com.rootrecord.minecraft.rootwebstat;

import com.rootrecord.minecraft.common.McDayClock;
import com.rootrecord.minecraft.rootcore.api.RootCoreApi;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Per-dataset JSON for the host Webstat site (claims/towny). Raw data source for other projects.
 */
public final class WebstatDataCatalog {

    public record DatasetMeta(String id, String title, String unit, String description) {}

    private static final DatasetMeta[] DATASETS = {
        new DatasetMeta("balances", "Balances", "G", "All player wallet balances on this host"),
        new DatasetMeta("playtime", "Playtime", "s", "Player playtime seconds (network total scope)"),
        new DatasetMeta("times", "Times", "", "Live Minecraft day / online / AFK status"),
        new DatasetMeta("online", "Online", "players", "Current online player count"),
        new DatasetMeta("shops", "Shops", "", "Chest shop / market listings on this host"),
        new DatasetMeta("gold_found", "Gold found", "G", "Lifetime gold found totals by player"),
    };

    private final RootWebstatPlugin plugin;
    private final AtomicReference<String> catalogJson = new AtomicReference<>("{\"datasets\":[]}");
    private final Map<String, AtomicReference<String>> datasetJson = new LinkedHashMap<>();

    public WebstatDataCatalog(RootWebstatPlugin plugin) {
        this.plugin = plugin;
        for (DatasetMeta d : DATASETS) {
            datasetJson.put(d.id(), new AtomicReference<>("{\"id\":\"" + d.id() + "\",\"rows\":[]}"));
        }
    }

    public String catalogJson() {
        return catalogJson.get();
    }

    public String datasetJson(String id) {
        AtomicReference<String> ref = datasetJson.get(id);
        return ref != null ? ref.get() : null;
    }

    public List<DatasetMeta> datasets() {
        return List.of(DATASETS);
    }

    /** Recompute all dataset payloads + metric samples. Call off main thread. */
    public void recomputeAll() {
        WebstatConfig cfg = plugin.config();
        RootCoreApi core = coreApi();
        String serverId = resolveServerId(core);
        String serverName = resolveServerName(cfg, core);

        StringBuilder catalog = new StringBuilder(512);
        catalog.append("{\"schema\":\"root-webstat/data-catalog/v1\",");
        catalog.append("\"server_id\":\"").append(esc(serverId)).append("\",");
        catalog.append("\"server_name\":\"").append(esc(serverName)).append("\",");
        catalog.append("\"computed_at\":\"").append(Instant.now()).append("\",");
        catalog.append("\"windows\":[\"1h\",\"8h\",\"12h\",\"24h\",\"48h\",\"7d\",\"1m\",\"year\"],");
        catalog.append("\"datasets\":[");

        try (Connection c = core != null ? WebstatMetricSamples.open(core) : null) {
            if (c != null) {
                WebstatMetricSamples.ensureSchema(c, cfg);
            }
            for (int i = 0; i < DATASETS.length; i++) {
                DatasetMeta meta = DATASETS[i];
                if (i > 0) {
                    catalog.append(',');
                }
                catalog
                        .append("{\"id\":\"")
                        .append(esc(meta.id()))
                        .append("\",\"title\":\"")
                        .append(esc(meta.title()))
                        .append("\",\"unit\":\"")
                        .append(esc(meta.unit()))
                        .append("\",\"description\":\"")
                        .append(esc(meta.description()))
                        .append("\",\"href\":\"/data.html?set=")
                        .append(esc(meta.id()))
                        .append("\",\"api\":\"/api/data/")
                        .append(esc(meta.id()))
                        .append(".json\"}");
                String json = buildDataset(c, cfg, meta, serverId, serverName);
                datasetJson.get(meta.id()).set(json);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Webstat data catalog failed: " + ex.getMessage());
            for (DatasetMeta meta : DATASETS) {
                datasetJson
                        .get(meta.id())
                        .set(errorDataset(meta, serverId, serverName, ex.getMessage()));
            }
        }
        catalog.append("]}");
        catalogJson.set(catalog.toString());
    }

    private String buildDataset(
            Connection c, WebstatConfig cfg, DatasetMeta meta, String serverId, String serverName)
            throws Exception {
        return switch (meta.id()) {
            case "balances" -> buildBalances(c, cfg, meta, serverId, serverName);
            case "playtime" -> buildPlaytime(c, cfg, meta, serverId, serverName);
            case "times" -> buildTimes(c, cfg, meta, serverId, serverName);
            case "online" -> buildOnline(c, cfg, meta, serverId, serverName);
            case "shops" -> buildShops(c, cfg, meta, serverId, serverName);
            case "gold_found" -> buildGoldFound(c, cfg, meta, serverId, serverName);
            default -> errorDataset(meta, serverId, serverName, "unknown dataset");
        };
    }

    private String buildBalances(
            Connection c, WebstatConfig cfg, DatasetMeta meta, String serverId, String serverName)
            throws Exception {
        List<Row> rows = new ArrayList<>();
        if (c != null) {
            String sql = "SELECT minecraft_username, balance FROM " + cfg.economyBalancesTable();
            if (!cfg.includeZeroBalances()) {
                sql += " WHERE balance > 0.0001";
            }
            sql += " ORDER BY balance DESC";
            try (PreparedStatement ps = c.prepareStatement(sql);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Row(rs.getString(1), rs.getDouble(2)));
                }
            }
        }
        return finishNumericDataset(c, cfg, meta, serverId, serverName, "G", rows, "player", "balance");
    }

    private String buildPlaytime(
            Connection c, WebstatConfig cfg, DatasetMeta meta, String serverId, String serverName)
            throws Exception {
        List<Row> rows = new ArrayList<>();
        if (c != null) {
            String[] tables = {
                cfg.economyTablePrefix() + "playtime",
                cfg.economyTablePrefix() + "rootmc_playtime",
                "root_playtime"
            };
            for (String table : tables) {
                rows.clear();
                String sql =
                        "SELECT username, seconds FROM "
                                + table
                                + " WHERE scope = '*' AND seconds > 0 ORDER BY seconds DESC LIMIT 5000";
                try (PreparedStatement ps = c.prepareStatement(sql);
                        ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new Row(rs.getString(1), rs.getDouble(2)));
                    }
                } catch (Exception ignored) {
                    continue;
                }
                if (!rows.isEmpty()) {
                    break;
                }
            }
        }
        return finishNumericDataset(c, cfg, meta, serverId, serverName, "s", rows, "player", "seconds");
    }

    private String buildGoldFound(
            Connection c, WebstatConfig cfg, DatasetMeta meta, String serverId, String serverName)
            throws Exception {
        List<Row> rows = new ArrayList<>();
        if (c != null) {
            String table = cfg.economyTablePrefix() + "gold_found";
            String sql =
                    "SELECT minecraft_username, total_gold_g FROM "
                            + table
                            + " WHERE total_gold_g > 0 ORDER BY total_gold_g DESC LIMIT 5000";
            try (PreparedStatement ps = c.prepareStatement(sql);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Row(rs.getString(1), rs.getDouble(2)));
                }
            } catch (Exception ignored) {
                // table may not exist on this host
            }
        }
        return finishNumericDataset(c, cfg, meta, serverId, serverName, "G", rows, "player", "amount");
    }

    private String buildShops(
            Connection c, WebstatConfig cfg, DatasetMeta meta, String serverId, String serverName)
            throws Exception {
        List<Row> rows = new ArrayList<>();
        if (c != null) {
            String table = cfg.economyTablePrefix() + "rootstat_shop_listings";
            String sql =
                    "SELECT CONCAT(IFNULL(owner_username,''),' ',IFNULL(item_key,'')) AS label, price"
                            + " FROM "
                            + table
                            + " WHERE price IS NOT NULL ORDER BY price DESC LIMIT 5000";
            try (PreparedStatement ps = c.prepareStatement(sql);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Row(rs.getString(1), rs.getDouble(2)));
                }
            } catch (Exception ignored) {
                // optional
            }
        }
        return finishNumericDataset(c, cfg, meta, serverId, serverName, "G", rows, "listing", "price");
    }

    private String buildOnline(
            Connection c, WebstatConfig cfg, DatasetMeta meta, String serverId, String serverName)
            throws Exception {
        int n = Bukkit.getOnlinePlayers().size();
        List<Row> rows = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(p -> rows.add(new Row(p.getName(), 1.0)));
        double current = n;
        double average = n;
        double total = n;
        int count = n;
        if (c != null) {
            WebstatMetricSamples.record(c, cfg, "online", current, average, total, count);
        }
        return wrapDataset(
                meta,
                serverId,
                serverName,
                "players",
                current,
                average,
                total,
                count,
                windowBlock(c, cfg, "online", current, average, total),
                rowsJson(rows, "player", "online"),
                extraOnline());
    }

    private String buildTimes(
            Connection c, WebstatConfig cfg, DatasetMeta meta, String serverId, String serverName)
            throws Exception {
        int online = Bukkit.getOnlinePlayers().size();
        int afk = 0;
        long dayId = plugin.dayWatcher().currentDayId();
        long fullTime = plugin.dayWatcher().currentFullTime();
        long tod = fullTime % McDayClock.TICKS_PER_DAY;
        String phase = "—";
        String timesTable = cfg.economyTablePrefix() + "times_status";
        if (c != null) {
            try (PreparedStatement ps = c.prepareStatement(
                            "SELECT day_id, tod_ticks, full_time, phase, online, afk, updated_at FROM "
                                    + timesTable
                                    + " WHERE id = 1 LIMIT 1");
                    ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    dayId = rs.getLong("day_id");
                    tod = rs.getLong("tod_ticks");
                    fullTime = rs.getLong("full_time");
                    phase = rs.getString("phase");
                    online = rs.getInt("online");
                    afk = rs.getInt("afk");
                }
            } catch (Exception ignored) {
                // live Bukkit fallback
            }
            WebstatMetricSamples.record(c, cfg, "times_online", online, online, online, 1);
            WebstatMetricSamples.record(c, cfg, "times_afk", afk, afk, afk, 1);
            WebstatMetricSamples.record(c, cfg, "times_day_id", dayId, dayId, dayId, 1);
        }

        StringBuilder rows = new StringBuilder(256);
        rows.append('[');
        rows.append(kvRow("day_id", dayId));
        rows.append(',').append(kvRow("tod_ticks", tod));
        rows.append(',').append(kvRow("full_time", fullTime));
        rows.append(',').append(kvRow("phase", phase));
        rows.append(',').append(kvRow("online", online));
        rows.append(',').append(kvRow("afk", afk));
        rows.append(',').append(kvRow("length_minutes", McDayClock.lengthMinutes()));
        rows.append(']');

        Map<String, Double> onlinePct =
                c != null
                        ? WebstatMetricSamples.percentChanges(c, cfg, "times_online", "current", online)
                        : emptyWindows();
        Map<String, Double> afkPct =
                c != null
                        ? WebstatMetricSamples.percentChanges(c, cfg, "times_afk", "current", afk)
                        : emptyWindows();

        StringBuilder sb = new StringBuilder(1024);
        sb.append("{\"schema\":\"root-webstat/dataset/v1\",");
        sb.append("\"id\":\"").append(esc(meta.id())).append("\",");
        sb.append("\"title\":\"").append(esc(meta.title())).append("\",");
        sb.append("\"unit\":\"\",");
        sb.append("\"server_id\":\"").append(esc(serverId)).append("\",");
        sb.append("\"server_name\":\"").append(esc(serverName)).append("\",");
        sb.append("\"computed_at\":\"").append(Instant.now()).append("\",");
        sb.append("\"summary\":{");
        sb.append("\"current\":").append(online).append(',');
        sb.append("\"average\":").append(online).append(',');
        sb.append("\"total\":").append(online).append(',');
        sb.append("\"count\":").append(online + afk).append(',');
        sb.append("\"pct_change\":").append(WebstatMetricSamples.pctJson(onlinePct)).append(',');
        sb.append("\"pct_change_average\":").append(WebstatMetricSamples.pctJson(onlinePct)).append(',');
        sb.append("\"pct_change_total\":").append(WebstatMetricSamples.pctJson(onlinePct));
        sb.append("},");
        sb.append("\"metrics\":{");
        sb.append("\"online\":{\"current\":")
                .append(online)
                .append(",\"pct_change\":")
                .append(WebstatMetricSamples.pctJson(onlinePct))
                .append("},");
        sb.append("\"afk\":{\"current\":")
                .append(afk)
                .append(",\"pct_change\":")
                .append(WebstatMetricSamples.pctJson(afkPct))
                .append("},");
        sb.append("\"day_id\":{\"current\":").append(dayId).append("},");
        sb.append("\"tod_ticks\":{\"current\":").append(tod).append("},");
        sb.append("\"phase\":{\"current\":\"").append(esc(phase)).append("\"}");
        sb.append("},");
        sb.append("\"windows\":[\"1h\",\"8h\",\"12h\",\"24h\",\"48h\",\"7d\",\"1m\",\"year\"],");
        sb.append("\"rows\":").append(rows).append('}');
        return sb.toString();
    }

    private String finishNumericDataset(
            Connection c,
            WebstatConfig cfg,
            DatasetMeta meta,
            String serverId,
            String serverName,
            String unit,
            List<Row> rows,
            String labelKey,
            String valueKey)
            throws Exception {
        double total = 0;
        for (Row r : rows) {
            total += r.value();
        }
        int count = rows.size();
        double average = count == 0 ? 0 : total / count;
        double current = total;
        String metricId = meta.id();
        if (c != null) {
            WebstatMetricSamples.record(c, cfg, metricId, current, average, total, count);
        }
        return wrapDataset(
                meta,
                serverId,
                serverName,
                unit,
                current,
                average,
                total,
                count,
                windowBlock(c, cfg, metricId, current, average, total),
                rowsJson(rows, labelKey, valueKey),
                "");
    }

    private String windowBlock(
            Connection c, WebstatConfig cfg, String metricId, double current, double average, double total)
            throws Exception {
        Map<String, Double> cur =
                c != null
                        ? WebstatMetricSamples.percentChanges(c, cfg, metricId, "current", current)
                        : emptyWindows();
        Map<String, Double> avg =
                c != null
                        ? WebstatMetricSamples.percentChanges(c, cfg, metricId, "average", average)
                        : emptyWindows();
        Map<String, Double> tot =
                c != null
                        ? WebstatMetricSamples.percentChanges(c, cfg, metricId, "total", total)
                        : emptyWindows();
        return "{\"pct_change\":"
                + WebstatMetricSamples.pctJson(cur)
                + ",\"pct_change_average\":"
                + WebstatMetricSamples.pctJson(avg)
                + ",\"pct_change_total\":"
                + WebstatMetricSamples.pctJson(tot)
                + "}";
    }

    private String wrapDataset(
            DatasetMeta meta,
            String serverId,
            String serverName,
            String unit,
            double current,
            double average,
            double total,
            int count,
            String windowJson,
            String rowsJson,
            String extra) {
        // windowJson already includes pct_change keys object — merge into summary
        String pctPart = windowJson;
        if (pctPart.startsWith("{") && pctPart.endsWith("}")) {
            pctPart = pctPart.substring(1, pctPart.length() - 1);
        }
        StringBuilder sb = new StringBuilder(2048);
        sb.append("{\"schema\":\"root-webstat/dataset/v1\",");
        sb.append("\"id\":\"").append(esc(meta.id())).append("\",");
        sb.append("\"title\":\"").append(esc(meta.title())).append("\",");
        sb.append("\"unit\":\"").append(esc(unit)).append("\",");
        sb.append("\"server_id\":\"").append(esc(serverId)).append("\",");
        sb.append("\"server_name\":\"").append(esc(serverName)).append("\",");
        sb.append("\"computed_at\":\"").append(Instant.now()).append("\",");
        sb.append("\"summary\":{");
        sb.append("\"current\":").append(num(current)).append(',');
        sb.append("\"average\":").append(num(average)).append(',');
        sb.append("\"total\":").append(num(total)).append(',');
        sb.append("\"count\":").append(count);
        if (!pctPart.isBlank()) {
            sb.append(',').append(pctPart);
        }
        sb.append("},");
        sb.append("\"windows\":[\"1h\",\"8h\",\"12h\",\"24h\",\"48h\",\"7d\",\"1m\",\"year\"],");
        if (extra != null && !extra.isBlank()) {
            sb.append(extra);
            if (!extra.endsWith(",")) {
                sb.append(',');
            }
        }
        sb.append("\"rows\":").append(rowsJson).append('}');
        return sb.toString();
    }

    private static String extraOnline() {
        return "";
    }

    private static String rowsJson(List<Row> rows, String labelKey, String valueKey) {
        StringBuilder sb = new StringBuilder(Math.max(64, rows.size() * 48));
        sb.append('[');
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            Row r = rows.get(i);
            sb.append("{\"")
                    .append(labelKey)
                    .append("\":\"")
                    .append(esc(r.label()))
                    .append("\",\"")
                    .append(valueKey)
                    .append("\":")
                    .append(num(r.value()))
                    .append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    private static String kvRow(String key, long value) {
        return "{\"key\":\"" + esc(key) + "\",\"value\":" + value + "}";
    }

    private static String kvRow(String key, String value) {
        return "{\"key\":\"" + esc(key) + "\",\"value\":\"" + esc(value) + "\"}";
    }

    private static Map<String, Double> emptyWindows() {
        Map<String, Double> m = new LinkedHashMap<>();
        for (String k : WebstatMetricSamples.WINDOW_KEYS) {
            m.put(k, null);
        }
        return m;
    }

    private String errorDataset(DatasetMeta meta, String serverId, String serverName, String err) {
        return "{\"schema\":\"root-webstat/dataset/v1\",\"id\":\""
                + esc(meta.id())
                + "\",\"title\":\""
                + esc(meta.title())
                + "\",\"server_id\":\""
                + esc(serverId)
                + "\",\"server_name\":\""
                + esc(serverName)
                + "\",\"error\":\""
                + esc(err)
                + "\",\"summary\":{\"current\":0,\"average\":0,\"total\":0,\"count\":0,"
                + "\"pct_change\":"
                + WebstatMetricSamples.pctJson(emptyWindows())
                + "},\"rows\":[]}";
    }

    private String resolveServerName(WebstatConfig cfg, RootCoreApi core) {
        if (cfg.serverNameOverride() != null && !cfg.serverNameOverride().isBlank()) {
            return cfg.serverNameOverride();
        }
        if (core != null) {
            try {
                String n = core.serverName();
                if (n != null && !n.isBlank()) {
                    return n;
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return Bukkit.getServer().getName();
    }

    private String resolveServerId(RootCoreApi core) {
        if (core != null) {
            String id = core.serverId();
            if (id != null && !id.isBlank()) {
                return id;
            }
        }
        return "local";
    }

    private RootCoreApi coreApi() {
        RegisteredServiceProvider<RootCoreApi> rsp =
                Bukkit.getServicesManager().getRegistration(RootCoreApi.class);
        return rsp != null ? rsp.getProvider() : null;
    }

    private static String num(double v) {
        if (!Double.isFinite(v)) {
            return "0";
        }
        return String.format(Locale.US, "%.6f", v);
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record Row(String label, double value) {}
}
