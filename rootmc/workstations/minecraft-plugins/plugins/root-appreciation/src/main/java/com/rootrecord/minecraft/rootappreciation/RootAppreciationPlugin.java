package com.rootrecord.minecraft.rootappreciation;

import com.rootrecord.minecraft.rootappreciation.voteshard.VoteShardItem;
import com.rootrecord.minecraft.rootappreciation.voteshard.VoteShardService;
import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class RootAppreciationPlugin extends JavaPlugin {

    private RootRecordYamlConfig yaml;
    private AppreciationConfig config;
    private AppreciationStore store;
    private AppreciationTokenItem tokens;
    private AppreciationService service;
    private AppreciationRewardPool rewardPool;
    private AppreciationRewardGranter rewardGranter;
    private VoteShardItem voteShards;
    private VoteShardService voteShardService;

    @Override
    public void onEnable() {
        RootRecordFolders.ensureDir(this);
        yaml = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_APPRECIATION_CONFIG, "root-appreciation.yml");
        yaml.load();
        config = new AppreciationConfig(yaml.config());
        tokens = new AppreciationTokenItem(this);
        rewardPool = new AppreciationRewardPool(this);
        rewardGranter = new AppreciationRewardGranter(this);
        voteShards = new VoteShardItem(this);

        String prefix = "root_";
        RootMcDatabaseConfig.DatabaseSettings db = RootMcDatabaseConfig.resolve(this, yaml.config());
        if (db != null && db.tablePrefix() != null && !db.tablePrefix().isBlank()) {
            prefix = db.tablePrefix();
        }
        store = new AppreciationStore(this, prefix);
        store.initSchema();
        service = new AppreciationService(this);
        voteShardService = new VoteShardService(this, voteShards, prefix);
        voteShardService.initSchema();

        ThanksCommand thanks = new ThanksCommand(this);
        bind("thanks", thanks, thanks);
        bind("bonus", new BonusCommand(this), null);
        bind("voteshard", new VoteShardCommand(this), null);
        getServer().getPluginManager().registerEvents(new AppreciationJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new AppreciationTokenListener(this), this);
        getServer().getScheduler().runTaskLater(this, () -> {
            if (voteShardService != null) {
                voteShardService.matchAvaToHumans();
            }
        }, 20L * 15L);
        // Push EC Vote Shard weights for Council math (~every 5 min). Ava 25% is synthetic.
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (voteShardService != null) {
                voteShardService.matchAvaToHumans();
            }
        }, 20L * 60L, 20L * 60L * 5L);

        getLogger().info("Root-Appreciation enabled — Appreciation Tokens + Vote Shards (/voteshard).");
    }

    public void reloadAll() {
        yaml.load();
        config = new AppreciationConfig(yaml.config());
        rewardPool = new AppreciationRewardPool(this);
        getLogger().info("Root-Appreciation reloaded.");
    }

    private void bind(String name, org.bukkit.command.CommandExecutor exec, org.bukkit.command.TabCompleter tab) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command missing from plugin.yml: " + name);
            return;
        }
        cmd.setExecutor(exec);
        if (tab != null) {
            cmd.setTabCompleter(tab);
        }
    }

    public RootRecordYamlConfig yaml() {
        return yaml;
    }

    public AppreciationConfig config() {
        return config;
    }

    public AppreciationStore store() {
        return store;
    }

    public AppreciationTokenItem tokens() {
        return tokens;
    }

    public AppreciationService service() {
        return service;
    }

    public AppreciationRewardPool rewardPool() {
        return rewardPool;
    }

    public AppreciationRewardGranter rewardGranter() {
        return rewardGranter;
    }

    public VoteShardItem voteShards() {
        return voteShards;
    }

    public VoteShardService voteShardService() {
        return voteShardService;
    }

    public String msg(String key) {
        String prefix = yaml.config().getString("messages.prefix", "");
        String body = yaml.config().getString("messages." + key, key);
        return colorize(prefix + body);
    }

    public String colorize(String input) {
        return input == null ? "" : input.replace('&', '\u00A7');
    }
}
