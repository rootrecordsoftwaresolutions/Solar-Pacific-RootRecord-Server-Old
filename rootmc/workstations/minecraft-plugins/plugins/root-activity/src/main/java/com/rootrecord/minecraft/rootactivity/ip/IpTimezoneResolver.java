package com.rootrecord.minecraft.rootactivity.ip;

import com.rootrecord.minecraft.rootactivity.config.ActivityConfig;
import com.rootrecord.minecraft.rootactivity.timezone.TimezoneDef;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IpTimezoneResolver {

    private static final Pattern STATUS_PATTERN = Pattern.compile("\"status\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern OFFSET_PATTERN = Pattern.compile("\"offset\"\\s*:\\s*(-?\\d+)");

    private record CacheEntry(String timezoneKey, Instant expiresAt) {}

    private final ActivityConfig config;
    private final HttpClient http;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public IpTimezoneResolver(ActivityConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public static boolean isResolvableIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        if ("127.0.0.1".equals(ip) || "0.0.0.0".equals(ip) || "::1".equals(ip)) {
            return false;
        }
        if (ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("172.")) {
            return false;
        }
        return true;
    }

    public Optional<String> resolveTimezoneKey(String ip) {
        if (!config.ipLookupEnabled() || !isResolvableIp(ip)) {
            return Optional.empty();
        }
        CacheEntry cached = cache.get(ip);
        if (cached != null && cached.expiresAt.isAfter(Instant.now())) {
            return Optional.of(cached.timezoneKey);
        }
        try {
            String url = config.ipLookupUrlTemplate().replace("{ip}", ip);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            String body = response.body();
            Matcher statusMatcher = STATUS_PATTERN.matcher(body);
            if (!statusMatcher.find() || !"success".equalsIgnoreCase(statusMatcher.group(1))) {
                return Optional.empty();
            }
            Matcher offsetMatcher = OFFSET_PATTERN.matcher(body);
            if (!offsetMatcher.find()) {
                return Optional.empty();
            }
            int offsetSeconds = Integer.parseInt(offsetMatcher.group(1));
            Optional<TimezoneDef> def = TimezoneDef.nearestOffsetSeconds(offsetSeconds);
            if (def.isEmpty()) {
                return Optional.empty();
            }
            String key = def.get().key();
            cache.put(
                    ip,
                    new CacheEntry(
                            key,
                            Instant.now().plus(Duration.ofHours(config.ipCacheHours()))));
            return Optional.of(key);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
