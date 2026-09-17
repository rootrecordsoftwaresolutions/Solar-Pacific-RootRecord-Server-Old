package com.rootrecord.minecraft.common;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;

/** Resolves the operator-facing server display name from Root-Core. */
public final class RootMcServerDisplay {

    private RootMcServerDisplay() {}

    /** Never blank — falls back to {@code "Server"}. */
    public static String serverName(Plugin consumer) {
        Plugin core = Bukkit.getPluginManager().getPlugin("Root-Core");
        if (core != null && core.isEnabled()) {
            try {
                Object api = core.getClass().getMethod("api").invoke(core);
                if (api != null) {
                    Object name = api.getClass().getMethod("serverName").invoke(api);
                    if (name != null) {
                        String s = String.valueOf(name).trim();
                        if (!s.isEmpty()) {
                            return s;
                        }
                    }
                }
            } catch (ReflectiveOperationException ignored) {
                // fall through to yaml
            }
        }
        File file = RootRecordFolders.configFile(consumer, RootRecordFolders.ROOT_CORE_CONFIG);
        if (file.isFile()) {
            String fromYml = YamlConfiguration.loadConfiguration(file).getString("server-name", "");
            if (fromYml != null && !fromYml.isBlank()) {
                return fromYml.trim();
            }
        }
        return "Server";
    }

    /** Replace {@code {server}} in welcome / message templates. */
    public static String apply(Plugin consumer, String template) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        return template.replace("{server}", serverName(consumer));
    }
}
