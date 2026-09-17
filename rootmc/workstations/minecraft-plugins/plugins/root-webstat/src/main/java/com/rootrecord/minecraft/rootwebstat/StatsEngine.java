package com.rootrecord.minecraft.rootwebstat;

import com.rootrecord.minecraft.common.RootMcServerDisplay;
import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import com.rootrecord.minecraft.rootcore.api.RootCoreApi;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Builds the unique stats.json payload on this host. */
public final class StatsEngine {

    private final RootWebstatPlugin plugin;
    private final AtomicReference<String> cachedJson = new AtomicReference<>("{}");
    private volatile long computedAtEpochMs;
    private volatile long lastCompletedDayId = -1L;
    private volatile long lastFullTime;

    public StatsEngine(RootWebstatPlugin plugin) {
        this.plugin = plugin;
    }

    public String cachedJson() {
        return cachedJson.get();
    }

    public long computedAtEpochMs() {
        return computedAtEpochMs;
    }

    public String recompute() {
        return recompute(-1L, -1L, -1L);
    }

    /**
     * @param completedDayId vanilla day that just finished ({@code fullTime/24000} before rollover), or -1
     * @param currentDayId vanilla day after rollover, or -1 to read from watcher/world
     * @param fullTime world fullTime at compute, or -1
     */
    public String recompute(long completedDayId, long currentDayId, long fullTime) {
        WebstatConfig cfg = plugin.config();
        String serverId = resolveServerId();
        String serverName = resolveServerName(cfg);
        List<StatSeries> series = new ArrayList<>();

        series.add(onlinePlayersSeries());
        series.add(walletSeries(cfg));
        series.add(playtimeSeries(cfg));

        long day = currentDayId >= 0 ? currentDayId : plugin.dayWatcher().currentDayId();
        long ft = fullTime >= 0 ? fullTime : plugin.dayWatcher().currentFullTime();
        if (completedDayId >= 0) {
            lastCompletedDayId = completedDayId;
        }
        lastFullTime = ft;

        StringBuilder sb = new StringBuilder(2048);
        sb.append('{');
        sb.append("\"schema\":\"root-webstat/v1\",");
        sb.append("\"server_id\":\"").append(esc(serverId)).append("\",");
        sb.append("\"server_name\":\"").append(esc(serverName)).append("\",");
        sb.append("\"computed_at\":\"").append(Instant.now()).append("\",");
        sb.append("\"source\":\"paper\",");
        String publicUrl = cfg.publicUrl();
        if (publicUrl != null && !publicUrl.isBlank()) {
            sb.append("\"webstat_url\":\"").append(esc(publicUrl.replaceAll("/$", ""))).append("\",");
        }
        sb.append("\"web_port\":").append(cfg.port()).append(',');
        sb.append("\"mc_day_id\":").append(day).append(',');
        if (completedDayId >= 0) {
            sb.append("\"completed_mc_day_id\":").append(completedDayId).append(',');
        } else if (lastCompletedDayId >= 0) {
            sb.append("\"completed_mc_day_id\":").append(lastCompletedDayId).append(',');
        }
        sb.append("\"full_time\":").append(ft).append(',');
        sb.append("\"ticks_per_day\":").append(com.rootrecord.minecraft.common.McDayClock.TICKS_PER_DAY).append(',');
        sb.append("\"series\":{");
        for (int i = 0; i < series.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            StatSeries s = series.get(i);
            sb.append('"').append(esc(s.id())).append("\":").append(s.toJsonObject());
        }
        sb.append("}}");
        String json = sb.toString();
        cachedJson.set(json);
        computedAtEpochMs = System.currentTimeMillis();
        return json;
    }

    private StatSeries onlinePlayersSeries() {
        int n = Bukkit.getOnlinePlayers().size();
        return StatSeries.from(
                "online_players",
                "players",
                new double[] {n},
                new String[] {"online"});
    }

    private StatSeries walletSeries(WebstatConfig cfg) {
        List<Double> amounts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        if (!loadWalletsFromMysql(cfg, amounts, names)) {
            loadWalletsFromVaultOnline(cfg, amounts, names);
        }
        if (amounts.isEmpty()) {
            return StatSeries.empty("player_wallet_g", "G");
        }
        double[] values = new double[amounts.size()];
        String[] labels = new String[amounts.size()];
        for (int i = 0; i < amounts.size(); i++) {
            values[i] = amounts.get(i);
            labels[i] = names.get(i);
        }
        return StatSeries.from("player_wallet_g", "G", values, labels);
    }

