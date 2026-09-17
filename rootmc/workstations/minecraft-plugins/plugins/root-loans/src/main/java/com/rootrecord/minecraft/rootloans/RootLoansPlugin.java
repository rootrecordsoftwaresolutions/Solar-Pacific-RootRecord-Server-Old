package com.rootrecord.minecraft.rootloans;

import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.common.RootMcLoanService;
import com.rootrecord.minecraft.common.RootMcTreasuryResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.rootloans.command.LoanCommand;
import com.rootrecord.minecraft.rootloans.command.RootLoansAdminCommand;
import com.rootrecord.minecraft.rootloans.command.TownLoanCommand;
import com.rootrecord.minecraft.rootloans.config.LoanDailyTaxConfig;
import com.rootrecord.minecraft.rootloans.config.LoansConfig;
import com.rootrecord.minecraft.rootloans.config.LoansMessages;
import com.rootrecord.minecraft.rootloans.config.TownLoansConfig;
import com.rootrecord.minecraft.rootloans.data.LoanDailyTaxStateStore;
import com.rootrecord.minecraft.rootloans.data.LoansStore;
import com.rootrecord.minecraft.rootloans.data.TownLoansStore;
import com.rootrecord.minecraft.rootloans.listener.GoldOreLoanListener;
import com.rootrecord.minecraft.rootloans.listener.TreasuryReadyListener;
import com.rootrecord.minecraft.rootloans.schedule.LoanDailyTaxScheduler;
import com.rootrecord.minecraft.rootloans.service.LoanDailyTaxService;
import com.rootrecord.minecraft.rootloans.service.LoanService;
import com.rootrecord.minecraft.rootloans.service.RankLoanLimitService;
import com.rootrecord.minecraft.rootloans.service.TownLoanService;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.text.DecimalFormat;

public final class RootLoansPlugin extends JavaPlugin {

    private RootRecordYamlConfig yamlConfig;
    private LoansConfig loansConfig;
    private TownLoansConfig townLoansConfig;
    private LoanDailyTaxConfig loanDailyTaxConfig;
    private LoansMessages messages;
    private LoansStore store;
    private TownLoansStore townLoansStore;
    private LoanDailyTaxStateStore loanDailyTaxState;
    private LoanService loans;
    private TownLoanService townLoans;
    private LoanDailyTaxService loanDailyTax;
    private LoanDailyTaxScheduler loanDailyTaxScheduler;
    private RankLoanLimitService rankLimits;
    private final DecimalFormat moneyFmt = new DecimalFormat("0.000");

    private boolean mysqlReady;
    private boolean finishScheduled;
    private boolean commandsRegistered;

    @Override
    public void onEnable() {
        RootRecordFolders.ensureDir(this);
        yamlConfig = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_LOANS_CONFIG, "root-loans.yml");
        yamlConfig.load();
        loans = new LoanService(this);
        townLoans = new TownLoanService(this);
        loanDailyTax = new LoanDailyTaxService(this);
        loanDailyTaxState = new LoanDailyTaxStateStore(this);
        loanDailyTaxState.load();
        loanDailyTaxScheduler = new LoanDailyTaxScheduler(this, loanDailyTaxState);
        registerCommands();
        getServer().getPluginManager().registerEvents(new TreasuryReadyListener(this), this);
        reloadLocalConfig();

