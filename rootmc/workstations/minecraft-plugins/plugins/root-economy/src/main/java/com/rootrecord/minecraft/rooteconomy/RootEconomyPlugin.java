package com.rootrecord.minecraft.rooteconomy;

import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.common.RootMcEconomyBridge;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.common.RootMcIncomeSweepResult;
import com.rootrecord.minecraft.common.RootMcLoanResolver;
import com.rootrecord.minecraft.common.RootMcLoanService;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.common.ShadedServiceBridge;
import com.rootrecord.minecraft.common.TreasuryReserveIncomeDispatcher;
import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootMcDatabaseConfig;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.rootessentials.command.*;
import com.rootrecord.minecraft.rootessentials.config.RootEconomyConfig;
import com.rootrecord.minecraft.rootessentials.data.*;
import com.rootrecord.minecraft.rootessentials.towny.TownyEconomyHeartbeatBridge;
import com.rootrecord.minecraft.rootessentials.towny.TownyLangOverrideBridge;
import com.rootrecord.minecraft.rootessentials.towny.TownyMcDayBridge;
import com.rootrecord.minecraft.rootessentials.towny.TownyPlotRefundBridge;
import com.rootrecord.minecraft.rootessentials.towny.TownyEconomyAccounts;
import com.rootrecord.minecraft.rootessentials.towny.TownyReflection;
import com.rootrecord.minecraft.rootessentials.towny.TownyTreasuryBridge;
import com.rootrecord.minecraft.rootessentials.listener.DigitalGoldMineListener;
import com.rootrecord.minecraft.rootessentials.listener.GoldFoundListener;
import com.rootrecord.minecraft.rootessentials.listener.SolarMiningDropListener;
import com.rootrecord.minecraft.rootessentials.listener.SolarHostStatusListener;
import com.rootrecord.minecraft.rootessentials.service.GoldProspectService;
import com.rootrecord.minecraft.rootessentials.service.SolarMiningMultiplierService;
import com.rootrecord.minecraft.rootessentials.treasury.ListTotalsRefreshService;
import com.rootrecord.minecraft.rootessentials.treasury.ReserveStatsService;
import com.rootrecord.minecraft.rootessentials.treasury.TreasuryManager;
import com.rootrecord.minecraft.rootessentials.service.EconomyHeartbeatService;
import com.rootrecord.minecraft.rootessentials.service.EconomyPlayerState;
import com.rootrecord.minecraft.rootessentials.util.GoldItemValue;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.rootrecord.minecraft.common.bstats.Metrics;
import com.rootrecord.minecraft.common.bstats.RootBStats;

/**
 * Root-Economy — wallets/Notes, Server Reserve treasury, Vault bridge, and the absorbed
 * bonds/loans/shops/upkeep economy modules. QoL (homes/spawn/banner/potions/etc.) stays in
 * Root-Essentials.
 */
public final class RootEconomyPlugin extends JavaPlugin implements RootMcEconomyService {

    private Metrics metrics;

    private RootRecordYamlConfig yaml;
    private RootEconomyConfig config;
    private EconomyStore economy;
    private GoldFoundStore goldFound;
    private GoldItemEventStore goldItemEvents;
    private GoldMaterialObtainedStore goldMaterials;
    private TreasuryLedgerStore treasuryLedger;
    private TreasuryManager treasury;
    private MonthlyPlaytimeStore monthlyPlaytime;
    private ReserveStatsService reserveStats;
    private ListTotalsStore listTotalsStore;
    private ListTotalsRefreshService listTotalsRefresh;
    private PlayerPrefsStore playerPrefs;
    private final EconomyPlayerState playerState = new EconomyPlayerState();
    private EconomyHeartbeatService economyHeartbeat;
    private final DecimalFormat moneyFmt = new DecimalFormat("0.000");
    private com.rootrecord.minecraft.rootessentials.economy.RootEssentialsVaultEconomy vaultEconomy;

    private com.rootrecord.minecraft.rootbonds.RootBondsPlugin bondsFeature;
    private com.rootrecord.minecraft.rootloans.RootLoansPlugin loansFeature;
    private com.rootrecord.minecraft.rootupkeep.RootUpkeepPlugin upkeepFeature;
    private com.rootrecord.minecraft.rootessentials.ender.DoubleEnderChestService doubleEnderChest;
    private SolarMiningMultiplierService solarMining;
    private GoldProspectService goldProspect;
    private MySqlSupport mysql;

