package com.rootrecord.minecraft.rootwebstat;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Local site + JSON APIs for this Paper host. */
public final class WebstatHttpServer {

    private final RootWebstatPlugin plugin;
    private HttpServer server;

    public WebstatHttpServer(RootWebstatPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        WebstatConfig cfg = plugin.config();
        if (!cfg.enabled() || !cfg.anyHttpSurface()) {
            return;
        }
        ensureDefaultWebFiles();
        try {
            String host = cfg.bind();
            int port = cfg.port();
            InetSocketAddress addr =
                    host == null || host.isBlank() || "0.0.0.0".equals(host)
                            ? new InetSocketAddress(port)
                            : new InetSocketAddress(host, port);
            server = HttpServer.create(addr, 0);
            if (cfg.featureHttpSite() || cfg.featureLogsPage()) {
                server.createContext("/", this::handleStatic);
            }
            if (cfg.featureStatsApi()) {
                server.createContext("/stats.json", this::handleStats);
                server.createContext("/api/stats", this::handleStats);
                server.createContext("/api/stats.json", this::handleStats);
            }
            if (cfg.featureDataApi()) {
                server.createContext("/api/data", this::handleDataApi);
            }
            if (cfg.featureHealthApi()) {
                server.createContext("/api/health", this::handleHealth);
            }
            if (cfg.featureLogsTail() || cfg.featureLogsDownload()) {
                server.createContext("/api/logs", this::handleLogsApi);
            }
            server.setExecutor(null);
            server.start();
            plugin.getLogger().info(
                    "Root-Webstat listening on http://" + displayHost(host) + ":" + port
                            + "/  features=" + cfg.featuresJson());
        } catch (IOException ex) {
            plugin.getLogger().warning("Webstat HTTP failed to start (port "
                    + cfg.port() + "): " + ex.getMessage());
            server = null;
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    public String publicUrl() {
        WebstatConfig cfg = plugin.config();
        return "http://" + displayHost(cfg.bind()) + ":" + cfg.port() + "/";
    }

    private static String displayHost(String host) {
        if (host == null || host.isBlank() || "0.0.0.0".equals(host)) {
            return "127.0.0.1";
        }
        return host;
    }

    private Path webRoot() {
        return RootRecordFolders.dir(plugin).toPath().resolve("webstat").resolve("web");
    }

    private void ensureDefaultWebFiles() {
        Path root = webRoot();
        try {
            Files.createDirectories(root);
            String version = plugin.getDescription().getVersion();
            Path stamp = root.resolve(".ui-version");
            String onDisk = Files.isRegularFile(stamp) ? Files.readString(stamp).trim() : "";
            boolean force = !version.equals(onDisk);
            WebstatConfig cfg = plugin.config();
            if (cfg.featureHttpSite()) {
                copyWebResource(root.resolve("index.html"), "web/index.html", force);
                copyWebResource(root.resolve("data.html"), "web/data.html", force);
                copyWebResource(root.resolve("style.css"), "web/style.css", force);
                copyWebResource(root.resolve("app.js"), "web/app.js", force);
                copyWebResource(root.resolve("data.js"), "web/data.js", force);
            }
            if (cfg.featureLogsPage()) {
                copyWebResource(root.resolve("logs.html"), "web/logs.html", force);
                copyWebResource(root.resolve("logs.js"), "web/logs.js", force);
                if (!cfg.featureHttpSite()) {
                    copyWebResource(root.resolve("style.css"), "web/style.css", force);
                }
            }
            if (force) {
                Files.writeString(stamp, version);
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not write webstat web files: " + ex.getMessage());
        }
    }

    private void copyWebResource(Path target, String resource, boolean force) throws IOException {
        if (!force && Files.isRegularFile(target)) {
            return;
        }
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) {
                return;
            }
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean authorized(HttpExchange exchange) {
        String token = plugin.config().token();
        if (token == null || token.isBlank()) {
            return true;
        }
        String q = exchange.getRequestURI().getRawQuery();
        if (q != null && q.contains("token=" + token)) {
            return true;
        }
        String header = exchange.getRequestHeaders().getFirst("X-RootWebstat-Token");
        return token.equals(header);
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            write(exchange, 401, "text/plain; charset=utf-8", "unauthorized");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path == null || path.equals("/") || path.isBlank()) {
            path = "/index.html";
        }
        if (path.equals("/logs") || path.equals("/logs/")) {
            path = "/logs.html";
        }
        if ((path.equals("/logs.html") || path.equals("/logs.js")) && !plugin.config().featureLogsPage()) {
            write(exchange, 403, "application/json; charset=utf-8", "{\"error\":\"feature_disabled\"}");
            return;
        }
        if (!plugin.config().featureHttpSite()
                && !path.equals("/logs.html")
                && !path.equals("/logs.js")
                && !path.equals("/style.css")) {
            write(exchange, 403, "application/json; charset=utf-8", "{\"error\":\"feature_disabled\"}");
            return;
        }
        if (path.equals("/stats.json")) {
            if (!plugin.config().featureStatsApi()) {
                write(exchange, 403, "application/json; charset=utf-8", "{\"error\":\"feature_disabled\"}");
                return;
            }
            handleStats(exchange);
            return;
        }
        Path file = webRoot().resolve(path.substring(1)).normalize();
        if (!file.startsWith(webRoot()) || !Files.isRegularFile(file)) {
            write(exchange, 404, "text/plain; charset=utf-8", "not found");
            return;
        }
        String type = contentType(file.getFileName().toString());
        byte[] body = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private void handleStats(HttpExchange exchange) throws IOException {
        if (!plugin.config().featureStatsApi()) {
            write(exchange, 403, "application/json; charset=utf-8", "{\"error\":\"feature_disabled\"}");
            return;
        }
        if (!authorized(exchange)) {
            write(exchange, 401, "application/json; charset=utf-8", "{\"error\":\"unauthorized\"}");
            return;
        }
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            corsOptions(exchange);
            return;
        }
        write(exchange, 200, "application/json; charset=utf-8", plugin.stats().cachedJson());
    }

    private void handleDataApi(HttpExchange exchange) throws IOException {
        if (!plugin.config().featureDataApi()) {
            write(exchange, 403, "application/json; charset=utf-8", "{\"error\":\"feature_disabled\"}");
            return;
        }
        if (!authorized(exchange)) {
            write(exchange, 401, "application/json; charset=utf-8", "{\"error\":\"unauthorized\"}");
            return;
        }
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            corsOptions(exchange);
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path == null) {
            write(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"not_found\"}");
            return;
        }
        if (path.equals("/api/data")
                || path.equals("/api/data/")
                || path.equals("/api/data/index.json")
                || path.equals("/api/data/catalog.json")) {
            write(exchange, 200, "application/json; charset=utf-8", plugin.dataCatalog().catalogJson());
            return;
        }
        String prefix = "/api/data/";
        if (!path.startsWith(prefix) || !path.endsWith(".json")) {
            write(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"not_found\"}");
            return;
        }
        String id = path.substring(prefix.length(), path.length() - ".json".length());
        String json = plugin.dataCatalog().datasetJson(id);
        if (json == null) {
            write(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"unknown_dataset\"}");
            return;
        }
        write(exchange, 200, "application/json; charset=utf-8", json);
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!plugin.config().featureHealthApi()) {
            write(exchange, 403, "application/json; charset=utf-8", "{\"error\":\"feature_disabled\"}");
            return;
        }
        String json = "{\"ok\":true,\"port\":" + plugin.config().port()
                + ",\"computed_at_ms\":" + plugin.stats().computedAtEpochMs()
                + ",\"features\":" + plugin.config().featuresJson() + "}";
        write(exchange, 200, "application/json; charset=utf-8", json);
    }