        if (tryFinishEnable()) {
            return;
        }
        scheduleFinishEnable();
    }

    private static final int STARTUP_RETRY_TICKS = 10;
    private static final int STARTUP_MAX_ATTEMPTS = 20;

    private void scheduleFinishEnable() {
        if (finishScheduled) {
            return;
        }
        finishScheduled = true;
        attemptFinishEnable(0);
    }

    /** Re-attempt treasury bridge + service registration (reload / Root-Essentials enable). */
    public void retryFinishEnable() {
        refreshTreasuryBridge();
        if (tryFinishEnable()) {
            return;
        }
        finishScheduled = false;
        scheduleFinishEnable();
    }

    public void refreshTreasuryBridge() {
        if (loans != null) {
            loans.reload(loansConfig, store, resolveTreasury(), rankLimits);
        }
        if (townLoans != null) {
            townLoans.reload(townLoansConfig, townLoansStore, resolveTreasury());
        }
    }

    private void attemptFinishEnable(int attempt) {
        loans.reload(loansConfig, store, resolveTreasury(), rankLimits);
        if (tryFinishEnable()) {
            return;
        }
        if (attempt + 1 >= STARTUP_MAX_ATTEMPTS) {
            logStartupFailure();
            getLogger().severe("Root-Loans commands are registered but loans stay disabled until treasury is available.");
            return;
        }
        getServer().getScheduler().runTaskLater(this, () -> attemptFinishEnable(attempt + 1), STARTUP_RETRY_TICKS);
    }

    /** @return true when fully enabled */
    private boolean tryFinishEnable() {
        if (!loansConfig.enabled()) {
            return false;
        }
        if (!mysqlReady) {
            return false;
        }
        if (!loans.enabled()) {
            return false;
        }
        if (!commandsRegistered) {
            getServer().getPluginManager().registerEvents(new GoldOreLoanListener(this), this);
            getServer().getServicesManager().register(RootMcLoanService.class, loans, this, ServicePriority.Normal);
            commandsRegistered = true;
            getLogger().info("Root-Loans enabled (treasury bridge active).");
        }
        return true;
    }

    private void logStartupFailure() {
        if (!loansConfig.enabled()) {
            getLogger().severe("Root-Loans disabled  -  set loan.enabled: true in root-loans.yml.");
            return;
        }
        if (!mysqlReady) {
            getLogger().severe("Root-Loans disabled  -  MySQL schema init failed (see earlier log line).");
            return;
        }
        if (resolveTreasury() == null) {
            getLogger().severe(
                    "Root-Loans disabled  -  no treasury. Ensure Root-Essentials started with closed-loop economy.");
            return;
        }
        if (resolveEconomy() == null) {
            getLogger().severe(
                    "Root-Loans disabled  -  no economy. Ensure Root-Essentials started and Vault.jar is installed.");
            return;
        }
        getLogger().severe("Root-Loans could not start  -  check MySQL and Root-Essentials economy.");
    }

    @Override
    public void onDisable() {
        if (loanDailyTaxScheduler != null) {
            loanDailyTaxScheduler.stop();
        }
        getServer().getServicesManager().unregister(RootMcLoanService.class, loans);
    }

    public void reloadLocalConfig() {
        if (yamlConfig != null) {
            yamlConfig.reload();
        }
        FileConfiguration cfg = yamlConfig != null ? yamlConfig.config() : null;
        loansConfig = LoansConfig.from(this, cfg);
        townLoansConfig = TownLoansConfig.from(cfg);
        loanDailyTaxConfig = LoanDailyTaxConfig.from(cfg);
        messages = LoansMessages.from(cfg);
        store = new LoansStore(loansConfig);
        townLoansStore = new TownLoansStore(loansConfig, townLoansConfig);
        rankLimits = buildRankLimits(cfg);
        mysqlReady = true;
        try {
            store.initSchema();
            townLoansStore.initSchema();
        } catch (Exception ex) {
            mysqlReady = false;
            getLogger().severe("MySQL init failed: " + ex.getMessage());
        }
        loans.reload(loansConfig, store, resolveTreasury(), rankLimits);
        townLoans.reload(townLoansConfig, townLoansStore, resolveTreasury());
        loanDailyTax.reload(loanDailyTaxConfig);
        if (loanDailyTaxScheduler != null) {
            loanDailyTaxScheduler.stop();
            if (mysqlReady) {
                loanDailyTaxState.load();
                loanDailyTaxScheduler.start();
            }
        }
        retryFinishEnable();
    }

    private RankLoanLimitService buildRankLimits(FileConfiguration loansCfg) {
        File ranksFile = RootRecordFolders.configFile(this, RootRecordFolders.ROOT_RANKS_CONFIG);
        RankLoanLimitService service = RankLoanLimitService.fromConfigs(loansCfg, ranksFile);
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            try {
                LuckPerms luckPerms = net.luckperms.api.LuckPermsProvider.get();
                service.bindLuckPerms(luckPerms);
            } catch (IllegalStateException ex) {
                getLogger().warning("LuckPerms API unavailable - loan limits use default-max-loan only.");
            }
        } else if (loansCfg.getBoolean("loan.rank-limits-enabled", true)) {
            getLogger().warning("LuckPerms missing - loan limits use default-max-loan only.");
        }
        if (service.enabled()) {
            getLogger().info("Rank loan limits loaded (" + service.tiers().size()
                    + " tiers, mode=" + loansCfg.getString("loan.max-cap-mode", "rank") + ").");
        } else {
            getLogger().warning("Rank loan limits inactive - using default-max-loan "
                    + service.defaultMaxLoan() + " G.");
        }
        return service;
    }

    private void registerCommands() {
        var loan = getCommand("loan");
        if (loan != null) {
            loan.setExecutor(new LoanCommand(this));
        }
        var admin = getCommand("rootloans");
        if (admin != null) {
            admin.setExecutor(new RootLoansAdminCommand(this));
        }
        var townLoan = getCommand("townloan");
        if (townLoan != null) {
            townLoan.setExecutor(new TownLoanCommand(this));
        }
    }

    public boolean isLoansReady() {
        if (loans != null && !loans.enabled()) {
            refreshTreasuryBridge();
            tryFinishEnable();
        }
        return loans != null && loans.enabled();
    }

    public String loansDisabledReason() {
        if (loansConfig == null || !loansConfig.enabled()) {
            return "config-disabled";
        }
        if (!mysqlReady) {
            return "mysql";
        }
        if (resolveTreasury() == null) {
            return "treasury";
        }
        return "unknown";
    }

    public RootMcEconomyService resolveEconomy() {
        return RootMcEconomyResolver.resolve(this);
    }

    RootMcTreasuryService resolveTreasury() {
        return RootMcTreasuryResolver.resolve(this);
    }

    public boolean isTownLoansReady() {
        refreshTreasuryBridge();
        return townLoans != null
                && townLoans.enabled()
                && mysqlReady
                && resolveTreasury() != null;
    }

    public LoanDailyTaxConfig loanDailyTaxConfig() {
        return loanDailyTaxConfig;
    }

    public LoanDailyTaxService loanDailyTax() {
        return loanDailyTax;
    }

    public LoanDailyTaxScheduler loanDailyTaxScheduler() {
        return loanDailyTaxScheduler;
    }

    public TownLoansConfig townLoansConfig() {
        return townLoansConfig;
    }

    public TownLoansStore townLoansStore() {
        return townLoansStore;
    }

    public TownLoanService townLoans() {
        return townLoans;
    }

    public LoansConfig loansConfig() {
        return loansConfig;
    }

    public LoansStore store() {
        return store;
    }

    public LoanService loans() {
        return loans;
    }

    public String money(double value) {
        synchronized (moneyFmt) {
            return moneyFmt.format(value);
        }
    }

    public String colorize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public String msg(String key) {
        String body = yamlConfig.config().getString("messages." + key, key);
        return colorize(messages.prefix() + body);
    }
}
