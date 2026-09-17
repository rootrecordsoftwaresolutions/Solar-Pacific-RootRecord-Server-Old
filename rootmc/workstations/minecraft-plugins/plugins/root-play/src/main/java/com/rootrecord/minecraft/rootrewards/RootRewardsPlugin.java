package com.rootrecord.minecraft.rootrewards;

import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.rootrewards.command.PlaytimeCommand;
import com.rootrecord.minecraft.rootrewards.command.ProposalCommand;
import com.rootrecord.minecraft.rootrewards.command.RootRewardsAdminCommand;
import com.rootrecord.minecraft.rootrewards.command.VoteCommand;
import com.rootrecord.minecraft.rootrewards.config.RewardsConfig;
import com.rootrecord.minecraft.rootrewards.config.RewardsMessages;
import com.rootrecord.minecraft.rootrewards.data.RewardsStore;
import com.rootrecord.minecraft.rootrewards.economy.RewardsEconomy;
import com.rootrecord.minecraft.rootrewards.listener.PlaytimeSessionListener;
import com.rootrecord.minecraft.rootrewards.listener.VotifierVoteHook;
import com.rootrecord.minecraft.rootrewards.service.PlaytimeRewardService;
import com.rootrecord.minecraft.rootrewards.service.ProposalNotifyService;
import com.rootrecord.minecraft.rootrewards.service.VotePerkService;
import com.rootrecord.minecraft.rootrewards.service.VoteReminderService;
import com.rootrecord.minecraft.rootrewards.service.VoteRewardService;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class RootRewardsPlugin {
    private final org.bukkit.plugin.java.JavaPlugin host;

    public RootRewardsPlugin(org.bukkit.plugin.java.JavaPlugin host) {
        this.host = host;
    }

    public org.bukkit.plugin.java.JavaPlugin host() { return host; }
    public org.bukkit.plugin.Plugin getPlugin() { return host; }
    public java.util.logging.Logger getLogger() { return host.getLogger(); }
    public org.bukkit.Server getServer() { return host.getServer(); }
    public java.io.File getDataFolder() { return host.getDataFolder(); }
    public org.bukkit.command.PluginCommand getCommand(String name) { return host.getCommand(name); }
    public org.bukkit.plugin.PluginDescriptionFile getDescription() { return host.getDescription(); }
    public java.io.InputStream getResource(String path) { return host.getResource(path); }
    public void saveResource(String path, boolean replace) { host.saveResource(path, replace); }
    public org.bukkit.scheduler.BukkitScheduler getScheduler() { return host.getServer().getScheduler(); }

    private RootRecordYamlConfig yamlConfig;
    private RewardsConfig rewardsConfig;
    private RewardsMessages messages;
    private RewardsStore store;
    private RewardsEconomy economy;
    private PlaytimeRewardService playtimeRewards;
    private VoteRewardService voteRewards;
    private VotePerkService votePerk;
    private VoteReminderService voteReminders;
    private ProposalNotifyService proposalNotify;
    private BukkitTask playtimeTask;
    private BukkitTask voteReminderTask;
    private BukkitTask proposalNotifyTask;
    private boolean votifierActive;

    public void enable() {
        RootRecordFolders.ensureDir(host);
        RootMcDatabaseConfig.ensureDefaults(host);
        yamlConfig = new RootRecordYamlConfig(host, RootRecordFolders.ROOT_REWARDS_CONFIG, "root-rewards.yml");
        yamlConfig.load();
        reloadLocalConfig();

        // Always bind plugin.yml commands — skipping leave executors null and Paper NPEs players.
        registerCommands();
        getServer().getPluginManager().registerEvents(new ProposalJoinListener(), host);
        startProposalNotifyTask();

        if (!economy.available()) {
            getLogger().warning(
                    "No economy (Root Essentials or Vault) — vote links still work; gold grants paused.");
            return;
        }

        getServer().getPluginManager().registerEvents(new PlaytimeSessionListener(this, store), host);
        registerVotifier();
        startPlaytimeTask();
        startVoteReminderTask();

        getLogger().info("Root-Rewards enabled — playtime milestones + vote rewards + Council poll tips.");
    }

    public void disable() {
        if (playtimeTask != null) {
            playtimeTask.cancel();
            playtimeTask = null;
        }
        if (voteReminderTask != null) {
            voteReminderTask.cancel();
            voteReminderTask = null;
        }
        if (proposalNotifyTask != null) {
            proposalNotifyTask.cancel();
            proposalNotifyTask = null;
        }
    }

    public void reloadLocalConfig() {
        if (yamlConfig != null) {
            yamlConfig.reload();
        }
        FileConfiguration cfg = yamlConfig != null ? yamlConfig.config() : null;
        rewardsConfig = RewardsConfig.from(host, cfg);
        messages = RewardsMessages.from(cfg);
        store = new RewardsStore(rewardsConfig);
        try {
            if (rewardsConfig.mysqlEnabled()) {
                store.initSchema();
            }
        } catch (Exception ex) {
            getLogger().severe("MySQL init failed: " + ex.getMessage());
        }
        economy = new RewardsEconomy(resolveRootEconomy(), resolveVault());
        playtimeRewards = new PlaytimeRewardService(this, store, economy, PlaytimeMilestones.defaults());
        voteRewards = new VoteRewardService(this, store, economy);
        votePerk = new VotePerkService(this);
        voteReminders = new VoteReminderService(this, store);
        proposalNotify = new ProposalNotifyService(this);
    }

    public void restartScheduledTasks() {
        startPlaytimeTask();
        startVoteReminderTask();
        startProposalNotifyTask();
    }

    public void restartPlaytimeTask() {
        startPlaytimeTask();
    }

    public ProposalNotifyService proposalNotify() {
        return proposalNotify;
    }

    private void registerCommands() {
        PlaytimeCommand playtimeHandler = new PlaytimeCommand(this);
        var playtime = getCommand("playtime");
        if (playtime != null) {
            playtime.setExecutor(playtimeHandler);
            playtime.setTabCompleter(playtimeHandler);
        }
        var vote = getCommand("vote");
        if (vote != null) {
            vote.setExecutor(new VoteCommand(this));
        }
        var proposal = getCommand("proposal");
        if (proposal != null) {
            proposal.setExecutor(new ProposalCommand(this));
        }
        var admin = getCommand("rootrewards");
        if (admin != null) {
            admin.setExecutor(new RootRewardsAdminCommand(this));
        }
    }

    private void registerVotifier() {
        votifierActive = getServer().getPluginManager().getPlugin("NuVotifier") != null
                || getServer().getPluginManager().getPlugin("VotifierPlus") != null
                || getServer().getPluginManager().getPlugin("Votifier") != null;
        if (votifierActive) {
            if (new VotifierVoteHook(this).register()) {
                getLogger().info("Votifier vote listener registered.");
            } else {
                getLogger().warning("Votifier plugin present but VotifierEvent class not found.");
            }
        } else {
            getLogger().warning("NuVotifier/VotifierPlus not found — vote gold will not auto-grant until installed.");
        }
    }

    private void startPlaytimeTask() {
        if (playtimeTask != null) {
            playtimeTask.cancel();
        }
        long interval = rewardsConfig.playtimeCheckIntervalSeconds() * 20L;
        playtimeTask = getServer().getScheduler().runTaskTimer(host,
                () -> playtimeRewards.checkOnlinePlayers(),
                interval,
                interval);
    }

    private void startVoteReminderTask() {
        if (voteReminderTask != null) {
            voteReminderTask.cancel();
            voteReminderTask = null;
        }
        if (!rewardsConfig.voteReminderEnabled()) {
            return;
        }
        long interval = rewardsConfig.voteReminderIntervalSeconds() * 20L;
        voteReminderTask = getServer().getScheduler().runTaskTimer(host,
                () -> voteReminders.remindOnlinePlayers(),
                interval,
                interval);
    }

    private void startProposalNotifyTask() {
        if (proposalNotifyTask != null) {
            proposalNotifyTask.cancel();
            proposalNotifyTask = null;
        }
        if (proposalNotify == null) {
            return;
        }
        // ~20 minutes
        long interval = 20L * 60L * 20L;
        proposalNotifyTask = getServer().getScheduler().runTaskTimer(host,
                () -> proposalNotify.remindOnlinePlayers(),
                20L * 90L,
                interval);
    }

    private final class ProposalJoinListener implements Listener {
        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            if (proposalNotify != null) {
                proposalNotify.notifyOnJoin(player);
            }
        }
    }

    private RootMcEconomyService resolveRootEconomy() {
        return RootMcEconomyResolver.resolve(host);
    }

    private Economy resolveVault() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        return rsp != null ? rsp.getProvider() : null;
    }

    public RewardsConfig rewardsConfig() {
        return rewardsConfig;
    }

    public RewardsStore store() {
        return store;
    }

    public PlaytimeRewardService playtimeRewards() {
        return playtimeRewards;
    }

    public VoteRewardService voteRewards() {
        return voteRewards;
    }

    public VotePerkService votePerk() {
        return votePerk;
    }

    public boolean votifierActive() {
        return votifierActive;
    }

    /** Local NuVotifier or network ingest (Towny receives; Claims mirrors). */
    public boolean listenerOnline() {
        return votifierActive || (rewardsConfig != null && rewardsConfig.voteNetworkListener());
    }

    public String colorize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public String msg(String body) {
        return colorize(messages.prefix() + body);
    }

    public String rawMsg(String key) {
        return yamlConfig.config().getString("messages." + key, "");
    }
}
