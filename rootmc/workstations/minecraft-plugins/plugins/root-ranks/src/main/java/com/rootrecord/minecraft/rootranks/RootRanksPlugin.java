package com.rootrecord.minecraft.rootranks;

import com.rootrecord.minecraft.common.FancyUiConfig;
import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.rootranks.command.RankCommand;
import com.rootrecord.minecraft.rootranks.command.RootRanksAdminCommand;
import com.rootrecord.minecraft.rootranks.config.RanksConfig;
import com.rootrecord.minecraft.rootranks.economy.RanksEconomy;
import com.rootrecord.minecraft.rootranks.listener.PlaceholderApiHookListener;
import com.rootrecord.minecraft.rootranks.service.ChatPrefixService;
import com.rootrecord.minecraft.rootranks.service.LuckPermsRankService;
import com.rootrecord.minecraft.rootranks.service.RankPurchaseService;
import net.luckperms.api.LuckPerms;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class RootRanksPlugin extends JavaPlugin {

    private RootRecordYamlConfig yaml;
    private RanksConfig ranksConfig;
    private RanksEconomy economy;
    private LuckPermsRankService luckPermsRanks;
    private ChatPrefixService chatPrefixes;
    private RankPurchaseService purchases;
    private boolean placeholderExpansionRegistered;

    @Override
    public void onEnable() {
        RootRecordFolders.ensureDir(this);
        FancyUiConfig.load(this);
        yaml = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_RANKS_CONFIG, "root-ranks.yml");
        yaml.load();

        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            getLogger().severe("LuckPerms not found — disabling Root-Ranks.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        LuckPerms luckPerms;
        try {
            luckPerms = net.luckperms.api.LuckPermsProvider.get();
        } catch (IllegalStateException ex) {
            getLogger().severe("LuckPerms API unavailable — disabling Root-Ranks.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        reloadLocalConfig(luckPerms);
        if (!economy.available()) {
            getLogger().severe("No economy (Root Essentials or Vault) — disabling Root-Ranks.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        var rankCmd = getCommand("rank");
        if (rankCmd != null) {
            RankCommand handler = new RankCommand(this);
            rankCmd.setExecutor(handler);
            rankCmd.setTabCompleter(handler);
        }
        var adminCmd = getCommand("rootranks");
        if (adminCmd != null) {
            adminCmd.setExecutor(new RootRanksAdminCommand(this));
        }

        getServer().getPluginManager().registerEvents(new PlaceholderApiHookListener(this), this);
        getServer().getScheduler().runTask(this, this::registerPlaceholderExpansionIfPresent);
        getServer().getScheduler().runTaskLater(this, this::registerPlaceholderExpansionIfPresent, 40L);
        getServer().getScheduler().runTaskLater(this, this::registerPlaceholderExpansionIfPresent, 100L);

        getLogger().info("Root-Ranks enabled — " + ranksConfig.ranks().size()
                + " purchasable tiers (player track).");
    }

    public void registerPlaceholderExpansionIfPresent() {
        if (placeholderExpansionRegistered || chatPrefixes == null) {
            return;
        }
        var papi = getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (papi == null || !papi.isEnabled()) {
            return;
        }
        try {
            Class.forName(
                    "me.clip.placeholderapi.expansion.PlaceholderExpansion",
                    false,
                    papi.getClass().getClassLoader());
            var expansion = new com.rootrecord.minecraft.rootranks.placeholder.RootRanksExpansion(this, chatPrefixes);
            if (expansion.register()) {
                placeholderExpansionRegistered = true;
                getLogger().info("PlaceholderAPI expansion registered (rootranks).");
            } else {
                getLogger().warning("PlaceholderAPI expansion register() returned false for rootranks.");
            }
        } catch (Throwable ex) {
            getLogger().warning("PlaceholderAPI expansion failed: "
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    public void reloadLocalConfig(LuckPerms luckPerms) {
        if (yaml != null) {
            yaml.reload();
        }
        ranksConfig = RanksConfig.from(yaml.config());
        economy = new RanksEconomy(resolveRootEconomy(), resolveVault());
        luckPermsRanks = new LuckPermsRankService(luckPerms);
        chatPrefixes = new ChatPrefixService(luckPerms);
        purchases = new RankPurchaseService(this, ranksConfig, economy, luckPermsRanks);
    }

    public ChatPrefixService chatPrefixes() {
        return chatPrefixes;
    }

    public void reloadLocalConfig() {
        reloadLocalConfig(net.luckperms.api.LuckPermsProvider.get());
    }

    public RanksConfig ranksConfig() {
        return ranksConfig;
    }

    public RankPurchaseService purchases() {
        return purchases;
    }

    public LuckPermsRankService luckPermsRanks() {
        return luckPermsRanks;
    }

    public String colorize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public String msg(String body) {
        return colorize(ranksConfig.prefix() + body);
    }

    public String formatGold(double gold) {
        if (gold == Math.rint(gold)) {
            return String.valueOf((long) gold);
        }
        return String.format("%,.2f", gold);
    }

    private RootMcEconomyService resolveRootEconomy() {
        return RootMcEconomyResolver.resolve(this);
    }

    private Economy resolveVault() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        return rsp != null ? rsp.getProvider() : null;
    }
}
