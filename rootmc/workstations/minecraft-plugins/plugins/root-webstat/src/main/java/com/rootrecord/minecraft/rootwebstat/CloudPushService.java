package com.rootrecord.minecraft.rootwebstat;

import com.rootrecord.minecraft.common.config.RootRecordCloudConfig;
import org.bukkit.Bukkit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** Persists stats.json on disk and POSTs to api.rootmc with server credentials. */
public final class CloudPushService {

    private final RootWebstatPlugin plugin;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public CloudPushService(RootWebstatPlugin plugin) {
        this.plugin = plugin;
    }

    public void writeLocalSnapshot(String json) {
        try {
            Path dir = com.rootrecord.minecraft.common.RootRecordFolders.dir(plugin)
                    .toPath()
                    .resolve("webstat");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("stats.json"), json, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not write webstat/stats.json: " + ex.getMessage());
        }
    }

    public void pushNow() {
        if (!plugin.config().pushEnabled()) {
            return;
        }
        RootRecordCloudConfig.CloudSettings cloud =
                RootRecordCloudConfig.resolve(plugin, null);
        if (!cloud.hasServerCredentials()) {
            plugin.getLogger().fine("Webstat cloud push skipped — set cloud.server-id / server-secret.");
            return;
        }
        String base = cloud.apiBase();
        if (base == null || base.isBlank()) {
            base = "https://api.rootmc.net";
        }
        String serverId = cloud.serverId();
        String url = base.replaceAll("/$", "")
                + "/api/rootmc/server/"
                + encode(serverId)
                + "/webstat";
        String body = plugin.stats().cachedJson();
        String secret = cloud.serverSecret();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(20))
                        .header("Content-Type", "application/json")
                        .header("X-RootStat-Server-Id", serverId)
                        .header("X-RootStat-Server-Secret", secret)
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                int code = res.statusCode();
                if (code >= 200 && code < 300) {
                    plugin.getLogger().info("Webstat pushed to cloud (" + code + ").");
                } else {
                    plugin.getLogger().warning("Webstat push HTTP " + code + ": " + trim(res.body()));
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Webstat push failed: " + ex.getMessage());
            }
        });
    }

    private static String encode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String trim(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() > 160 ? t.substring(0, 160) + "…" : t;
    }
}
