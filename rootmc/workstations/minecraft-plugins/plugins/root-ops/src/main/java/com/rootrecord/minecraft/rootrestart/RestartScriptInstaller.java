package com.rootrecord.minecraft.rootrestart;

import com.rootrecord.minecraft.common.RootRecordFolders;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

final class RestartScriptInstaller {

    static final String HELPER_NAME = "restart-helper.sh";
    static final String EXPECTED_SPIGOT_SCRIPT = "./plugins/RootMC/restart-helper.sh";

    private RestartScriptInstaller() {}

    static java.io.File ensure(JavaPlugin plugin) {
        RootRecordFolders.ensureDir(plugin);
        java.io.File out = new java.io.File(RootRecordFolders.dir(plugin), HELPER_NAME);
        try (InputStream in = plugin.getResource(HELPER_NAME)) {
            if (in == null) {
                plugin.getLogger().warning("restart-helper.sh missing from plugin jar.");
                return out;
            }
            Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
            out.setExecutable(true, false);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not write restart-helper.sh: " + ex.getMessage());
        }
        return out;
    }

    static void verifySpigotConfig(JavaPlugin plugin) {
        java.io.File serverRoot = plugin.getServer().getWorldContainer();
        java.io.File spigot = new java.io.File(serverRoot, "spigot.yml");
        if (!spigot.isFile()) {
            plugin.getLogger().warning(
                    "spigot.yml not found — set settings.restart-script to " + EXPECTED_SPIGOT_SCRIPT);
            return;
        }
        try {
            String text = Files.readString(spigot.toPath(), StandardCharsets.UTF_8);
            if (text.contains("restart-helper.sh")) {
                plugin.getLogger().info("restart-script points at RootMC restart-helper.sh");
                return;
            }
            String patched = text
                    .replace("restart-script: ./start.sh", "restart-script: " + EXPECTED_SPIGOT_SCRIPT)
                    .replace("restart-script: './start.sh'", "restart-script: " + EXPECTED_SPIGOT_SCRIPT);
            if (!patched.equals(text)) {
                Files.writeString(spigot.toPath(), patched, StandardCharsets.UTF_8);
                plugin.getLogger().warning(
                        "spigot.yml restart-script was ./start.sh (missing on Shockbyte) — "
                                + "auto-patched to " + EXPECTED_SPIGOT_SCRIPT);
                return;
            }
            plugin.getLogger().warning(
                    "Verify spigot.yml settings.restart-script is: " + EXPECTED_SPIGOT_SCRIPT);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not read spigot.yml: " + ex.getMessage());
        }
    }
}
