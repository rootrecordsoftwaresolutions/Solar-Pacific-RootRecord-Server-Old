package com.rootrecord.minecraft.rootreferrals;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.bstats.Metrics;
import com.rootrecord.minecraft.common.bstats.RootBStats;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.rootcore.api.RootCoreApi;
import com.rootrecord.minecraft.rootreferrals.command.RootCommand;
import com.rootrecord.minecraft.rootreferrals.hook.LinkHook;
import com.rootrecord.minecraft.rootreferrals.hook.PlaytimeHook;
import com.rootrecord.minecraft.rootreferrals.placeholder.RootReferralsExpansion;
import com.rootrecord.minecraft.rootreferrals.service.ClaimService;
import com.rootrecord.minecraft.rootreferrals.service.CodeService;
import com.rootrecord.minecraft.rootreferrals.service.QualifyService;
import com.rootrecord.minecraft.rootreferrals.service.RewardService;
import com.rootrecord.minecraft.rootreferrals.store.ReferralStore;
import com.rootrecord.minecraft.rootreferrals.task.QualifyTask;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class RootReferralsPlugin extends JavaPlugin {

    private Metrics metrics;
    private RootRecordYamlConfig yaml;
    private ReferralsConfig config;
    private ReferralStore store;
    private CodeService codeService;
    private LinkHook linkHook;
    private PlaytimeHook playtimeHook;
    private RewardService rewardService;
    private ClaimService claimService;
    private QualifyService qualifyService;
    private QualifyTask qualifyTask;

    @Override
    public void onEnable() {
        metrics = RootBStats.start(this);
        RootRecordFolders.ensureDir(this);
        yaml = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_REFERRALS_CONFIG, "root-referrals.yml");
        yaml.load();
        config = new ReferralsConfig(yaml.config());

        String prefix = tablePrefix();
        store = new ReferralStore(this, prefix);
        store.initSchema();

        codeService = new CodeService(this, store, config);
        linkHook = new LinkHook(this);
        playtimeHook = new PlaytimeHook(this, prefix);
        rewardService = new RewardService(this);
        claimService = new ClaimService(this, config, store, linkHook, codeService);
        qualifyService = new QualifyService(this, config, store, linkHook, playtimeHook, rewardService);

        RootCommand handler = new RootCommand(this);
        bind("root", handler);
        bind("rootreferrals", handler);

        qualifyTask = new QualifyTask(this, qualifyService, store);
        qualifyTask.start();

        Bukkit.getScheduler().runTask(this, this::registerPlaceholderExpansionIfPresent);
        Bukkit.getScheduler().runTaskLater(this, this::registerPlaceholderExpansionIfPresent, 40L);

        getLogger().info("Root-Referrals enabled.");
    }

    @Override
    public void onDisable() {
        RootBStats.shutdown(metrics);
        if (qualifyTask != null) {
            qualifyTask.stop();
        }
    }

    public void reloadAll() {
        if (qualifyTask != null) {
            qualifyTask.stop();
        }
        yaml.load();
        config = new ReferralsConfig(yaml.config());
        codeService = new CodeService(this, store, config);
        claimService = new ClaimService(this, config, store, linkHook, codeService);
        qualifyService = new QualifyService(this, config, store, linkHook, playtimeHook, rewardService);
        qualifyTask = new QualifyTask(this, qualifyService, store);
        qualifyTask.start();
        getLogger().info("Root-Referrals reloaded.");
    }

    public ReferralsConfig referralsConfig() {
        return config;
    }

    public ReferralStore store() {
        return store;
    }

    public CodeService codeService() {
        return codeService;
    }

    public ClaimService claimService() {
        return claimService;
    }

    public QualifyService qualifyService() {
        return qualifyService;
    }

    public LinkHook linkHook() {
        return linkHook;
    }

    public PlaytimeHook playtimeHook() {
        return playtimeHook;
    }

    public FileConfiguration configFile() {
        return yaml != null ? yaml.config() : null;
    }

    public String colorize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public String msg(String key) {
        FileConfiguration cfg = configFile();
        String prefix = cfg != null ? cfg.getString("messages.prefix", "") : "";
        String body = cfg != null ? cfg.getString("messages." + key, key) : key;
        return colorize((prefix == null ? "" : prefix) + (body == null ? key : body));
    }

    public void registerPlaceholderExpansionIfPresent() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            new RootReferralsExpansion(this).register();
        } catch (Throwable ex) {
            getLogger().warning("PlaceholderAPI expansion register failed: " + ex.getMessage());
        }
    }

    private String tablePrefix() {
        RegisteredServiceProvider<RootCoreApi> rsp =
                Bukkit.getServicesManager().getRegistration(RootCoreApi.class);
        if (rsp != null && rsp.getProvider() != null) {
            var db = rsp.getProvider().databaseSettings();
            if (db != null && db.tablePrefix() != null && !db.tablePrefix().isBlank()) {
                return db.tablePrefix();
            }
        }
        return "root_";
    }

    private void bind(String name, Object executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command missing from plugin.yml: " + name);
            return;
        }
        if (executor instanceof org.bukkit.command.CommandExecutor ce) {
            cmd.setExecutor(ce);
        }
        if (executor instanceof org.bukkit.command.TabCompleter tc) {
            cmd.setTabCompleter(tc);
        }
    }
}
