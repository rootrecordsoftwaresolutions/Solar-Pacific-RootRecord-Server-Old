package com.rootrecord.minecraft.rootessentials.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import com.rootrecord.minecraft.rootessentials.util.WorthLoader;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record RootEssentialsConfig(
        String mysqlHost,
        int mysqlPort,
        String mysqlDatabase,
        String mysqlUsername,
        String mysqlPassword,
        String mysqlTablePrefix,
        String mysqlJdbcParams,
        String currencySymbol,
        String worthFile,
        String defaultHomeName,
        int maxHomesDefault,
        int maxHomesPro,
        int maxHomesLifetime,
        String spawnWorld,
        List<String> motdLines,
        List<String> rulesLines,
        Map<String, List<String>> kitItems,
        Map<String, Boolean> kitOneTime,
        String newbieKit,
        Map<Material, Double> worthByMaterial) {

    public static RootEssentialsConfig from(JavaPlugin plugin, FileConfiguration cfg) {
        RootMcDatabaseConfig.DatabaseSettings db = RootMcDatabaseConfig.resolve(plugin, cfg);
        Map<Material, Double> worth = WorthLoader.load(
                cfg.getConfigurationSection("economy.worth"),
                cfg.getString("economy.worth-file", "").trim());

        Map<String, List<String>> kits = new HashMap<>();
        Map<String, Boolean> oneTime = new HashMap<>();
        ConfigurationSection kitsSec = cfg.getConfigurationSection("kits");
        if (kitsSec != null) {
            for (String kit : kitsSec.getKeys(false)) {
                ConfigurationSection kitSec = kitsSec.getConfigurationSection(kit);
                if (kitSec == null) continue;
                kits.put(kit.toLowerCase(Locale.ROOT), kitSec.getStringList("items"));
                oneTime.put(kit.toLowerCase(Locale.ROOT), kitSec.getInt("delay-seconds", -1) < 0);
            }
        }

        return new RootEssentialsConfig(
                db.host(),
                db.port(),
                db.database(),
                db.username(),
                db.password(),
                db.tablePrefix(),
                db.jdbcParams(),
                cfg.getString("economy.currency-symbol", "G").trim(),
                cfg.getString("economy.worth-file", "").trim(),
                cfg.getString("homes.default-name", "home").trim(),
                cfg.getInt("homes.max.default", 3),
                cfg.getInt("homes.max.pro", 5),
                cfg.getInt("homes.max.lifetime", 8),
                cfg.getString("spawn.world", "").trim(),
                cfg.getStringList("server.motd"),
                cfg.getStringList("server.rules"),
                Collections.unmodifiableMap(kits),
                Collections.unmodifiableMap(oneTime),
                cfg.getString("newbie-kit", "starter").trim(),
                worth);
    }
}
