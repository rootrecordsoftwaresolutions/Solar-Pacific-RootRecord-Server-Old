package com.rootrecord.minecraft.rootcore.suite;

import com.rootrecord.minecraft.rootcore.RootCorePlugin;
import com.rootrecord.minecraft.rootcore.api.RootCoreApi;
import com.rootrecord.minecraft.rootcore.license.LicenseConnectService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Sync identity/plugin checks + optional async API reachability for /rootcore connect.
 */
public final class NetworkConnectProbe {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private NetworkConnectProbe() {
    }

    public enum Level {
        OK, FAIL, WARN
    }

    public record Line(Level level, String text) {
    }

    public record SyncResult(
            List<Line> lines,
            boolean connected,
            String firstFailHint
    ) {
    }

    public static SyncResult evaluateSync(RootCorePlugin plugin) {
        List<Line> lines = new ArrayList<>();
        String firstFail = null;

        RootCoreApi api = plugin.api();
        for (String name : SuiteSpine.REQUIRED_PLUGINS) {
            Plugin p = Bukkit.getPluginManager().getPlugin(name);
            boolean ok = p != null && p.isEnabled();
            if (ok) {
                lines.add(new Line(Level.OK, "Plugin " + name + " enabled"));
            } else {
                lines.add(new Line(Level.FAIL, "Plugin " + name + " missing or disabled"));
                if (firstFail == null) {
                    firstFail = "Install and enable " + name + ".jar";
                }
            }
        }

        if (api != null && api.hasCloudCredentials()) {
            lines.add(new Line(Level.OK, "Cloud credentials present (server-id + secret)"));
        } else {
            lines.add(new Line(Level.FAIL, "Cloud credentials incomplete"));
            if (firstFail == null) {
                firstFail = "Set product-key in root-core.yml and restart so bind fills cloud.yml";
            }
        }

        LicenseConnectService connect = plugin.licenseConnect();
        if (connect == null) {
            lines.add(new Line(Level.FAIL, "License connect service unavailable"));
            if (firstFail == null) {
                firstFail = "Reload Root-Core or check console for license errors";
            }
        } else {
            if (connect.productKeyPresent()) {
                lines.add(new Line(Level.OK, "Product-key present"));
            } else {
                lines.add(new Line(Level.FAIL, "Product-key blank in root-core.yml"));
                if (firstFail == null) {
                    firstFail = "Set product-key in plugins/RootMC/root-core.yml (keys portal)";
                }
            }

            String err = connect.lastPresenceError();
            String okAt = connect.lastPresenceOk();
            boolean bound = connect.boundServerId() != null && !connect.boundServerId().isBlank();
            boolean presenceOk = (err == null || err.isBlank()) && okAt != null && !okAt.isBlank();
            if (presenceOk) {
                lines.add(new Line(Level.OK, "License presence OK @ " + okAt));
            } else if (bound && (err == null || err.isBlank())) {
                lines.add(new Line(Level.OK, "License bind server-id set (presence pending or OK)"));
            } else if (err != null && !err.isBlank()) {
                lines.add(new Line(Level.FAIL, "License presence fail — " + err));
                if (firstFail == null) {
                    firstFail = "Fix license presence: /rootcore reload, check api-base and key";
                }
            } else {
                lines.add(new Line(Level.FAIL, "License presence pending — wait or /rootcore reload"));
                if (firstFail == null) {
                    firstFail = "Wait for bind/presence, or run /rootcore reload";
                }
            }
        }

        String apiBase = resolveApiBase(plugin, api, connect);
        if (apiBase == null || apiBase.isBlank()) {
            lines.add(new Line(Level.FAIL, "API base blank"));
            if (firstFail == null) {
                firstFail = "Set cloud.api-base or license.api-base to https://api.rootmc.net";
            }
        }

        for (SuiteSpine.Pack pack : SuiteSpine.RECOMMENDED_PACKS) {
            List<String> missing = new ArrayList<>();
            List<String> present = new ArrayList<>();
            for (String name : pack.plugins()) {
                Plugin p = Bukkit.getPluginManager().getPlugin(name);
                if (p != null && p.isEnabled()) {
                    present.add(name);
                } else {
                    missing.add(name);
                }
            }
            if (missing.isEmpty()) {
                lines.add(new Line(Level.OK, "Pack " + pack.label() + ": " + String.join(", ", present)));
            } else if (present.isEmpty()) {
                lines.add(new Line(Level.WARN, "Pack " + pack.label() + ": none installed ("
                        + String.join(", ", missing) + ")"));
            } else {
                lines.add(new Line(Level.WARN, "Pack " + pack.label() + ": missing "
                        + String.join(", ", missing)));
            }
        }

        boolean connected = firstFail == null;
        return new SyncResult(lines, connected, firstFail == null ? "" : firstFail);
    }

    public static String resolveApiBase(
            RootCorePlugin plugin,
            RootCoreApi api,
            LicenseConnectService connect
    ) {
        if (connect != null) {
            String license = connect.licenseApiBase();
            if (license != null && !license.isBlank()) {
                return trimSlash(license);
            }
        }
        if (api != null) {
            String base = api.apiBase();
            if (base != null && !base.isBlank()) {
                return trimSlash(base);
            }
        }
        return "https://api.rootmc.net";
    }

    /** Returns null on success (reachable), or an error message. */
    public static String probeApiReachable(String apiBase) {
        if (apiBase == null || apiBase.isBlank()) {
            return "API base blank";
        }
        String url = trimSlash(apiBase);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .header("User-Agent", "RootMC-RootCore-Connect/1")
                    .build();
            HttpResponse<Void> response = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
            int code = response.statusCode();
            // Any HTTP response means the host is reachable (404/401 still counts).
            if (code >= 100 && code < 600) {
                return null;
            }
            return "Unexpected HTTP " + code;
        } catch (Exception ex) {
            String msg = ex.getMessage();
            if (msg == null || msg.isBlank()) {
                msg = ex.getClass().getSimpleName();
            }
            return msg;
        }
    }

    private static String trimSlash(String url) {
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }
}
