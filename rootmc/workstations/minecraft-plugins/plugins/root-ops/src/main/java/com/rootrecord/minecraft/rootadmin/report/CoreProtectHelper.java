package com.rootrecord.minecraft.rootadmin.report;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** CoreProtect lookup via reflection (no compile-time CP dependency). */
public final class CoreProtectHelper {

    private CoreProtectHelper() {}

    public static List<String> recentActions(String username, int lookbackSeconds) {
        if (username == null || username.isBlank()) return List.of();
        Plugin cp = Bukkit.getPluginManager().getPlugin("CoreProtect");
        if (cp == null || !cp.isEnabled()) {
            return List.of("(CoreProtect not installed — block history skipped)");
        }
        try {
            Method getApi = cp.getClass().getMethod("getAPI");
            Object api = getApi.invoke(cp);
            if (api == null) return List.of("(CoreProtect API unavailable)");
            Method version = api.getClass().getMethod("APIVersion");
            int apiVersion = (Integer) version.invoke(api);
            if (apiVersion < 7) {
                return List.of("(CoreProtect API too old — need v7+)");
            }
            Method lookup = api.getClass().getMethod(
                    "performLookup",
                    int.class,
                    List.class,
                    List.class,
                    List.class,
                    List.class,
                    boolean.class,
                    boolean.class,
                    boolean.class,
                    boolean.class);
            @SuppressWarnings("unchecked")
            List<String[]> rows = (List<String[]>) lookup.invoke(
                    api,
                    Math.max(60, lookbackSeconds),
                    List.of(username),
                    null,
                    null,
                    null,
                    false,
                    false,
                    true,
                    false);
            if (rows == null || rows.isEmpty()) {
                return List.of("(No CoreProtect entries in last " + (lookbackSeconds / 60) + " min)");
            }
            List<String> lines = new ArrayList<>();
            int limit = Math.min(rows.size(), 80);
            for (int i = 0; i < limit; i++) {
                String[] row = rows.get(i);
                if (row == null || row.length < 4) continue;
                String time = row.length > 0 ? row[0] : "?";
                String user = row.length > 1 ? row[1] : username;
                String action = row.length > 3 ? row[3] : "?";
                String block = row.length > 4 ? row[4] : "?";
                String coords = row.length > 8 ? row[5] + " " + row[6] + " " + row[7] + " @ " + row[8] : "";
                lines.add(String.format(Locale.ROOT, "[%s] %s %s %s %s", time, user, action, block, coords).trim());
            }
            if (rows.size() > limit) {
                lines.add("… +" + (rows.size() - limit) + " more CoreProtect rows");
            }
            return lines;
        } catch (ReflectiveOperationException ex) {
            return List.of("(CoreProtect lookup failed: " + ex.getClass().getSimpleName() + ")");
        }
    }
}
