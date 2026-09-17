package com.rootrecord.minecraft.common.config;

/**
 * API base URLs for Paper → Cloudflare Worker.
 * Public default is production {@code api.rootmc.net}. Minecraft-only.
 * Do not use Ava origin hostnames ({@code ava-origin.rootmc.net}, 127.0.0.1:8787).
 */
public final class RootMcApiBases {

    public static final String PRODUCTION = "https://api.rootmc.net";
    /** Same as production on AVA-CORE. Local Ava origin is not a Minecraft API. */
    public static final String LOCAL_EDGE = "https://api.rootmc.net";

    private RootMcApiBases() {}

    public static String normalize(String base) {
        if (base == null || base.isBlank()) {
            return PRODUCTION;
        }
        return base.trim().replaceAll("/+$", "");
    }

    /** Default when configs omit api-base — public Cloudflare Worker. */
    public static String defaultBase() {
        return PRODUCTION;
    }

    /**
     * Prefer local edge for production (avoids Worker free-tier 1027), but callers must
     * fall back to {@link #PRODUCTION} when the tunnel is down (CF 530 / 1033).
     */
    public static String preferredBase(String configured) {
        String p = normalize(configured);
        if (p.equalsIgnoreCase(PRODUCTION)) {
            return LOCAL_EDGE;
        }
        return p;
    }

    /** Fallback when preferred base fails (tunnel down, DNS, etc.). */
    public static String fallbackBase(String preferred) {
        String p = normalize(preferred);
        if (p.equalsIgnoreCase(LOCAL_EDGE)) {
            return PRODUCTION;
        }
        if (p.equalsIgnoreCase(PRODUCTION)) {
            return LOCAL_EDGE;
        }
        return PRODUCTION;
    }

    /**
     * Alternate base to try after HTTP 429 / CF 1027.
     * @return null if no useful alternate
     */
    public static String alternateAfterThrottle(String primary) {
        String p = normalize(primary);
        if (p.equalsIgnoreCase(PRODUCTION)) {
            return LOCAL_EDGE;
        }
        if (p.equalsIgnoreCase(LOCAL_EDGE)) {
            return PRODUCTION;
        }
        return LOCAL_EDGE;
    }

    public static boolean looksLikeThrottle(int status, String body) {
        if (status == 429) {
            return true;
        }
        if (body == null) {
            return false;
        }
        String b = body.toLowerCase();
        return b.contains("error code: 1027") || b.contains("error code 1027") || b.contains("1027");
    }

    public static boolean looksLikeThrottleMessage(String message) {
        if (message == null) {
            return false;
        }
        String m = message.toLowerCase();
        return m.contains("http 429") || m.contains("1027") || m.contains("too many requests");
    }

    /** Tunnel / edge unreachable — fall back to production Worker. */
    public static boolean looksLikeEdgeDownMessage(String message) {
        if (message == null) {
            return false;
        }
        String m = message.toLowerCase();
        return m.contains("http 502")
                || m.contains("http 530")
                || m.contains("http 1033")
                || m.contains("error code: 502")
                || m.contains("error code 502")
                || m.contains("error code: 1033")
                || m.contains("error code 1033")
                || m.contains("cloudflare tunnel")
                || m.contains("connection refused")
                || m.contains("timed out")
                || m.contains("unknown host")
                || m.contains("upstream unavailable");
    }

    /**
     * HTTP status from CF / local gateway when OptiPlex tunnel origin is down.
     * 502 was missing historically — Shockbyte kept failing on api-local during tunnel blips.
     */
    public static boolean looksLikeEdgeDownStatus(int status, String body) {
        if (status == 502 || status == 530 || status == 1033) {
            return true;
        }
        return looksLikeEdgeDownMessage("HTTP " + status + ": " + (body == null ? "" : body));
    }
}
