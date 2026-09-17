package com.rootrecord.minecraft.rootcore.license;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Product-key bind + presence against api.rootmc.net (not cloud.yml api-base). */
public final class LicenseConnectClient {

    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    private volatile String licenseApiBase =
            com.rootrecord.minecraft.common.config.RootMcApiBases.PRODUCTION;

    public void setLicenseApiBase(String apiBase) {
        if (apiBase == null || apiBase.isBlank()) {
            this.licenseApiBase = com.rootrecord.minecraft.common.config.RootMcApiBases.defaultBase();
            return;
        }
        this.licenseApiBase = apiBase.replaceAll("/+$", "");
    }

    public String licenseApiBase() {
        return licenseApiBase;
    }

    public BindResult bind(
            String productKey, String serverName, String existingServerId, boolean issueSecret)
            throws IOException, InterruptedException {
        StringBuilder body = new StringBuilder(220);
        body.append('{');
        body.append("\"product_key\":\"").append(escape(productKey)).append('"');
        body.append(",\"server_name\":\"").append(escape(serverName)).append('"');
        if (existingServerId != null && !existingServerId.isBlank()) {
            body.append(",\"server_id\":\"").append(escape(existingServerId.trim())).append('"');
        }
        if (issueSecret) {
            body.append(",\"issue_secret\":true");
        }
        body.append('}');

        HttpResponse<String> response = post("/api/rootmc/license/bind", body.toString());
        if (response.statusCode() >= 400) {
            throw new IOException("bind HTTP " + response.statusCode() + ": " + truncate(response.body()));
        }
        return BindResult.parse(response.body());
    }

    public PresenceResult presence(
            String productKey,
            String serverName,
            String serverId,
            String serverAddress,
            String pluginVersion,
            int onlinePlayers)
            throws IOException, InterruptedException {
        StringBuilder body = new StringBuilder(256);
        body.append('{');
        body.append("\"product_key\":\"").append(escape(productKey)).append('"');
        body.append(",\"server_name\":\"").append(escape(serverName)).append('"');
        if (serverId != null && !serverId.isBlank()) {
            body.append(",\"server_id\":\"").append(escape(serverId.trim())).append('"');
        }
        if (serverAddress != null && !serverAddress.isBlank()) {
            body.append(",\"server_address\":\"").append(escape(serverAddress.trim())).append('"');
        }
        if (pluginVersion != null && !pluginVersion.isBlank()) {
            body.append(",\"plugin_version\":\"").append(escape(pluginVersion.trim())).append('"');
        }
        body.append(",\"online_players\":").append(Math.max(0, onlinePlayers));
        body.append('}');

        HttpResponse<String> response = post("/api/rootmc/license/presence", body.toString());
        if (response.statusCode() >= 400) {
            throw new IOException("presence HTTP " + response.statusCode() + ": " + truncate(response.body()));
        }
        return PresenceResult.parse(response.body());
    }

    private HttpResponse<String> post(String path, String jsonBody)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(licenseApiBase + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c >= 0x20) {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        String t = body.trim();
        return t.length() <= 240 ? t : t.substring(0, 240) + "…";
    }

    public record BindResult(
            boolean ok,
            boolean created,
            String serverId,
            String serverSecret,
            String serverName,
            String apiBase,
            String note) {
        static BindResult parse(String json) {
            return new BindResult(
                    boolField(json, "ok"),
                    boolField(json, "created"),
                    textField(json, "server_id"),
                    textField(json, "server_secret"),
                    textField(json, "server_name"),
                    textField(json, "api_base"),
                    textField(json, "note"));
        }
    }

    public record PresenceResult(boolean ok, String serverId, String serverName, String seenAt) {
        static PresenceResult parse(String json) {
            return new PresenceResult(
                    boolField(json, "ok"),
                    textField(json, "server_id"),
                    textField(json, "server_name"),
                    textField(json, "seen_at"));
        }
    }

    /** Minimal JSON field reader — avoids a JSON dependency in Core. */
    private static String textField(String json, String key) {
        if (json == null || key == null) {
            return "";
        }
        // Require `"key":` so "server_id" cannot partially confuse adjacent keys.
        String needle = "\"" + key + "\":";
        int i = json.indexOf(needle);
        if (i < 0) {
            return "";
        }
        int p = i + needle.length();
        while (p < json.length() && Character.isWhitespace(json.charAt(p))) {
            p++;
        }
        if (p >= json.length() || json.startsWith("null", p)) {
            return "";
        }
        if (json.charAt(p) != '"') {
            return "";
        }
        p++;
        StringBuilder out = new StringBuilder();
        while (p < json.length()) {
            char c = json.charAt(p++);
            if (c == '\\' && p < json.length()) {
                out.append(json.charAt(p++));
                continue;
            }
            if (c == '"') {
                break;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static boolean boolField(String json, String key) {
        if (json == null || key == null) {
            return false;
        }
        String needle = "\"" + key + "\":";
        int i = json.indexOf(needle);
        if (i < 0) {
            return false;
        }
        String rest = json.substring(i + needle.length()).trim();
        return rest.startsWith("true");
    }
}