    private void handleLogsApi(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            write(exchange, 401, "application/json; charset=utf-8", "{\"error\":\"unauthorized\"}");
            return;
        }
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            corsOptions(exchange);
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path == null) {
            write(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"not_found\"}");
            return;
        }
        if (path.equals("/api/logs/tail.json") || path.equals("/api/logs/tail")) {
            if (!plugin.config().featureLogsTail()) {
                write(exchange, 403, "application/json; charset=utf-8", "{\"error\":\"feature_disabled\"}");
                return;
            }
            handleLogsTail(exchange);
            return;
        }
        if (path.equals("/api/logs/latest.log") || path.equals("/api/logs/download")) {
            if (!plugin.config().featureLogsDownload()) {
                write(exchange, 403, "application/json; charset=utf-8", "{\"error\":\"feature_disabled\"}");
                return;
            }
            handleLogsDownload(exchange);
            return;
        }
        write(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"not_found\"}");
    }

    private void handleLogsTail(HttpExchange exchange) throws IOException {
        int max = plugin.config().logsMaxTailLines();
        int lines = Math.min(max, Math.max(1, queryInt(exchange.getRequestURI(), "lines", Math.min(200, max))));
        WebstatLogReader.TailResult tail = WebstatLogReader.readTail(lines);
        if (!tail.found()) {
            write(exchange, 404, "application/json; charset=utf-8",
                    "{\"ok\":false,\"error\":\"log_not_found\",\"path\":\"" + esc(tail.path()) + "\"}");
            return;
        }
        StringBuilder sb = new StringBuilder(Math.max(256, tail.lines().size() * 80));
        sb.append("{\"ok\":true,\"schema\":\"root-webstat/logs-tail/v1\",");
        sb.append("\"server\":\"").append(esc(plugin.displayServerName())).append("\",");
        sb.append("\"path\":\"").append(esc(tail.path())).append("\",");
        sb.append("\"mtime_ms\":").append(tail.mtimeMs()).append(',');
        sb.append("\"size_bytes\":").append(tail.sizeBytes()).append(',');
        sb.append("\"truncated\":").append(tail.truncated()).append(',');
        sb.append("\"line_count\":").append(tail.lines().size()).append(',');
        sb.append("\"lines\":[");
        List<String> list = tail.lines();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(esc(list.get(i))).append('"');
        }
        sb.append("]}");
        write(exchange, 200, "application/json; charset=utf-8", sb.toString());
    }

    private void handleLogsDownload(HttpExchange exchange) throws IOException {
        int max = plugin.config().logsMaxDownloadBytes();
        int bytes = Math.min(max, Math.max(4096, queryInt(exchange.getRequestURI(), "bytes", max)));
        byte[] body = WebstatLogReader.readLastBytes(bytes);
        if (body == null) {
            write(exchange, 404, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"log_not_found\"}");
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"latest-tail.log\"");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static int queryInt(URI uri, String key, int fallback) {
        String q = uri == null ? null : uri.getRawQuery();
        if (q == null || q.isBlank()) {
            return fallback;
        }
        for (String part : q.split("&")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            if (!key.equals(part.substring(0, eq))) {
                continue;
            }
            try {
                return Integer.parseInt(part.substring(eq + 1));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private static void corsOptions(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "X-RootWebstat-Token");
        exchange.sendResponseHeaders(204, -1);
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
        if (name.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        return "application/octet-stream";
    }

    private static void write(HttpExchange exchange, int code, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