    @Override
    public void onEnable() {
        metrics = RootBStats.start(this);
        getLogger().info("Enabling Root-Economy v" + getDescription().getVersion());
        RootMcDatabaseConfig.ensureDefaults(this);
        yaml = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_ECONOMY_CONFIG, RootRecordFolders.ROOT_ECONOMY_CONFIG);
        yaml.load();
        reloadLocalConfig();
        try {
            MySqlSupport sql = new MySqlSupport(config);
            mysql = sql;
            economy = new EconomyStore(sql, config.mysqlTablePrefix(), config.startingBalance());
            goldFound = new GoldFoundStore(sql, config.mysqlTablePrefix());
            goldItemEvents = new GoldItemEventStore(sql, config.mysqlTablePrefix());
            goldMaterials = new GoldMaterialObtainedStore(sql, config.mysqlTablePrefix());
            playerPrefs = new PlayerPrefsStore(sql, config.mysqlTablePrefix());
            economy.initSchema();
            playerPrefs.initSchema();
            treasuryLedger = new TreasuryLedgerStore(sql, config.mysqlTablePrefix());
            treasuryLedger.initSchema();
            listTotalsStore = new ListTotalsStore(sql, config.mysqlTablePrefix());
            listTotalsStore.initSchema();
            listTotalsRefresh = new ListTotalsRefreshService(
                    this, listTotalsStore, sql, config.mysqlTablePrefix());
            monthlyPlaytime = new MonthlyPlaytimeStore(sql, config.mysqlTablePrefix());
            treasury = new TreasuryManager(
                    this,
                    economy,
                    treasuryLedger,
                    yaml.config().getBoolean("treasury.transaction-tax-enabled", true),
                    yaml.config().getDouble("treasury.transaction-tax-rate", 0.001),
                    yaml.config().getBoolean("treasury.dynamic-tax-enabled", true),
                    yaml.config().getDouble("treasury.dynamic-tax-shortfall-factor", 0.1),
                    yaml.config().getDouble("treasury.dynamic-tax-max-rate", 0.0),
                    yaml.config().getBoolean("treasury.dynamic-tax-retire-over-issue", true));
            reserveStats = new ReserveStatsService(
                    treasuryLedger,
                    monthlyPlaytime,
                    treasury,
                    economy,
                    yaml.config().getDouble("treasury.dividend-payout-ratio", 0.50));
        } catch (Exception ex) {
            getLogger().severe("MySQL init failed for "
                    + config.mysqlUsername() + "@" + config.mysqlHost() + ":" + config.mysqlPort()
                    + "/" + config.mysqlDatabase()
                    + " (passwordSet=" + (config.mysqlPassword() != null && !config.mysqlPassword().isBlank())
                    + "): " + ex.getMessage());
            getLogger().severe("Fix plugins/RootMC/database.yml (do not upload handoff stubs with empty password).");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            goldFound.initSchema();
            goldItemEvents.initSchema();
            goldMaterials.initSchema();
        } catch (Exception ex) {
            getLogger().severe("Gold tracking MySQL schema failed (economy still enabled): "
                    + ex.getMessage());
            goldFound = null;
            goldItemEvents = null;
            goldMaterials = null;
        }

