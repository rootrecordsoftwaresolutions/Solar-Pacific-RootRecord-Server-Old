package com.rootrecord.minecraft.rootessentials.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import com.rootrecord.minecraft.rootessentials.util.WorthLoader;

import java.util.Collections;
import java.util.Map;

public record RootEconomyConfig(
        String mysqlHost,
        int mysqlPort,
        String mysqlDatabase,
        String mysqlUsername,
        String mysqlPassword,
        String mysqlTablePrefix,
        String mysqlJdbcParams,
        double startingBalance,
        /** Baked into /reserve Balance (Towny MAP_262 default; Claims uses 0). */
        double trueReserveOpening,
        String currencySymbol,
        String worthFile,
        Map<Material, Double> worthByMaterial) {

    public static RootEconomyConfig from(JavaPlugin plugin, FileConfiguration cfg) {
        RootMcDatabaseConfig.DatabaseSettings db = RootMcDatabaseConfig.resolve(plugin, cfg);
        Map<Material, Double> worth = WorthLoader.load(
                cfg.getConfigurationSection("economy.worth"),
                cfg.getString("economy.worth-file", "").trim());

        return new RootEconomyConfig(
                db.host(),
                db.port(),
                db.database(),
                db.username(),
                db.password(),
                db.tablePrefix(),
                db.jdbcParams(),
                cfg.getDouble("economy.starting-balance", 0),
                cfg.getDouble(
                        "economy.true-reserve-opening",
                        com.rootrecord.minecraft.rootessentials.treasury.EconomyBaseline
                                .MAP_262_TRUE_RESERVE_OPENING),
                cfg.getString("economy.currency-symbol", "G").trim(),
                cfg.getString("economy.worth-file", "").trim(),
                worth);
    }
}
