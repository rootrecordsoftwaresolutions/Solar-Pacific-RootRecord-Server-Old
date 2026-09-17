package com.rootrecord.minecraft.rootiteminfo;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.rootiteminfo.census.ItemCensusScanner;
import com.rootrecord.minecraft.rootiteminfo.census.ItemCensusStore;
import com.rootrecord.minecraft.rootiteminfo.command.InfoCommand;
import com.rootrecord.minecraft.rootiteminfo.listener.ItemCensusListener;
import com.rootrecord.minecraft.rootiteminfo.service.ItemValueLookup;
import com.rootrecord.minecraft.rootiteminfo.sync.ItemCensusMysqlSync;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import com.rootrecord.minecraft.common.bstats.Metrics;
import com.rootrecord.minecraft.common.bstats.RootBStats;

public final class RootItemInfoPlugin extends JavaPlugin {

    private Metrics metrics;

    private RootRecordYamlConfig yamlConfig;
    private ItemCensusStore store;
    private ItemCensusScanner scanner;
    private ItemValueLookup values;
    private ItemCensusMysqlSync mysqlSync;
    private BukkitTask scanTask;
    private boolean enabledFlag = true;

    @Override
    public void onEnable() {
        metrics = RootBStats.start(this);
        RootRecordFolders.ensureDir(this);
        yamlConfig = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_ITEMINFO_CONFIG, "root-iteminfo.yml");
        yamlConfig.load();

        File censusFile = RootRecordFolders.configFile(this, RootRecordFolders.ROOT_ITEMINFO_CENSUS);
        migrateLegacyCensus(censusFile);
        store = new ItemCensusStore(censusFile.toPath());
        store.setLogger(getLogger());
        store.load();
        values = new ItemValueLookup(this);
        scanner = new ItemCensusScanner(this, store);
        mysqlSync = new ItemCensusMysqlSync(this, store);

        reloadLocalConfig();
        scheduleTasks();

        getServer().getPluginManager().registerEvents(new ItemCensusListener(this, scanner), this);

        PluginCommand info = getCommand("info");
        if (info != null) {
            InfoCommand handler = new InfoCommand(this);
            info.setExecutor(handler);
            info.setTabCompleter(handler);
        }

        FileConfiguration bootCfg = yamlConfig != null ? yamlConfig.config() : null;
        boolean autoScan = bootCfg == null || bootCfg.getBoolean("scan.enabled", true);
        if (autoScan) {
            getServer().getScheduler().runTaskLater(this, () -> scanner.requestFullScan("startup"), 40L);
            getLogger().info("Root-ItemInfo enabled — /info + world item census.");
        } else {
            getLogger().info("Root-ItemInfo enabled — /info (auto census off).");
        }
    }

    @Override
    public void onDisable() {
        RootBStats.shutdown(metrics);
        cancelTasks();
        if (store != null) {
            store.save();
        }
    }

    public void reloadLocalConfig() {
        if (yamlConfig != null) {
            yamlConfig.reload();
        }
        FileConfiguration cfg = yamlConfig != null ? yamlConfig.config() : null;
        enabledFlag = cfg == null || cfg.getBoolean("enabled", true);
        cancelTasks();
        scheduleTasks();
    }

    private void scheduleTasks() {
        FileConfiguration cfg = yamlConfig != null ? yamlConfig.config() : null;
        if (cfg != null && !cfg.getBoolean("scan.enabled", true)) {
            return;
        }
        long scanSec = Math.max(30, cfg != null ? cfg.getLong("scan.interval-seconds", 120) : 120);
        scanTask = getServer().getScheduler().runTaskTimer(this, () -> {
            if (enabledFlag) {
                scanner.requestFullScan("interval");
            }
        }, scanSec * 20L, scanSec * 20L);

    }

    private void cancelTasks() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
    }

    public boolean featureEnabled() {
        return enabledFlag;
    }

    public ItemCensusStore store() {
        return store;
    }

    public ItemCensusScanner scanner() {
        return scanner;
    }

    public ItemValueLookup values() {
        return values;
    }

    public ItemCensusMysqlSync mysqlSync() {
        return mysqlSync;
    }

    public FileConfiguration configFile() {
        return yamlConfig != null ? yamlConfig.config() : null;
    }

    public String msg(String key) {
        FileConfiguration cfg = configFile();
        String raw = cfg != null ? cfg.getString("messages." + key, key) : key;
        String prefix = cfg != null ? cfg.getString("messages.prefix", "") : "";
        return colorize(prefix + raw);
    }

    public String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    public static String materialKey(Material material) {
        return material == null ? "air" : material.getKey().getKey().toLowerCase(Locale.ROOT);
    }

    public Map<String, Long> snapshotCounts() {
        return store.snapshot();
    }

    /** Move {@code plugins/Root-ItemInfo/item-census.yml} → {@code plugins/RootMC/item-census.yml}. */
    private void migrateLegacyCensus(File target) {
        if (target.exists()) {
            deleteEmptyLegacyFolder();
            return;
        }
        File legacy = new File(getDataFolder(), "item-census.yml");
        if (!legacy.isFile()) {
            return;
        }
        try {
            Files.createDirectories(target.toPath().getParent());
            Files.move(legacy.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            getLogger().info("Migrated item census to plugins/RootMC/" + RootRecordFolders.ROOT_ITEMINFO_CENSUS);
            deleteEmptyLegacyFolder();
        } catch (IOException ex) {
            getLogger().warning("Could not migrate legacy item-census.yml: " + ex.getMessage());
        }
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
}
