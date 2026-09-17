package com.rootrecord.minecraft.roottimes.cloud;

import com.rootrecord.minecraft.common.McDayClock;
import com.rootrecord.minecraft.roottimes.RootTimesPlugin;
import com.rootrecord.minecraft.roottimes.api.McDaySnapshot;
import com.rootrecord.minecraft.roottimes.clock.ClockService;
import com.rootrecord.minecraft.roottimes.mysql.TimesStatusStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Upserts live Times status into host MySQL ({@code root_times_status}).
 * Cloudflare reads via Hyperdrive → LIVE_DB — no HTTPS push.
 */
public final class TimesCloudReporter {

    private final RootTimesPlugin plugin;
    private BukkitTask task;
    private boolean loggedMissingMysql;
    private boolean loggedOk;
    private volatile String lastError = "";

    public TimesCloudReporter(RootTimesPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.timesConfig().mysqlStatusEnabled()) {
            plugin.getLogger().info("MySQL status reporter disabled (mysql-status.enabled: false)");
            return;
        }
        if (!plugin.mysql().ready()) {
            plugin.getLogger().warning("MySQL status reporter skipped — database.yml not ready");
            return;
        }
        int seconds = plugin.timesConfig().mysqlStatusIntervalSeconds();
        long ticks = Math.max(20L, seconds * 20L);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickCollect, ticks, ticks);
        plugin.getLogger().info(
                "MySQL status reporter every "
                        + seconds
                        + "s → "
                        + TimesStatusStore.tableName(plugin.timesConfig()));
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tickCollect() {
        if (!plugin.mysql().ready()) {
            if (!loggedMissingMysql) {
                loggedMissingMysql = true;
                plugin.getLogger().warning("MySQL status skipped — database not configured");
            }
            return;
        }

        McDaySnapshot snap = ClockService.snapshot();
        List<PlayerSnap> players = new ArrayList<>();
        int afkCount = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean afk = plugin.afkService() != null && plugin.afkService().isAfk(p.getUniqueId());
            players.add(new PlayerSnap(p.getName(), afk));
            if (afk) {
                afkCount++;
            }
        }
        int online = Bukkit.getOnlinePlayers().size();
        final String pluginsJson = installedPluginsJson();
        final String playersJson = playersJson(players);
        final String timezone = McDayClock.zone().getId();
        final McDaySnapshot snapFinal = snap;
        final int onlineFinal = online;
        final int afkFinal = afkCount;
        Bukkit.getScheduler().runTaskAsynchronously(
                plugin,
                () -> upsertMysql(snapFinal, onlineFinal, afkFinal, playersJson, pluginsJson, timezone));
    }

    private void upsertMysql(
            McDaySnapshot snap,
            int online,
            int afk,
            String playersJson,
            String pluginsJson,
            String timezone) {
        try (Connection c = plugin.mysql().open()) {
            TimesStatusStore.upsert(
                    c,
                    plugin.timesConfig(),
                    snap.dayId(),
                    (int) snap.timeOfDayTicks(),
                    snap.fullTime(),
                    snap.phase(),
                    snap.lengthMinutes(),
                    online,
                    afk,
                    playersJson,
                    pluginsJson,
                    timezone);
            if (!loggedOk) {
                loggedOk = true;
                plugin.getLogger().info(
                        "MySQL status OK → " + TimesStatusStore.tableName(plugin.timesConfig()));
            }
            lastError = "";
        } catch (Exception ex) {
            lastError = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            plugin.getLogger().log(Level.WARNING, "MySQL status upsert failed: " + lastError, ex);
        }
    }

    private static String playersJson(List<PlayerSnap> players) {
        StringBuilder body = new StringBuilder(256);
        body.append('[');
        for (int i = 0; i < players.size(); i++) {
            if (i > 0) {
                body.append(',');
            }
            PlayerSnap p = players.get(i);
            body.append("{\"name\":\"")
                    .append(escape(p.name()))
                    .append("\",\"afk\":")
                    .append(p.afk())
                    .append('}');
        }
        body.append(']');
        return body.toString();
    }

    private static String installedPluginsJson() {
        List<String> parts = new ArrayList<>();
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            String name = p.getName();
            if (name == null) {
                continue;
            }
            String lower = name.toLowerCase(Locale.ROOT);
            if (!(lower.startsWith("root") || lower.equals("rootmc") || lower.equals("roothelp"))) {
                continue;
            }
            String id = toManifestId(name);
            parts.add(
                    "{\"id\":\""
                            + escape(id)
                            + "\",\"version\":\""
                            + escape(p.getDescription().getVersion())
                            + "\"}");
        }
        return "[" + String.join(",", parts) + "]";
    }

    private static String toManifestId(String bukkitName) {
        return switch (bukkitName) {
            case "RootMC" -> "rootmc";
            case "RootMC-Shops" -> "rootmc-shops";
            case "RootHelp" -> "roothelp";
            case "Root-Core" -> "root-core";
            case "Root-Times" -> "root-times";
            case "Root-Perms" -> "root-perms";
            case "RootMC-Official" -> "rootmc-official";
            case "Root-Essentials" -> "root-essentials";
            case "Root-Rewards" -> "root-rewards";
            default -> bukkitName.toLowerCase(Locale.ROOT);
        };
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record PlayerSnap(String name, boolean afk) {}
}
