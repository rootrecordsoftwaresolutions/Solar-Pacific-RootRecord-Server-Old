package com.rootrecord.minecraft.roottimes.web;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.roottimes.RootTimesPlugin;
import com.rootrecord.minecraft.roottimes.api.McDaySnapshot;
import com.rootrecord.minecraft.roottimes.clock.ClockService;
import com.rootrecord.minecraft.roottimes.mysql.ActivityHarvestStore;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

public final class TimesHttpServer {

    private final RootTimesPlugin plugin;
    private HttpServer server;

    public TimesHttpServer(RootTimesPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.timesConfig().webEnabled()) {
            return;
        }
        ensureDefaultWebFiles();
        try {
            String host = plugin.timesConfig().webHost();
            int port = plugin.timesConfig().webPort();
            InetSocketAddress addr =
                    host == null || host.isBlank() || "0.0.0.0".equals(host)
                            ? new InetSocketAddress(port)
                            : new InetSocketAddress(host, port);
            server = HttpServer.create(addr, 0);
            server.createContext("/", this::handleStatic);
            server.createContext("/api/status", this::handleStatus);
            server.createContext("/api/players", this::handlePlayers);
            server.createContext("/api/timezones", this::handleTimezones);
            server.setExecutor(null);
            server.start();
            plugin.getLogger().info("Root-Times web UI at http://" + displayHost(host) + ":" + port + "/");
        } catch (IOException ex) {
            plugin.getLogger().warning("Web server failed to start: " + ex.getMessage());
            server = null;
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public String publicUrl() {
        return "http://"
                + displayHost(plugin.timesConfig().webHost())
                + ":"
                + plugin.timesConfig().webPort()
                + "/";
    }

    private static String displayHost(String host) {
        if (host == null || host.isBlank() || "0.0.0.0".equals(host)) {
            return "127.0.0.1";
        }
        return host;
    }

    private Path webRoot() {
        return RootRecordFolders.dir(plugin).toPath().resolve("times").resolve("web");
    }

    private void ensureDefaultWebFiles() {
        Path root = webRoot();
        try {
            Files.createDirectories(root);
            copyIfAbsent(root.resolve("index.html"), "web/index.html");
            copyIfAbsent(root.resolve("style.css"), "web/style.css");
            copyIfAbsent(root.resolve("app.js"), "web/app.js");
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not write default web files: " + ex.getMessage());
        }
    }

    private void copyIfAbsent(Path target, String resource) throws IOException {
        if (Files.isRegularFile(target)) {
            return;
        }
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) {
                return;
            }
            Files.copy(in, target);
        }
    }

    private boolean authorized(HttpExchange exchange) {
        String token = plugin.timesConfig().webToken();
        if (token == null || token.isBlank()) {
            return true;
        }
        String q = exchange.getRequestURI().getRawQuery();
        if (q != null && q.contains("token=" + token)) {
            return true;
        }
        String header = exchange.getRequestHeaders().getFirst("X-RootTimes-Token");
        return token.equals(header);
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            write(exchange, 401, "text/plain", "unauthorized");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path == null || path.equals("/") || path.isBlank()) {
            path = "/index.html";
        }
        Path file = webRoot().resolve(path.substring(1)).normalize();
        if (!file.startsWith(webRoot()) || !Files.isRegularFile(file)) {
            write(exchange, 404, "text/plain", "not found");
            return;
        }
        String type = contentType(file.getFileName().toString());
        byte[] body = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            write(exchange, 401, "application/json", "{\"error\":\"unauthorized\"}");
            return;
        }
        McDaySnapshot snap = ClockService.snapshot();
        long afkCount = Bukkit.getOnlinePlayers().stream()
                .filter(p -> plugin.afkService() != null && plugin.afkService().isAfk(p.getUniqueId()))
                .count();
        String json = """
                {"dayId":%d,"todTicks":%d,"fullTime":%d,"phase":"%s","lengthMinutes":%d,"online":%d,"afk":%d}
                """
                .formatted(
                        snap.dayId(),
                        snap.timeOfDayTicks(),
                        snap.fullTime(),
                        escape(snap.phase()),
                        snap.lengthMinutes(),
                        Bukkit.getOnlinePlayers().size(),
                        afkCount)
                .trim();
        write(exchange, 200, "application/json", json);
    }

    private void handlePlayers(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            write(exchange, 401, "application/json", "{\"error\":\"unauthorized\"}");
            return;
        }
        String list = Bukkit.getOnlinePlayers().stream()
                .map(p -> {
                    boolean afk = plugin.afkService() != null && plugin.afkService().isAfk(p.getUniqueId());
                    return "{\"name\":\""
                            + escape(p.getName())
                            + "\",\"afk\":"
                            + afk
                            + "}";
                })
                .collect(Collectors.joining(","));
        write(exchange, 200, "application/json", "{\"players\":[" + list + "]}");
    }

    private void handleTimezones(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            write(exchange, 401, "application/json", "{\"error\":\"unauthorized\"}");
            return;
        }
        if (!plugin.mysql().ready()) {
            write(exchange, 200, "application/json", "{\"timezones\":{}}");
            return;
        }
        try (var c = plugin.mysql().open()) {
            Map<String, long[]> totals = ActivityHarvestStore.loadTimezoneHourTotals(c, plugin.timesConfig());
            StringBuilder sb = new StringBuilder("{\"timezones\":{");
            boolean first = true;
            for (var e : totals.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('"').append(escape(e.getKey())).append("\":[");
                long[] b = e.getValue();
                for (int i = 0; i < 24; i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append(b[i]);
                }
                sb.append(']');
            }
            sb.append("}}");
            write(exchange, 200, "application/json", sb.toString());
        } catch (Exception ex) {
            write(exchange, 500, "application/json", "{\"error\":\"" + escape(ex.getMessage()) + "\"}");
        }
    }

    private static String contentType(String name) {
        if (name.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (name.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (name.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        return "application/octet-stream";
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void write(HttpExchange exchange, int code, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
