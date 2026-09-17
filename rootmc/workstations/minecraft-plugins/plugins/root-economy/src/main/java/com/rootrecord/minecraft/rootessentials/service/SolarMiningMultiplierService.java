package com.rootrecord.minecraft.rootessentials.service;

import com.rootrecord.minecraft.common.config.RootMcApiBases;
import com.rootrecord.minecraft.rootessentials.chat.SolarHostChat;
import com.rootrecord.minecraft.common.config.RootRecordCloudConfig;
import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Polls {@code GET /api/rootmc/solar-mining-multiplier} for live bank / watts, then applies:
 * <ul>
 *   <li>Disconnected → gold + skills 1.00×, +1% env tax</li>
 *   <li>Connected → 1 + 0.01 + 0.01 per 10% battery + 0.01 per 100 W (gold and skills)</li>
 * </ul>
 */
public final class SolarMiningMultiplierService {

    public static final double CONNECTED_BASE = 0.01d;
    public static final double PER_10_BATTERY = 0.01d;
    public static final double PER_100W = 0.01d;
    public static final double OFFLINE_ENV_TAX = 0.01d;

    private final RootEconomyPlugin plugin;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.offline());
    private BukkitTask pollTask;
    private volatile boolean enabled = true;
    private volatile long pollSeconds = 60;

    public SolarMiningMultiplierService(RootEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        reloadFromConfig();
        if (!enabled) {
            plugin.getLogger().info("Solar mining multiplier disabled in config.");
            return;
        }
        long periodTicks = Math.max(20L * 30L, pollSeconds * 20L);
        pollTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, this::refreshSafe, 40L, periodTicks);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::refreshSafe);
        plugin.getLogger().info("Solar mining multiplier polling every " + pollSeconds + "s.");
    }

    public void stop() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
    }

    public void reloadFromConfig() {
        FileConfiguration cfg = plugin.economyYaml();
        enabled = cfg.getBoolean("solar-mining.enabled", true);
        pollSeconds = Math.max(30L, cfg.getLong("solar-mining.poll-seconds", 60L));
    }

    public double multiplier() {
        if (!enabled) {
            return 1.0d;
        }
        Snapshot s = snapshot.get();
        return s != null ? s.multiplier() : 1.0d;
    }

    public boolean online() {
        Snapshot s = snapshot.get();
        return enabled && s != null && s.online();
    }

    /** Offline env tax only (1%). Connected → 0. Reserve tax is separate. */
    public double taxRate() {
        if (!enabled) {
            return 0.0d;
        }
        Snapshot s = snapshot.get();
        if (s == null || !s.online()) {
            return OFFLINE_ENV_TAX;
        }
        return 0.0d;
    }

    /** @deprecated CPU tax removed. Always 0. */
    public double cpuTaxRate() {
        return 0.0d;
    }

    /** Same curve as gold mine bonus. */
    public double xpMultiplier() {
        return multiplier();
    }

    public Double solarWatts() {
        Snapshot s = snapshot.get();
        return s != null ? s.solarWatts() : null;
    }

    public Double cpuPercent() {
        Snapshot s = snapshot.get();
        return s != null ? s.cpuPercent() : null;
    }

    public Snapshot current() {
        return snapshot.get();
    }

    public void sendStatus(Player player) {
        SolarHostChat.send(player, current());
    }

    public static Snapshot compute(boolean connected, Double batteryPct, Double solarW, Double cpuPct) {
        return compute(connected, batteryPct, solarW, cpuPct, false);
    }

    public static Snapshot compute(
            boolean connected, Double batteryPct, Double solarW, Double cpuPct, boolean volcanoActive) {
        if (!connected) {
            return new Snapshot(
                    false, batteryPct, 1.0d, OFFLINE_ENV_TAX, 0.0d, cpuPct, solarW, 1.0d,
                    false, System.currentTimeMillis());
        }
        double batteryBonus = 0;
        if (batteryPct != null && Double.isFinite(batteryPct) && batteryPct > 0) {
            double bank = Math.min(100.0d, Math.max(0.0d, batteryPct));
            batteryBonus = Math.floor(bank / 10.0d) * PER_10_BATTERY;
        }
        double wattBonus = 0;
        if (solarW != null && Double.isFinite(solarW) && solarW > 0) {
            wattBonus = Math.floor(solarW / 100.0d) * PER_100W;
        }
        double solar = round3(1.0d + CONNECTED_BASE + batteryBonus + wattBonus);
        double mult = volcanoActive ? round3(solar * 2.0d) : solar;
        return new Snapshot(
                true, batteryPct, mult, 0.0d, 0.0d, cpuPct, solarW, mult,
                volcanoActive, System.currentTimeMillis());
    }

    public static List<ItemStack> scaleStacks(Collection<ItemStack> stacks, double multiplier) {
        List<ItemStack> out = new ArrayList<>();
        if (stacks == null) {
            return out;
        }
        double mult = multiplier;
        if (!Double.isFinite(mult) || Math.abs(mult - 1.0d) < 1e-9) {
            for (ItemStack stack : stacks) {
                if (stack != null && !stack.getType().isAir() && stack.getAmount() > 0) {
                    out.add(stack.clone());
                }
            }
            return out;
        }
        for (ItemStack stack : stacks) {
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                continue;
            }
            ItemStack copy = stack.clone();
            int scaled = scaleAmount(copy.getAmount(), mult);
            if (scaled <= 0) {
                continue;
            }
            int remaining = scaled;
            while (remaining > 0) {
                ItemStack piece = copy.clone();
                int n = Math.min(remaining, piece.getMaxStackSize());
                piece.setAmount(n);
                out.add(piece);
                remaining -= n;
            }
        }
        return out;
    }

    public static int scaleAmount(int base, double multiplier) {
        if (base <= 0) {
            return 0;
        }
        if (!Double.isFinite(multiplier) || multiplier <= 0) {
            return 0;
        }
        if (Math.abs(multiplier - 1.0d) < 1e-9) {
            return base;
        }
        double scaled = base * multiplier;
        int whole = (int) Math.floor(scaled);
        double frac = scaled - whole;
        if (frac > 1e-9 && ThreadLocalRandom.current().nextDouble() < frac) {
            whole++;
        }
        return Math.max(0, whole);
    }

    private void refreshSafe() {
        try {
            refresh();
        } catch (Exception ex) {
            snapshot.set(Snapshot.offline());
            plugin.getLogger().warning("Solar mining multiplier refresh failed: " + ex.getMessage());
        }
    }

    private void refresh() throws Exception {
        FileConfiguration cfg = plugin.economyYaml();
        RootRecordCloudConfig.CloudSettings cloud = RootRecordCloudConfig.resolve(plugin, cfg);
        String configured = RootMcApiBases.normalize(cloud.apiBase());
        String primary = configured;
        String secondary =
                RootMcApiBases.PRODUCTION.equalsIgnoreCase(configured)
                        ? RootMcApiBases.LOCAL_EDGE
                        : RootMcApiBases.fallbackBase(configured);

        String json = fetchLiveJson(primary);
        String used = primary;
        if (!isLivePayload(json) && !secondary.equalsIgnoreCase(primary)) {
            String alt = fetchLiveJson(secondary);
            if (isLivePayload(alt)) {
                json = alt;
                used = secondary;
            }
        }
        if (!isLivePayload(json)) {
            Snapshot prev = snapshot.get();
            snapshot.set(Snapshot.offline());
            if (prev == null || prev.online() || Math.abs(prev.taxRate() - OFFLINE_ENV_TAX) > 1e-9) {
                broadcastStatus();
            }
            return;
        }
        boolean connected = hostConnected(json);
        boolean volcanoActive = parseBool(json, "volcano_active");
        Double battery = optionalNum(parseNum(json, "battery_percent"));
        Double cpu = optionalNum(parseNum(json, "cpu_percent"));
        Double watts = optionalNum(parseNum(json, "solar_w"));
        Snapshot next = compute(connected, battery, watts, cpu, volcanoActive);
        Snapshot prev = snapshot.get();
        snapshot.set(next);
        if (prev == null || !sameState(prev, next)) {
            plugin.getLogger().info(String.format(
                    "Solar host %s via %s — bank %s%% %sW → gold/skills %.3fx env tax %.2f%%",
                    connected ? "connected" : "offline",
                    used,
                    battery != null ? String.valueOf(Math.round(battery)) : "?",
                    watts != null ? String.valueOf(Math.round(watts)) : "?",
                    next.multiplier(),
                    next.taxRate() * 100.0d));
            broadcastStatus();
        }
    }

    private static boolean hostConnected(String json) {
        if (json.contains("\"host_online\":false") || json.contains("\"online\":false")) {
            if (json.contains("\"host_online\":true")) {
                return true;
            }
            return json.contains("\"mode\":\"bonus\"");
        }
        return json.contains("\"host_online\":true")
                || json.contains("\"online\":true")
                || json.contains("\"mode\":\"bonus\"");
    }

    private static boolean sameState(Snapshot a, Snapshot b) {
        return a.online() == b.online()
                && a.volcanoActive() == b.volcanoActive()
                && Math.abs(a.multiplier() - b.multiplier()) < 0.0005d
                && Math.abs(a.taxRate() - b.taxRate()) < 0.0005d
                && Math.abs(a.xpMultiplier() - b.xpMultiplier()) < 0.0005d;
    }

    private void broadcastStatus() {
        Snapshot snap = snapshot.get();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                SolarHostChat.send(player, snap);
            }
        });
    }

    private static boolean isLivePayload(String json) {
        if (json == null || json.isBlank() || json.trim().startsWith("<")) {
            return false;
        }
        if (!json.contains("\"ok\":true")) {
            return false;
        }
        return json.contains("\"online\"")
                || json.contains("\"host_online\"")
                || json.contains("\"battery_percent\"")
                || json.contains("\"solar_w\"")
                || json.contains("\"multiplier\"");
    }

    private String fetchLiveJson(String apiBase) {
        try {
            return getJson(apiBase);
        } catch (Exception ex) {
            plugin.getLogger().fine("Solar mining fetch " + apiBase + ": " + ex.getMessage());
            return "";
        }
    }

    private String getJson(String apiBase) throws Exception {
        String url = apiBase + "/api/rootmc/solar-mining-multiplier";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .header("User-Agent", "RootEconomy-SolarMining/1.0")
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + res.statusCode());
        }
        String body = res.body() == null ? "" : res.body();
        if (body.trim().startsWith("<")) {
            throw new IllegalStateException("non-JSON body from " + apiBase);
        }
        return body;
    }

    private static Double optionalNum(double parsed) {
        return parsed >= 0 ? parsed : null;
    }

    private static boolean parseBool(String json, String key) {
        if (json == null || key == null) {
            return false;
        }
        return json.contains("\"" + key + "\":true")
                || json.contains("\"" + key + "\": true");
    }

    private static double parseNum(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?)")
                .matcher(json);
        if (!m.find()) {
            return -1;
        }
        try {
            return Double.parseDouble(m.group(1));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0d) / 1000.0d;
    }

    public record Snapshot(
            boolean online,
            Double batteryPercent,
            double multiplier,
            double taxRate,
            double cpuTaxRate,
            Double cpuPercent,
            Double solarWatts,
            double xpMultiplier,
            boolean volcanoActive,
            long fetchedAtMs) {
        public static Snapshot offline() {
            return new Snapshot(false, null, 1.0d, OFFLINE_ENV_TAX, 0.0d, null, null, 1.0d, false, 0L);
        }
    }
}
