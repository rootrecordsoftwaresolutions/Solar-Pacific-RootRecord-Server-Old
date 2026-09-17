package com.rootrecord.minecraft.rootavacore;

import com.rootrecord.minecraft.common.config.RootMcApiBases;
import com.rootrecord.minecraft.common.config.RootRecordCloudConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-game {@code /solar} — HI Pacific Solar Root Server power + weather board.
 * Reads public host-site telemetry from api.rootmc.net (same pack Ava Discord /solar uses).
 */
public final class SolarCommand implements CommandExecutor {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private final RootAvaCorePlugin plugin;

    public SolarCommand(RootAvaCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rootavacore.use")) {
            sender.sendMessage(plugin.colorize(plugin.config().noPermission()));
            return true;
        }
        sender.sendMessage(plugin.colorize(plugin.config().prefix() + "&7Fetching live &f/solar&7…"));
        new BukkitRunnable() {
            @Override
            public void run() {
                List<String> lines;
                try {
                    lines = fetchBoard();
                } catch (Exception ex) {
                    lines = List.of(
                            "&c/solar failed: &7" + ex.getMessage(),
                            "&8Try again in a moment — or check Discord &f/solar&8.");
                }
                List<String> out = lines;
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        for (String line : out) {
                            sender.sendMessage(plugin.colorize(plugin.config().prefix() + line));
                        }
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
        return true;
    }

    private List<String> fetchBoard() throws Exception {
        RootRecordCloudConfig.CloudSettings cloud = RootRecordCloudConfig.resolve(plugin, null);
        String configured = RootMcApiBases.normalize(
                cloud != null && cloud.apiBase() != null ? cloud.apiBase() : RootMcApiBases.PRODUCTION);
        String primary = RootMcApiBases.preferredBase(configured);
        String json;
        try {
            json = getJson(primary);
        } catch (Exception first) {
            String fallback = RootMcApiBases.fallbackBase(primary);
            if (fallback.equals(primary)) {
                throw first;
            }
            json = getJson(fallback);
        }
        return formatBoard(json);
    }

    private static String getJson(String apiBase) throws Exception {
        String url = apiBase.replaceAll("/+$", "") + "/api/rootmc/host-site/telemetry";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .header("User-Agent", "RootAvaCore-Solar/1.0")
                .GET()
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + res.statusCode());
        }
        return res.body() == null ? "" : res.body();
    }

    private static List<String> formatBoard(String json) {
        List<String> lines = new ArrayList<>();
        String telem = extractObject(json, "telemetry");
        if (telem == null) {
            telem = json;
        }
        String site = extractObject(telem, "site");
        String solar = extractObject(telem, "solar");
        String weather = extractObject(telem, "weather");

        String label = str(site, "label");
        if (label.isEmpty()) {
            label = "HI Pacific Solar Root Server";
        }
        boolean hostOnline = !telem.contains("\"hostOnline\":false")
                && (solar == null || !solar.contains("\"hostOnline\":false"));
        boolean ecoStale = solar != null && solar.contains("\"ecoStale\":true");
        Double bank = num(solar, "batteryPct");

        lines.add("&d" + label + " &8· &f/solar");
        String powerBit = hostOnline ? "&aonline" : "&chost off";
        String ecoBit = ecoStale ? "&estale (>3m)" : "&alive";
        String bankBit = bank != null ? " &8· bank &f~" + Math.round(bank) + "%" : "";
        lines.add("&7Power: " + powerBit + " &8· EcoFlow " + ecoBit + bankBit);

        String perSn = extractObject(solar, "perSn");
        if (perSn != null) {
            addDeviceLine(lines, perSn, "R331ZAB5SG6S2858", "Delta 2");
            addDeviceLine(lines, perSn, "R621ZA16XH6K1155", "River 2 Pro");
        } else {
            lines.add("&8No EcoFlow packs in telemetry yet.");
        }

        Double morning = num(solar, "morningAvgW");
        if (morning != null) {
            lines.add("&7Morning solar avg: &f~" + Math.round(morning) + "W");
        }

        if (weather != null && weather.contains("\"ok\":true")) {
            String period = extractObject(weather, "period");
            String name = str(period, "name");
            String shortF = str(period, "short");
            Double temp = num(period, "temp");
            String unit = str(period, "unit");
            String wind = str(period, "wind");
            StringBuilder wx = new StringBuilder("&7Weather");
            if (!name.isEmpty()) {
                wx.append(" (&f").append(name).append("&7)");
            }
            wx.append(": ");
            if (temp != null) {
                wx.append("&f").append(Math.round(temp)).append(unit.isEmpty() ? "°" : unit);
            }
            if (!shortF.isEmpty()) {
                wx.append(" &8· &f").append(shortF);
            }
            if (!wind.isEmpty()) {
                wx.append(" &8· wind &f").append(wind);
            }
            lines.add(wx.toString());
            if (weather.contains("\"alerts\":[") && !weather.contains("\"alerts\":[]")) {
                lines.add("&cNWS hazard active — check Discord &f/solar &cfor detail.");
            } else {
                lines.add("&7Hazards: &fnone active (NWS)");
            }
        } else {
            lines.add("&7Weather unavailable right now.");
        }

        lines.add("&8Live rule: offline / >3m stale packs excluded · ava.rootmc.net/solar");
        return lines;
    }

    private static void addDeviceLine(List<String> lines, String perSn, String sn, String label) {
        String entry = extractObject(perSn, sn);
        if (entry == null) {
            return;
        }
        boolean ok = entry.contains("\"ok\":true") && !entry.contains("\"live\":false")
                && !entry.contains("\"deviceOnline\":false");
        if (!ok) {
            String msg = str(entry, "message");
            lines.add("&7" + label + ": &coffline/stale"
                    + (msg.isEmpty() ? "" : " &8(" + msg + ")"));
            return;
        }
        Double soc = num(entry, "soc");
        Double solarW = num(entry, "solarW");
        Double outW = num(entry, "outW");
        boolean off = entry.contains("\"offCircuit\":true");
        StringBuilder b = new StringBuilder("&7").append(label).append(": ");
        if (soc != null) {
            b.append("&f").append(Math.round(soc)).append("%");
        }
        if (solarW != null) {
            b.append(" &8· solar &f").append(Math.round(solarW)).append("W");
        }
        if (outW != null) {
            b.append(" &8· out &f").append(Math.round(outW)).append("W");
        }
        if (off) {
            b.append(" &8· off-circuit");
        }
        lines.add(b.toString());
    }

    private static String extractObject(String json, String key) {
        if (json == null || key == null) {
            return null;
        }
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\{");
        Matcher m = p.matcher(json);
        if (!m.find()) {
            return null;
        }
        int start = m.end() - 1;
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private static String str(String obj, String key) {
        if (obj == null) {
            return "";
        }
        Matcher m = Pattern.compile(
                        "\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(obj);
        return m.find() ? m.group(1) : "";
    }

    private static Double num(String obj, String key) {
        if (obj == null) {
            return null;
        }
        Matcher m = Pattern.compile(
                        "\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?)")
                .matcher(obj);
        if (!m.find()) {
            return null;
        }
        try {
            return Double.parseDouble(m.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