        registerCommands();
        solarMining = new SolarMiningMultiplierService(this);
        if (treasury != null) {
            treasury.setPowerTaxRateSource(() -> solarMining.taxRate());
        }
        solarMining.start();
        goldProspect = new GoldProspectService(this);
        goldProspect.start();
        getServer().getPluginManager().registerEvents(goldProspect, this);
        getServer().getPluginManager().registerEvents(new DigitalGoldMineListener(this), this);
        getServer().getPluginManager().registerEvents(new SolarMiningDropListener(this), this);
        getServer().getPluginManager().registerEvents(new SolarHostStatusListener(this), this);
        if (goldFound != null && goldItemEvents != null && goldMaterials != null) {
            getServer().getPluginManager().registerEvents(new GoldFoundListener(this), this);
        } else {
            getLogger().warning("GoldFoundListener not registered — fix gold schema tables, then reboot.");
        }
        getServer().getServicesManager().register(RootMcEconomyService.class, this, this, ServicePriority.Normal);
        if (treasury != null) {
            getServer().getServicesManager().register(RootMcTreasuryService.class, treasury, this, ServicePriority.Normal);
            getLogger().info("Registered RootMcTreasuryService (Activity Dividend treasury).");
        }
        enableDoubleEnderChest();
        wireReserveIncomeBridge();
        registerVaultEconomy();
        getServer().getScheduler().runTask(this, () -> {
            registerEconomyHeartbeat();
            registerTownyHooks();
        });
        // Precompute /list totals every 5 minutes (same cadence as economy sync skip window).
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (listTotalsRefresh == null) {
                return;
            }
            try {
                listTotalsRefresh.refreshIfStale();
            } catch (Exception ex) {
                getLogger().warning("List totals refresh failed: " + ex.getMessage());
            }
        }, 20L * 60, 20L * 60 * 5);
        enableAbsorbedFeatures();
        getLogger().info("Root-Economy enabled (wallets, treasury, bonds/loans/upkeep).");
    }

    private void enableAbsorbedFeatures() {
        // Order: economy/Vault base already up → Bonds → Loans → Upkeep
        // Chest shops + market browse ship as Root-ChestShops / Root-Market jars.
        bondsFeature = new com.rootrecord.minecraft.rootbonds.RootBondsPlugin(this);
        bondsFeature.enable();
        loansFeature = new com.rootrecord.minecraft.rootloans.RootLoansPlugin(this);
        loansFeature.enable();
        upkeepFeature = new com.rootrecord.minecraft.rootupkeep.RootUpkeepPlugin(this);
        upkeepFeature.enable();
    }

    public com.rootrecord.minecraft.rootbonds.RootBondsPlugin bondsFeature() {
        return bondsFeature;
    }

    public com.rootrecord.minecraft.rootupkeep.RootUpkeepPlugin upkeepFeature() {
        return upkeepFeature;
    }

    public com.rootrecord.minecraft.rootloans.RootLoansPlugin loansFeature() {
        return loansFeature;
    }

    /** SPI bridge for ShadedServiceBridge.resolveLoans when Root-Loans jar is retired. */
    public Object loans() {
        return loansFeature != null ? loansFeature.loans() : null;
    }

    private void enableDoubleEnderChest() {
        if (mysql == null) {
            return;
        }
        String serverId = "local";
        try {
            var rm = getServer().getPluginManager().getPlugin("RootMC");
            if (rm != null && rm.isEnabled()) {
                Object bridge = rm.getClass().getMethod("rootStat").invoke(rm);
                if (bridge != null) {
                    Object cfg = bridge.getClass().getMethod("config").invoke(bridge);
                    if (cfg != null) {
                        Object id = cfg.getClass().getMethod("serverId").invoke(cfg);
                        if (id != null && !String.valueOf(id).isBlank()) {
                            serverId = String.valueOf(id).trim();
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // fall back to local
        }
        doubleEnderChest = new com.rootrecord.minecraft.rootessentials.ender.DoubleEnderChestService(
                this, mysql, config.mysqlTablePrefix(), serverId);
        getServer().getPluginManager().registerEvents(doubleEnderChest, this);
        getServer().getServicesManager().register(
                com.rootrecord.minecraft.common.RootMcEnderChestService.class,
                doubleEnderChest,
                this,
                ServicePriority.Normal);
        getLogger().info("Double ender chest (54 slots) ready — server_id=" + serverId);
    }

    public com.rootrecord.minecraft.rootessentials.ender.DoubleEnderChestService doubleEnderChest() {
        return doubleEnderChest;
    }

    public Object bondTransfer() {
        return bondsFeature != null ? bondsFeature.bondTransfer() : null;
    }

    public Object bondIncome() {
        return bondsFeature != null ? bondsFeature.bondIncome() : null;
    }

    private void registerEconomyHeartbeat() {
        var section = yaml.config().getConfigurationSection("economy-heartbeat");
        if (section != null && !section.getBoolean("enabled", true)) {
            return;
        }
        economyHeartbeat = new EconomyHeartbeatService(this);
        getLogger().info("Economy heartbeat wired to Minecraft day rollover pipeline.");
        if (TownyReflection.townyPlugin() != null) {
            TownyLangOverrideBridge.register(this);
            if (section == null || section.getBoolean("towny-new-day-fallback", true)) {
                TownyEconomyHeartbeatBridge.register(this, economyHeartbeat);
            }
        }
    }

    private void registerTownyHooks() {
        if (TownyReflection.townyPlugin() == null) {
            return;
        }
        TownyTreasuryBridge.register(this);
        if (yaml.config().getBoolean("towny.plot-unclaim-full-refund", true)) {
            TownyPlotRefundBridge.register(this);
        }
        // MC-day pipeline owns Towny taxes/upkeep — stop Towny's separate day_interval clock.
        getServer().getScheduler().runTaskLater(this, () -> TownyMcDayBridge.disableTownyDailyTimer(this), 40L);
        getServer().getScheduler().runTaskLaterAsynchronously(this, () -> {
            try {
                int fixed = economy.reconcileMislabeledHolderAccounts();
                if (fixed > 0) {
                    getLogger().info("Reconciled " + fixed + " town/nation economy row(s) with placeholder usernames.");
                }
            } catch (Exception ex) {
                getLogger().warning("Economy holder reconciliation failed: " + ex.getMessage());
            }
        }, 100L);
    }

    private void registerVaultEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault not found — Towny economy bridge not registered (install Vault.jar).");
            return;
        }
        vaultEconomy = new com.rootrecord.minecraft.rootessentials.economy.RootEssentialsVaultEconomy(this);
        getServer().getServicesManager().register(
                net.milkbowl.vault.economy.Economy.class, vaultEconomy, this,
                ServicePriority.High);
        getLogger().info("Registered RootMC economy with Vault (Towny-compatible).");
    }

    private void registerCommands() {
        bind("balance", new BalanceCommand(this));
        bind("bal", new BalanceCommand(this));
        var pay = new PayCommand(this);
        bind("pay", pay);
        var payCmd = getCommand("pay");
        if (payCmd != null) {
            payCmd.setTabCompleter(pay);
        }
        bind("paytoggle", new EconomyCommands.Paytoggle(this));
        var baltop = new EconomyCommands.Baltop(this);
        bind("baltop", baltop);
        bind("balancetop", baltop);
        var baltopCmd = getCommand("baltop");
        if (baltopCmd != null) {
            baltopCmd.setTabCompleter(baltop);
        }
        var balanceTopCmd = getCommand("balancetop");
        if (balanceTopCmd != null) {
            balanceTopCmd.setTabCompleter(baltop);
        }
        bind("reserve", new ReserveCommand(this));
        bind("totals", new TotalsCommand(this));
        bind("economy", new EconomyCommand(this));
        bind("tax", new TaxCommand(this));
        bind("gold", new GoldCommand(this));
        bind("solar", new SolarCommand(this));
        var radar = new RadarCommand(this);
        bind("radar", radar);
        var radarCmd = getCommand("radar");
        if (radarCmd != null) {
            radarCmd.setTabCompleter(radar);
        }
        var mint = new MintCommand(this);
        bind("mint", mint);
        var mintCmd = getCommand("mint");
        if (mintCmd != null) {
            mintCmd.setTabCompleter(mint);
        }
        bind("grant", new GrantCommand(this));
    }

    private void bind(String name, org.bukkit.command.CommandExecutor executor) {
        var cmd = getCommand(name);
        if (cmd != null) cmd.setExecutor(executor);
    }

    @Override
    public void onDisable() {
        if (goldProspect != null) {
            goldProspect.stop();
            goldProspect = null;
        }
        if (solarMining != null) {
            solarMining.stop();
            solarMining = null;
        }
        RootBStats.shutdown(metrics);
        if (upkeepFeature != null) {
            upkeepFeature.disable();
            upkeepFeature = null;
        }
        if (loansFeature != null) {
            loansFeature.disable();
            loansFeature = null;
        }
        if (bondsFeature != null) {
            bondsFeature.disable();
            bondsFeature = null;
        }
        TreasuryReserveIncomeDispatcher.clearHandler();
        if (vaultEconomy != null) {
            getServer().getServicesManager().unregister(net.milkbowl.vault.economy.Economy.class, vaultEconomy);
            vaultEconomy = null;
        }
        getServer().getServicesManager().unregister(RootMcEconomyService.class, this);
        if (treasury != null) {
            getServer().getServicesManager().unregister(RootMcTreasuryService.class, treasury);
        }
    }

    /** Called by RootMC at the start of each Minecraft day rollover (Towny taxes/upkeep). */
    public void processTownyMcDay(Runnable onComplete) {
        TownyMcDayBridge.runNewDay(this, onComplete);
    }

    /** Forwarded for RootMC MC-day pipeline (bonds live inside Root-Economy). */
    public void processMcDayRollover(long firstCompletedDay, long currentMcDayId, Runnable onComplete) {
        if (bondsFeature == null) {
            onComplete.run();
            return;
        }
        bondsFeature.processMcDayRollover(firstCompletedDay, currentMcDayId, onComplete);
    }

    /** Town bank tax: {@code rate-percent} of balance per completed MC day → Server Reserve. */
    public void processMcDayTownTax(long firstCompletedDay, long currentMcDayId, Runnable onComplete) {
        if (upkeepFeature == null) {
            onComplete.run();
            return;
        }
        upkeepFeature.processMcDayTownTax(firstCompletedDay, currentMcDayId, onComplete);
    }

    /** Forwarded for RootMC MC-day pipeline (inactivity tax lives inside Root-Economy). */
    public void processMcDayInactivityTax(long firstCompletedDay, long currentMcDayId, Runnable onComplete) {
        if (upkeepFeature == null) {
            onComplete.run();
            return;
        }
        upkeepFeature.processMcDayInactivityTax(firstCompletedDay, currentMcDayId, onComplete);
    }

    /** Called by RootMC after daily settlements and scheduled debits complete. */
    public void processMcDayEconomyHeartbeat(Runnable onComplete) {
        if (economyHeartbeat != null) {
            economyHeartbeat.onNewDay(Bukkit.getOnlinePlayers());
        }
        onComplete.run();
    }

    public EconomyHeartbeatService economyHeartbeat() {
        return economyHeartbeat;
    }

    public TreasuryManager treasury() {
        return treasury;
    }

    public SolarMiningMultiplierService solarMining() {
        return solarMining;
    }

    public GoldProspectService goldProspect() {
        return goldProspect;
    }

    public boolean oreRadarActive(java.util.UUID uuid) {
        return goldProspect != null && goldProspect.isActive(uuid);
    }

    public EconomyStore economy() {
        return economy;
    }

    public TreasuryLedgerStore treasuryLedger() {
        return treasuryLedger;
    }

    public ListTotalsStore listTotalsStore() {
        return listTotalsStore;
    }

    public ListTotalsRefreshService listTotalsRefresh() {
        return listTotalsRefresh;
    }

    private void wireReserveIncomeBridge() {
        TreasuryReserveIncomeDispatcher.setLogger(getLogger());
        TreasuryReserveIncomeDispatcher.setHandler((amount, type, sourceUuid, details) -> {
            var bonds = ShadedServiceBridge.resolveBondIncome(this);
            if (bonds != null) {
                bonds.recordTreasuryIncome(amount, type.name(), sourceUuid, details == null ? "" : details);
            }
        });
    }

    public void reloadLocalConfig() {
        yaml.reload();
        config = RootEconomyConfig.from(this, yaml.config());
        com.rootrecord.minecraft.rootessentials.treasury.EconomyBaseline.setOpeningCarryoverG(
                config.trueReserveOpening());
        if (treasury != null) {
            treasury.reload(
                    yaml.config().getBoolean("treasury.transaction-tax-enabled", true),
                    yaml.config().getDouble("treasury.transaction-tax-rate", 0.001),
                    yaml.config().getBoolean("treasury.dynamic-tax-enabled", true),
                    yaml.config().getDouble("treasury.dynamic-tax-shortfall-factor", 0.1),
                    yaml.config().getDouble("treasury.dynamic-tax-max-rate", 0.0),
                    yaml.config().getBoolean("treasury.dynamic-tax-retire-over-issue", true));
        }
        if (solarMining != null) {
            solarMining.reloadFromConfig();
        }
    }

    public EconomyPlayerState playerState() { return playerState; }

    /** Live economy YAML ({@code root-economy.yml} via RootRecord folders). */
    public org.bukkit.configuration.file.FileConfiguration economyYaml() {
        return yaml != null ? yaml.config() : getConfig();
    }

    public boolean acceptsPay(UUID uuid) throws Exception {
        return playerPrefs.acceptsPay(uuid);
    }

    public boolean toggleAcceptsPay(UUID uuid) throws Exception {
        return playerPrefs.togglePay(uuid);
    }

    public void ensureBalanceRow(Player player) throws Exception {
        economy.balance(player.getUniqueId(), player.getName());
    }

    public String msg(String key) {
        String p = yaml.config().getString("messages.prefix", "");
        String body = yaml.config().getString("messages." + key, key);
        return colorize(p + body);
    }

    public String rawMsg(String key) {
        return yaml.config().getString("messages." + key, key);
    }

    public String colorize(String input) {
        return input == null ? "" : input.replace('&', '\u00A7');
    }

    public String money(double value) {
        synchronized (moneyFmt) { return moneyFmt.format(value); }
    }

    public String currency() { return config.currencySymbol(); }
    public Double worth(Material material) { return config.worthByMaterial().get(material); }

    public double serviceFee(String key, double defaultAmount) {
        return Math.max(0, yaml.config().getDouble("fees." + key, defaultAmount));
    }

    public void sinkServiceFee(java.util.UUID playerUuid, String playerName, double amount, String channel) {
        if (amount <= 0 || treasury == null) {
            return;
        }
        treasury.settleClosedLoopPayment(
                playerUuid,
                playerName,
                amount,
                "service-fee:" + channel);
    }

    /** Shop rolling average when RootMC is online; else static worth.yml fallback. */
    public Double itemPrice(Material material) {
        if (material == null || material.isAir()) return null;
        RootMcEconomyBridge bridge = marketBridge();
        if (bridge != null) {
            double avg = bridge.averagePrice(material.name());
            if (avg > 0) return avg;
        }
        return worth(material);
    }

    public Double mintRate(Material material) {
        return GoldItemValue.mintRate(material);
    }

    /**
     * Physical {@code /mint hand|all}: reserve-tier tax + wallet + mint-gross audit (backing).
     */
    public RootMcIncomeSweepResult mintIncomeAfterTax(UUID uuid, String username, double gross) throws Exception {
        return incomeAfterTax(uuid, username, gross, true, true);
    }

    /**
     * Gold ore → Notes: mining tax (solar/CPU overlay) + wallet + mint-gross audit (backing).
     */
    public RootMcIncomeSweepResult mineNotesAfterTax(UUID uuid, String username, double gross) throws Exception {
        return incomeAfterTax(uuid, username, gross, true, false);
    }

    /**
     * Other Notes income: tax + wallet only. Does not increase mint backing.
     */
    public RootMcIncomeSweepResult incomeAfterTax(UUID uuid, String username, double gross) throws Exception {
        return incomeAfterTax(uuid, username, gross, false, false);
    }

    private RootMcIncomeSweepResult incomeAfterTax(
            UUID uuid, String username, double gross, boolean mintBacking, boolean mintTaxOnly) throws Exception {
        if (gross <= 0) {
            return RootMcIncomeSweepResult.allToWallet(0);
        }
        double net = gross;
        if (treasury != null && treasury.transactionTaxEnabled()) {
            double tax = mintTaxOnly
                    ? treasury.computeMintTaxAmount(uuid, gross)
                    : treasury.computeTaxAmount(uuid, gross);
            net = Math.max(0, gross - tax);
            if (tax > 0) {
                if (treasury.retireTaxAsBurn()) {
                    treasury.burnNotes(
                            tax,
                            uuid,
                            username,
                            String.format(java.util.Locale.US, mintBacking ? "mint:gross=%.3f" : "income:gross=%.3f", gross));
                } else {
                    treasury.creditTreasury(
                            tax,
                            com.rootrecord.minecraft.common.TreasuryLedgerType.TAX,
                            uuid,
                            username,
                            String.format(java.util.Locale.US, mintBacking ? "mint:gross=%.3f" : "income:gross=%.3f", gross));
                }
            }
        }
        if (mintBacking && treasury != null) {
            treasury.recordMintGrossAudit(uuid, gross);
        }
        return depositIncome(uuid, username, net);
    }

    public boolean redeemMintGold(UUID uuid, String username, double grossG) {
        if (treasury == null || grossG <= 0 || uuid == null) {
            return false;
        }
        return treasury.acceptMintRedemption(uuid, username, grossG);
    }

    private RootMcEconomyBridge marketBridge() {
        var bn = getServer().getPluginManager().getPlugin("RootMC");
        if (bn instanceof RootMcEconomyBridge bridge) return bridge;
        return null;
    }

    public boolean marketBridgeAvailable() {
        return marketBridge() != null;
    }

    @Override
    public double balance(UUID uuid, String username) {
        if (uuid == null) {
            return 0;
        }
        String name = TownyEconomyAccounts.canonicalStoredUsername(uuid, username);
        try {
            return economy.balance(uuid, name);
        } catch (Exception ex) {
            return 0;
        }
    }

    public RootMcIncomeSweepResult depositIncome(UUID uuid, String username, double amount) throws Exception {
        if (amount <= 0) {
            return RootMcIncomeSweepResult.allToWallet(0);
        }
        RootMcLoanService loan = resolveLoanService();
        if (loan != null) {
            RootMcIncomeSweepResult sweep = loan.applyIncome(uuid, username, amount);
            if (sweep.toWallet() > 0) {
                economy.deposit(uuid, username, sweep.toWallet());
            }
            return sweep;
        }
        economy.deposit(uuid, username, amount);
        return RootMcIncomeSweepResult.allToWallet(amount);
    }

    public void deposit(UUID uuid, String username, double amount) throws Exception {
        economy.deposit(uuid, username, amount);
    }

    public boolean donateToReserve(UUID uuid, String username, double amount) throws Exception {
        if (treasury == null || amount <= 0) {
            return false;
        }
        return treasury.acceptDonation(uuid, username, amount);
    }

    /** Ban confiscation: move full wallet balance to Server Reserve (DONATION ledger). */
    public double seizeWalletToReserve(UUID uuid, String username) throws Exception {
        if (uuid == null) {
            return 0;
        }
        String name = username == null || username.isBlank() ? "player" : username;
        double bal = balance(uuid, name);
        if (bal < GoldMoney.MIN_AMOUNT) {
            return 0;
        }
        if (!donateToReserve(uuid, name, bal)) {
            return 0;
        }
        return bal;
    }

    public boolean transfer(Player from, Player to, double amount) throws Exception {
        if (amount <= 0) {
            return false;
        }
        if (treasury != null && treasury.transactionTaxEnabled()) {
            double net = treasury.withholdTransactionTax(from.getUniqueId(), from.getName(), amount, "pay");
            if (net < 0) {
                return false;
            }
            depositIncome(to.getUniqueId(), to.getName(), net);
            return true;
        }
        boolean ok = economy.withdraw(from.getUniqueId(), from.getName(), amount);
        if (!ok) {
            return false;
        }
        depositIncome(to.getUniqueId(), to.getName(), amount);
        return true;
    }

    public boolean withdraw(UUID uuid, String username, double amount) throws Exception {
        return economy.withdraw(uuid, username, amount);
    }

    public boolean withdrawAllowingDebt(UUID uuid, String username, double amount) throws Exception {
        return economy.withdraw(uuid, username, amount, true);
    }

    public void setBalance(UUID uuid, String username, double amount) throws Exception {
        economy.setBalance(uuid, username, amount);
    }
    public void resetBalance(UUID uuid, String username) throws Exception {
        economy.resetBalance(uuid, username);
    }
    public List<EconomyStore.BalanceRow> topBalances(int limit) throws Exception {
        return economy.topBalances(limit);
    }

    /** Full MySQL wallet export for RootMC economy sync (excludes towny-server / town / nation banks). */
    public List<EconomyStore.PlayerBalanceRow> allPlayerBalancesForSync() {
        try {
            return economy.allPlayerBalancesForSync();
        } catch (Exception ex) {
            getLogger().warning("allPlayerBalancesForSync failed: " + ex.getMessage());
            return List.of();
        }
    }

    /** Server Reserve + town/nation bank Notes for gold supply dashboard sync. */
    public List<EconomyStore.SystemBalanceRow> allSystemBalancesForSync() {
        try {
            return economy.allSystemBalancesForSync();
        } catch (Exception ex) {
            getLogger().warning("allSystemBalancesForSync failed: " + ex.getMessage());
            return List.of();
        }
    }

    /** Cumulative physical gold found (mine + loot), pegged at /mint rates — for RootMC sync. */
    public List<GoldFoundStore.GoldFoundRow> allGoldFoundForSync() {
        try {
            return goldFound.allForSync();
        } catch (Exception ex) {
            getLogger().warning("allGoldFoundForSync failed: " + ex.getMessage());
            return List.of();
        }
    }

    public GoldFoundStore goldFoundStore() {
        return goldFound;
    }

    public GoldItemEventStore goldItemEventStore() {
        return goldItemEvents;
    }

    /** Incremental gold-item provenance export for RootMC economy sync. */
    public List<GoldItemEventStore.EventRow> goldItemEventsAfter(long afterId, int limit) {
        try {
            if (goldItemEvents == null) {
                return List.of();
            }
            return goldItemEvents.eventsAfter(afterId, limit);
        } catch (Exception ex) {
            getLogger().warning("goldItemEventsAfter failed: " + ex.getMessage());
            return List.of();
        }
    }

    public void recordGoldItemEvent(
            UUID uuid,
            String username,
            com.rootrecord.minecraft.rootessentials.goldfound.GoldItemEventType eventType,
            String obtainedVia,
            org.bukkit.Material material,
            int stackAmount,
            double goldG,
            org.bukkit.Location location,
            String contextJson) {
        if (goldItemEvents == null || uuid == null || material == null || stackAmount <= 0) {
            return;
        }
        double roundedGold = com.rootrecord.minecraft.common.GoldMoney.round(Math.max(0, goldG));
        if (roundedGold <= 0
                && (eventType == com.rootrecord.minecraft.rootessentials.goldfound.GoldItemEventType.ACQUIRED
                        || eventType == com.rootrecord.minecraft.rootessentials.goldfound.GoldItemEventType.MINT_TO_ITEMS)) {
            return;
        }
        final boolean tallyMaterial =
                eventType == com.rootrecord.minecraft.rootessentials.goldfound.GoldItemEventType.ACQUIRED
                        || eventType == com.rootrecord.minecraft.rootessentials.goldfound.GoldItemEventType.MINT_TO_ITEMS;
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                goldItemEvents.record(
                        uuid, username, eventType, obtainedVia, material, stackAmount, roundedGold, location, contextJson);
                if (tallyMaterial && goldMaterials != null) {
                    goldMaterials.record(uuid, username, material, stackAmount, roundedGold);
                }
            } catch (Exception ex) {
                getLogger().warning("Gold item event failed for " + username + ": " + ex.getMessage());
            }
        });
    }

    public GoldMaterialObtainedStore goldMaterialStore() {
        return goldMaterials;
    }

    public List<GoldMaterialObtainedStore.PlayerMaterialRow> allGoldMaterialsForSync() {
        try {
            return goldMaterials == null ? List.of() : goldMaterials.allPlayersForSync();
        } catch (Exception ex) {
            getLogger().warning("allGoldMaterialsForSync failed: " + ex.getMessage());
            return List.of();
        }
    }

    public List<GoldMaterialObtainedStore.ServerMaterialRow> goldMaterialServerTotals() {
        try {
            return goldMaterials == null ? List.of() : goldMaterials.serverTotals();
        } catch (Exception ex) {
            getLogger().warning("goldMaterialServerTotals failed: " + ex.getMessage());
            return List.of();
        }
    }

    public List<EconomyStore.BalanceRow> topBaltop(EconomyStore.BaltopKind kind, int limit) throws Exception {
        return economy.topBaltop(kind, limit);
    }

    public java.util.Optional<EconomyStore.RankedBalance> baltopRank(EconomyStore.BaltopKind kind, String name)
            throws Exception {
        return economy.baltopRank(kind, name);
    }

    public java.util.Optional<EconomyStore.RankedBalance> baltopRankForPlayer(UUID uuid, String username)
            throws Exception {
        return economy.baltopRankForPlayer(uuid, username);
    }

    public ReserveStatsService.ReserveSnapshot reserveSnapshot(UUID playerUuid) throws Exception {
        if (reserveStats == null) {
            throw new IllegalStateException("Reserve stats not initialized");
        }
        return reserveStats.build(playerUuid);
    }

    @Override public double balance(UUID playerId) {
        var p = getServer().getPlayer(playerId);
        return balance(playerId, p != null ? p.getName() : "player");
    }
    @Override public boolean has(UUID playerId, double amount) { return balance(playerId) >= amount; }
    @Override public boolean has(UUID accountId, String accountName, double amount) {
        return balance(accountId, accountName) >= amount;
    }
    @Override public boolean withdraw(UUID playerId, double amount) {
        var p = getServer().getPlayer(playerId);
        try { return economy.withdraw(playerId, p != null ? p.getName() : "player", amount); }
        catch (Exception ex) { return false; }
    }
    @Override public boolean withdrawAccount(UUID accountId, String accountName, double amount) {
        try { return economy.withdraw(accountId, accountName == null ? "player" : accountName, amount); }
        catch (Exception ex) { return false; }
    }
    @Override public void deposit(UUID playerId, double amount) {
        var p = getServer().getPlayer(playerId);
        try { deposit(playerId, p != null ? p.getName() : "player", amount); }
        catch (Exception ignored) {}
    }
    @Override public void depositAccount(UUID accountId, String accountName, double amount) {
        try { deposit(accountId, accountName == null ? "player" : accountName, amount); }
        catch (Exception ignored) {}
    }
    @Override public void depositIncome(UUID playerId, double amount) {
        var p = getServer().getPlayer(playerId);
        try { depositIncome(playerId, p != null ? p.getName() : "player", amount); }
        catch (Exception ignored) {}
    }

    public java.util.Optional<RootMcLoanService.LoanBalanceSummary> loanSummary(UUID uuid) {
        RootMcLoanService loan = resolveLoanService();
        return loan != null ? loan.balanceSummary(uuid) : java.util.Optional.empty();
    }

    private RootMcLoanService resolveLoanService() {
        return RootMcLoanResolver.resolve(this);
    }
}
