package com.rootrecord.minecraft.rootannouncer.cloud;

import com.rootrecord.minecraft.common.config.RootRecordCloudConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fetches active season announcer lines from RootMC API. */
public final class SeasonCloudClient {

    private static final Pattern LINE_ENTRY = Pattern.compile("\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\"");

    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();
    private final RootRecordCloudConfig.CloudSettings settings;

    public SeasonCloudClient(RootRecordCloudConfig.CloudSettings settings) {
        this.settings = settings;
    }

    public List<String> fetchSeasonLines() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(settings.apiBase() + "/api/rootmc/season"))
                .timeout(Duration.ofSeconds(18))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode());
        }
        return parseAnnouncerLines(response.body());
    }

    static List<String> parseAnnouncerLines(String json) {
        List<String> lines = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return lines;
        }
        int key = json.indexOf("\"announcer_lines\"");
        if (key < 0) {
            return lines;
        }
        int start = json.indexOf('[', key);
        int end = json.indexOf(']', start);
        if (start < 0 || end <= start) {
            return lines;
        }
        Matcher matcher = LINE_ENTRY.matcher(json.substring(start, end + 1));
        while (matcher.find()) {
            String raw = matcher.group(1);
            if (raw != null && !raw.isBlank()) {
                lines.add(unescapeJson(raw));
            }
        }
        return lines;
    }

    private static String unescapeJson(String value) {
        return value
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }
}
