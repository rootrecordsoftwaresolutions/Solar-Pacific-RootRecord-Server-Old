package com.rootrecord.minecraft.roothelp.cloud;

import com.rootrecord.minecraft.common.config.RootRecordCloudConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HelpCloudClient {

    private static final Pattern JSON_BOOL = Pattern.compile("\"linked\"\\s*:\\s*(true|false)");
    private static final Pattern JSON_DISCORD_LINKED = Pattern.compile("\"discord_linked\"\\s*:\\s*(true|false)");

    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();
    private final RootRecordCloudConfig.CloudSettings settings;

    public HelpCloudClient(RootRecordCloudConfig.CloudSettings settings) {
        this.settings = settings;
    }

    public boolean hasCredentials() {
        return settings.hasServerCredentials();
    }

    public void submitFeedback(String uuid, String username, String serverName, String message)
            throws IOException, InterruptedException {
        String body = "{\"uuid\":\"" + escapeJson(uuid)
                + "\",\"username\":\"" + escapeJson(username)
                + "\",\"server_name\":\"" + escapeJson(serverName == null ? "" : serverName)
                + "\",\"message\":\"" + escapeJson(message) + "\"}";
        post("/api/rootmc/ingame-feedback", body);
    }

    public void submitCommandTestReport(
            String uuid,
            String username,
            String world,
            String testKey,
            String command,
            String note,
            int completed,
            int total)
            throws IOException, InterruptedException {
        String body = "{\"uuid\":\"" + escapeJson(uuid)
                + "\",\"username\":\"" + escapeJson(username)
                + "\",\"world\":\"" + escapeJson(world == null ? "" : world)
                + "\",\"test_key\":\"" + escapeJson(testKey)
                + "\",\"command\":\"" + escapeJson(command)
                + "\",\"note\":\"" + escapeJson(note == null ? "" : note)
                + "\",\"completed\":" + completed
                + ",\"total\":" + total + "}";
        post("/api/rootmc/command-test-report", body);
    }

    public LinkProfile fetchLinkProfile(String uuid) throws IOException, InterruptedException {
        String json = get("/api/realm/minecraft/link/status?uuid=" + uuid);
        Matcher linked = JSON_BOOL.matcher(json);
        if (!linked.find() || !"true".equals(linked.group(1))) {
            return LinkProfile.unlinked(
                    extractString(json, "stats_url"),
                    extractString(json, "verify_url"));
        }
        boolean discordLinked = false;
        Matcher discord = JSON_DISCORD_LINKED.matcher(json);
        if (discord.find()) {
            discordLinked = "true".equals(discord.group(1));
        }
        String email = extractString(json, "email");
        String account = email != null && !email.isBlank() ? email : extractString(json, "account_id");
        return new LinkProfile(
                true,
                discordLinked,
                account,
                extractString(json, "discord_username"),
                extractString(json, "discord_user_id"),
                extractString(json, "discord_profile_url"),
                extractString(json, "stats_url"),
                extractString(json, "verify_url"));
    }

    private String get(String path) throws IOException, InterruptedException {
        return exchange("GET", path, null);
    }

    private void post(String path, String jsonBody) throws IOException, InterruptedException {
        exchange("POST", path, jsonBody);
    }

    private String exchange(String method, String path, String jsonBody)
            throws IOException, InterruptedException {
        String configured = com.rootrecord.minecraft.common.config.RootMcApiBases.normalize(settings.apiBase());
        String preferred = com.rootrecord.minecraft.common.config.RootMcApiBases.preferredBase(configured);
        try {
            return exchangeOnce(preferred, method, path, jsonBody);
        } catch (IOException first) {
            String alt = com.rootrecord.minecraft.common.config.RootMcApiBases.fallbackBase(preferred);
            if (alt == null || alt.equalsIgnoreCase(preferred)) {
                throw first;
            }
            boolean retry = com.rootrecord.minecraft.common.config.RootMcApiBases.looksLikeThrottleMessage(
                            first.getMessage())
                    || com.rootrecord.minecraft.common.config.RootMcApiBases.looksLikeEdgeDownMessage(
                            first.getMessage());
            if (!retry) {
                throw first;
            }
            return exchangeOnce(alt, method, path, jsonBody);
        }
    }

    private String exchangeOnce(String base, String method, String path, String jsonBody)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(base + path))
                .timeout(Duration.ofSeconds(18));
        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody));
        } else {
            builder.GET();
        }
        HttpRequest request = authorized(builder);
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            String body = response.body() == null ? "" : response.body();
            throw new IOException("HTTP " + response.statusCode() + ": " + body);
        }
        return response.body() == null ? "" : response.body();
    }

    private HttpRequest authorized(HttpRequest.Builder builder) {
        return builder
                .header("X-RootStat-Server-Id", settings.serverId())
                .header("X-RootStat-Server-Secret", settings.serverSecret())
                .build();
    }

    private static String extractString(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        if (!m.find()) {
            return null;
        }
        return m.group(1);
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public record LinkProfile(
            boolean minecraftLinked,
            boolean discordLinked,
            String accountLabel,
            String discordUsername,
            String discordUserId,
            String discordProfileUrl,
            String statsUrl,
            String verifyUrl) {

        static LinkProfile unlinked(String statsUrl, String verifyUrl) {
            return new LinkProfile(false, false, null, null, null, null, statsUrl, verifyUrl);
        }
    }
}