    private boolean loadWalletsFromMysql(WebstatConfig cfg, List<Double> amounts, List<String> names) {
        RootCoreApi core = coreApi();
        if (core == null || !core.isReady()) {
            return false;
        }
        RootMcDatabaseConfig.DatabaseSettings db = core.databaseSettings();
        if (db == null || !db.isConfigured()) {
            return false;
        }
        String sql = "SELECT minecraft_username, balance FROM " + cfg.economyBalancesTable();
        if (!cfg.includeZeroBalances()) {
            sql += " WHERE balance > 0.0001";
        }
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection c = DriverManager.getConnection(db.jdbcUrl(), db.username(), db.password());
                 PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    amounts.add(rs.getDouble("balance"));
                    names.add(rs.getString("minecraft_username"));
                }
            }
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Wallet MySQL scan failed: " + ex.getMessage());
            amounts.clear();
            names.clear();
            return false;
        }
    }

    private void loadWalletsFromVaultOnline(WebstatConfig cfg, List<Double> amounts, List<String> names) {
        try {
            @SuppressWarnings("unchecked")
            Class<Object> ecoClass = (Class<Object>) Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<Object> rsp =
                    Bukkit.getServicesManager().getRegistration(ecoClass);
            if (rsp == null || rsp.getProvider() == null) {
                return;
            }
            Object economy = rsp.getProvider();
            var getBalance = ecoClass.getMethod("getBalance", org.bukkit.OfflinePlayer.class);
            for (Player p : Bukkit.getOnlinePlayers()) {
                double bal = ((Number) getBalance.invoke(economy, p)).doubleValue();
                if (!cfg.includeZeroBalances() && bal <= 0.0001) {
                    continue;
                }
                amounts.add(bal);
                names.add(p.getName());
            }
        } catch (ClassNotFoundException ignored) {
            // Vault not present
        } catch (Exception ex) {
            plugin.getLogger().warning("Vault wallet scan failed: " + ex.getMessage());
        }
    }

    private StatSeries playtimeSeries(WebstatConfig cfg) {
        RootCoreApi core = coreApi();
        if (core == null || !core.isReady()) {
            return StatSeries.empty("playtime_seconds", "s");
        }
        RootMcDatabaseConfig.DatabaseSettings db = core.databaseSettings();
        if (db == null || !db.isConfigured()) {
            return StatSeries.empty("playtime_seconds", "s");
        }
        String table = cfg.economyTablePrefix() + "playtime";
        String sql = "SELECT username, seconds FROM " + table
                + " WHERE scope = '*' AND seconds > 0";
        List<Double> amounts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection c = DriverManager.getConnection(db.jdbcUrl(), db.username(), db.password());
                 PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    amounts.add(rs.getDouble("seconds"));
                    names.add(rs.getString("username"));
                }
            }
        } catch (Exception ex) {
            // try Towny legacy table name
            try {
                String alt = cfg.economyTablePrefix() + "rootmc_playtime";
                try (Connection c = DriverManager.getConnection(db.jdbcUrl(), db.username(), db.password());
                     PreparedStatement ps = c.prepareStatement(
                             "SELECT username, seconds FROM " + alt + " WHERE scope = '*' AND seconds > 0");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        amounts.add(rs.getDouble("seconds"));
                        names.add(rs.getString("username"));
                    }
                }
            } catch (Exception ignored) {
                return StatSeries.empty("playtime_seconds", "s");
            }
        }
        if (amounts.isEmpty()) {
            return StatSeries.empty("playtime_seconds", "s");
        }
        double[] values = new double[amounts.size()];
        String[] labels = new String[amounts.size()];
        for (int i = 0; i < amounts.size(); i++) {
            values[i] = amounts.get(i);
            labels[i] = names.get(i);
        }
        return StatSeries.from("playtime_seconds", "s", values, labels);
    }

    private String resolveServerName(WebstatConfig cfg) {
        if (cfg.serverNameOverride() != null && !cfg.serverNameOverride().isBlank()) {
            return cfg.serverNameOverride();
        }
        RootCoreApi core = coreApi();
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
        return RootMcServerDisplay.serverName(plugin);
    }

    private String resolveServerId() {
        RootCoreApi core = coreApi();
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

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
