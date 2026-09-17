package com.rootrecord.minecraft.rootranks;

import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.common.RootMcPermsResolver;
import com.rootrecord.minecraft.common.RootMcPermsService;
import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.rootranks.command.RankCommand;
import com.rootrecord.minecraft.rootranks.command.RootRanksAdminCommand;
import com.rootrecord.minecraft.rootranks.config.RanksConfig;
import com.rootrecord.minecraft.rootranks.economy.RanksEconomy;
import com.rootrecord.minecraft.rootranks.listener.CatalogChatListener;
import com.rootrecord.minecraft.rootranks.listener.PlaceholderApiHookListener;
import com.rootrecord.minecraft.rootranks.service.ChatPrefixService;
import com.rootrecord.minecraft.rootranks.service.RankPurchaseService;
import com.rootrecord.minecraft.rootranks.service.RootPermsRankService;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class RootRanksPlugin {
    private final org.bukkit.plugin.java.JavaPlugin host;

    public RootRanksPlugin(org.bukkit.plugin.java.JavaPlugin host) {
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

    private RootRecordYamlConfig yaml;
    private RanksConfig ranksConfig;
    private RanksEconomy economy;
    private RootPermsRankService rankService;
    private ChatPrefixService chatPrefixes;
    private RankPurchaseService purchases;
    private boolean placeholderExpansionRegistered;
    private boolean ranksActive;

    public void enable() {
        RootRecordFolders.ensureDir(host);
        com.rootrecord.minecraft.common.FancyUiConfig.load(host);
        yaml = new RootRecordYamlConfig(host, RootRecordFolders.ROOT_RANKS_CONFIG, "root-ranks.yml");
        yaml.load();
        ranksConfig = RanksConfig.from(yaml.config());

        // Always bind /rank — null executor → Paper "unexpected error" for players.
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

        RootMcPermsService perms = RootMcPermsResolver.resolve(host);
        if (perms == null) {
            getLogger().warning("Root-Perms not found — Root-Ranks inactive (Play continues).");
            ranksActive = false;
            registerCatalogChat();
            return;
        }

        reloadLocalConfig(perms);
        registerCatalogChat();
        if (!economy.available()) {
            getLogger().warning("No economy — Root-Ranks inactive (Play continues).");
            ranksActive = false;
            return;
        }

        getServer().getPluginManager().registerEvents(new PlaceholderApiHookListener(this), host);
        getServer().getScheduler().runTask(host, this::registerPlaceholderExpansionIfPresent);
        getServer().getScheduler().runTaskLater(host, this::registerPlaceholderExpansionIfPresent, 40L);
        getServer().getScheduler().runTaskLater(host, this::registerPlaceholderExpansionIfPresent, 100L);

        ranksActive = true;
        getLogger().info("Root-Ranks enabled — " + ranksConfig.ranks().size()
                + " purchasable tiers (Root-Perms track).");
    }

    private void registerCatalogChat() {
        boolean catalog = yaml == null || yaml.config().getBoolean("chat.catalog-without-townychat", true);
        String prefix = yaml == null
                ? "&6◆&r {joint}{rank}&f{name}&8 »&f "
                : yaml.config().getString("chat.prefix", "&6◆&r {joint}{rank}&f{name}&8 »&f ");
        getServer().getPluginManager().registerEvents(new CatalogChatListener(this, catalog, prefix), host);
        if (catalog && getServer().getPluginManager().getPlugin("TownyChat") == null) {
            getLogger().info("Catalog chat enabled (no TownyChat) — Claims-style ◆ Rank Name » msg.");
        }
    }

    public boolean ranksActive() {
        return ranksActive;
    }

    public void registerPlaceholderExpansionIfPresent() {
        if (!ranksActive || placeholderExpansionRegistered || chatPrefixes == null) {
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
            }
        } catch (Throwable ex) {
            getLogger().warning("PlaceholderAPI expansion failed: "
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    public void reloadLocalConfig(RootMcPermsService perms) {
        if (yaml != null) {
            yaml.reload();
        }
        ranksConfig = RanksConfig.from(yaml.config());
        economy = new RanksEconomy(resolveRootEconomy(), resolveVault());
        rankService = new RootPermsRankService(perms);
        chatPrefixes = new ChatPrefixService(perms);
        purchases = new RankPurchaseService(this, ranksConfig, economy, rankService);
    }

    public ChatPrefixService chatPrefixes() {
        return chatPrefixes;
    }

    public void reloadLocalConfig() {
        RootMcPermsService perms = RootMcPermsResolver.resolve(host);
        if (perms == null) {
            getLogger().warning("Root-Perms missing — cannot reload ranks.");
            return;
        }
        reloadLocalConfig(perms);
    }

    public RanksConfig ranksConfig() {
        return ranksConfig;
    }

    public RankPurchaseService purchases() {
        return purchases;
    }

    public RootPermsRankService rankService() {
        return rankService;
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

    public void disable() {
        ranksActive = false;
    }
}
