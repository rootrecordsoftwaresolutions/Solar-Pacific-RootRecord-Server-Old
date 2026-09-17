package com.rootrecord.minecraft.roottimes.ip;

import com.rootrecord.minecraft.roottimes.timezone.PlayerTimezone;

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

/** IP → fixed-offset timezone key (same ip-api.com fields as Root-Activity). */
public final class IpTimezoneResolver {

    private static final Pattern STATUS_PATTERN = Pattern.compile("\"status\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern OFFSET_PATTERN = Pattern.compile("\"offset\"\\s*:\\s*(-?\\d+)");

    private record CacheEntry(String timezoneKey, Instant expiresAt) {}

    private final boolean enabled;
    private final String urlTemplate;
    private final int cacheHours;
    private final HttpClient http;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public IpTimezoneResolver(boolean enabled, String urlTemplate, int cacheHours) {
        this.enabled = enabled;
        this.urlTemplate = urlTemplate == null || urlTemplate.isBlank()
                ? "http://ip-api.com/json/{ip}?fields=status,offset,timezone"
                : urlTemplate;
        this.cacheHours = Math.max(1, cacheHours);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
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
        if (!enabled || !isResolvableIp(ip)) {
            return Optional.empty();
        }
        CacheEntry cached = cache.get(ip);
        if (cached != null && cached.expiresAt.isAfter(Instant.now())) {
            return Optional.of(cached.timezoneKey);
        }
        try {
            String url = urlTemplate.replace("{ip}", ip);
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
            Optional<PlayerTimezone> def = PlayerTimezone.nearestOffsetSeconds(offsetSeconds);
            if (def.isEmpty()) {
                return Optional.empty();
            }
            String key = def.get().key();
            cache.put(ip, new CacheEntry(key, Instant.now().plus(Duration.ofHours(cacheHours))));
            return Optional.of(key);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
