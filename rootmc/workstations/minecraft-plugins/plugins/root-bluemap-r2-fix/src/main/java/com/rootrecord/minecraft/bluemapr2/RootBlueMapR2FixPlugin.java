package com.rootrecord.minecraft.bluemapr2;

import com.rootrecord.minecraft.common.bstats.Metrics;
import com.rootrecord.minecraft.common.bstats.RootBStats;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Sets AWS SDK properties before BlueMapS3Storage initializes.
 * Credentials: {@code plugins/RootMC/r2.env} (migrates from legacy plugin data folder).
 */
public final class RootBlueMapR2FixPlugin extends JavaPlugin {

    private static final String ROOTMC_FOLDER = "RootMC";
    private static final String ENV_NAME = "r2.env";

    @Override
    public void onLoad() {
        System.setProperty("aws.requestChecksumCalculation", "WHEN_REQUIRED");
        System.setProperty("aws.responseChecksumValidation", "WHEN_REQUIRED");
        System.setProperty("aws.region", "auto");

        Path envFile = resolveEnvFile();
        Map<String, String> env = loadEnv(envFile);
        if (env.isEmpty()) {
            getLogger().warning(
                    "Missing plugins/RootMC/" + ENV_NAME
                            + " — place R2 keys there (legacy plugins/Root-BlueMap-R2-Fix/r2.env is migrated on load).");
            return;
        }

        setIfPresent("aws.region", env.get("AWS_REGION"));
        setIfPresent("aws.accessKeyId", env.get("R2_ACCESS_KEY_ID"));
        setIfPresent("aws.secretAccessKey", env.get("R2_SECRET_ACCESS_KEY"));

        String key = env.get("R2_ACCESS_KEY_ID");
        if (key != null && key.length() >= 4) {
            getLogger().info(
                    "R2 credentials loaded from plugins/RootMC/" + ENV_NAME
                            + " (access key starts " + key.substring(0, 4) + "...)");
        }
        getLogger().info("R2 signing flags set (aws.requestChecksumCalculation=WHEN_REQUIRED)");
    }

    private Metrics metrics;

    @Override
    public void onEnable() {
        metrics = RootBStats.start(this);
    }

    @Override
    public void onDisable() {
        RootBStats.shutdown(metrics);
    }

    private Path resolveEnvFile() {
        File plugins = getServer().getPluginsFolder();
        File rootMc = new File(plugins, ROOTMC_FOLDER);
        //noinspection ResultOfMethodCallIgnored
        rootMc.mkdirs();
        Path preferred = rootMc.toPath().resolve(ENV_NAME);
        if (Files.isRegularFile(preferred)) {
            return preferred;
        }
        Path legacy = getDataFolder().toPath().resolve(ENV_NAME);
        if (Files.isRegularFile(legacy)) {
            try {
                Files.move(legacy, preferred, StandardCopyOption.REPLACE_EXISTING);
                getLogger().info("Migrated " + ENV_NAME + " → plugins/RootMC/" + ENV_NAME);
                deleteEmptyLegacyFolder();
                return preferred;
            } catch (IOException ex) {
                getLogger().warning("Could not migrate r2.env to RootMC: " + ex.getMessage());
                return legacy;
            }
        }
        return preferred;
    }

    private void deleteEmptyLegacyFolder() {
        File folder = getDataFolder();
        if (!folder.isDirectory()) {
            return;
        }
        File[] leftover = folder.listFiles();
        if (leftover != null && leftover.length == 0) {
            //noinspection ResultOfMethodCallIgnored
            folder.delete();
        }
    }

    private static void setIfPresent(String property, String value) {
        if (value != null && !value.isBlank()) {
            System.setProperty(property, value.trim());
        }
    }

    static Map<String, String> loadEnv(Path path) {
        Map<String, String> out = new HashMap<>();
        if (!Files.isRegularFile(path)) {
            return out;
        }
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                out.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        } catch (IOException e) {
            return out;
        }
        return out;
    }
}
