package com.rootrecord.minecraft.rootcore.transfer;

import com.rootrecord.minecraft.common.config.RootRecordCloudConfig;
import com.rootrecord.minecraft.rootcore.RootCorePlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fetches the global /goto peer list from api.rootmc.net. */
public final class TransferMeshClient {

    private final RootCorePlugin plugin;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();

    public TransferMeshClient(RootCorePlugin plugin) {
        this.plugin = plugin;
    }

    public List<MeshPeer> fetchPeers() throws Exception {
        FileConfiguration cfg = plugin.yamlConfig().config();
        RootRecordCloudConfig.CloudSettings cloud = RootRecordCloudConfig.resolve(plugin, cfg);
        if (!cloud.hasServerCredentials()) {
            throw new IllegalStateException("cloud.yml server-id/secret missing — bind Root-Core first");
        }
        String base = cloud.apiBase().replaceAll("/+$", "");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base + "/api/rootmc/transfer-mesh"))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("X-RootStat-Server-Id", cloud.serverId())
                .header("X-RootStat-Server-Secret", cloud.serverSecret())
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("transfer-mesh HTTP " + response.statusCode());
        }
        return parsePeers(response.body());
    }

    static List<MeshPeer> parsePeers(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        List<MeshPeer> out = new ArrayList<>();
        // Split peer objects roughly
        int peersIdx = body.indexOf("\"peers\"");
        String slice = peersIdx >= 0 ? body.substring(peersIdx) : body;
        Matcher m = Pattern.compile("\\{[^{}]*\"slug\"\\s*:\\s*\"([^\"]+)\"[^{}]*\\}", Pattern.DOTALL)
                .matcher(slice);
        while (m.find()) {
            String obj = m.group();
            String slug = jsonString(obj, "slug");
            String label = jsonString(obj, "label");
            String host = jsonString(obj, "host");
            int port = jsonInt(obj, "port", 25565);
            String kind = jsonString(obj, "kind");
            String serverId = jsonString(obj, "server_id");
            Boolean online = jsonBool(obj, "online");
            List<String> aliases = jsonStringArray(obj, "aliases");
            if (slug != null && host != null && !host.isBlank() && port > 0) {
                out.add(new MeshPeer(
                        slug,
                        label == null || label.isBlank() ? slug : label,
                        host,
                        port,
                        kind == null ? "myserver" : kind,
                        serverId,
                        online,
                        aliases));
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static String jsonString(String obj, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(obj);
        return m.find() ? m.group(1) : null;
    }

    private static int jsonInt(String obj, String key, int def) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)").matcher(obj);
        if (!m.find()) {
            return def;
        }
        try {
            return Integer.parseInt(m.group(1));
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private static Boolean jsonBool(String obj, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false|null)").matcher(obj);
        if (!m.find()) {
            return null;
        }
        String v = m.group(1);
        if ("null".equals(v)) {
            return null;
        }
        return Boolean.parseBoolean(v);
    }

    private static List<String> jsonStringArray(String obj, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL)
                .matcher(obj);
        if (!m.find()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        Matcher s = Pattern.compile("\"([^\"]+)\"").matcher(m.group(1));
        while (s.find()) {
            out.add(s.group(1));
        }
        return out;
    }
}
