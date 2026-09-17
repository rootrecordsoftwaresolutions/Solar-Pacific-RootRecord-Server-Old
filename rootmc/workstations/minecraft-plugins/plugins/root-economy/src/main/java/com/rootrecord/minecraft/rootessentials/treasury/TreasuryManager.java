package com.rootrecord.minecraft.rootessentials.treasury;


import com.rootrecord.minecraft.common.GoldMoney;

import com.rootrecord.minecraft.common.DeathFeeSettlement;
import com.rootrecord.minecraft.common.RootMcIncomeSweepResult;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.common.RootMcVotePerk;
import com.rootrecord.minecraft.common.ShadedServiceBridge;
import com.rootrecord.minecraft.common.TreasuryLedgerType;
import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import com.rootrecord.minecraft.rootessentials.data.EconomyStore;
import com.rootrecord.minecraft.rootessentials.data.EconomySystemAccounts;
import com.rootrecord.minecraft.rootessentials.data.TreasuryLedgerStore;
import com.rootrecord.minecraft.rootessentials.treasury.EconomyBaseline.NoteSupplySnapshot;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;
import java.util.logging.Level;

public final class TreasuryManager implements RootMcTreasuryService {

    private final JavaPlugin plugin;
    private final EconomyStore economy;
    private final TreasuryLedgerStore ledger;
    private final UUID treasuryUuid;
    private final String treasuryUsername;
    private volatile boolean transactionTaxEnabled;
    private volatile double transactionTaxRate;
    private volatile boolean dynamicTaxEnabled;
    private volatile double dynamicTaxShortfallFactor;
    private volatile double dynamicTaxMaxRate;
    private volatile boolean dynamicTaxRetireOverIssue;
    private volatile DoubleSupplier powerTaxRate = () -> 0;
    private volatile long noteSupplyCacheAtMs;
    private volatile NoteSupplySnapshot cachedNoteSupply;
    private final ConcurrentHashMap<String, Long> recentSettlements = new ConcurrentHashMap<>();

    private static final long SETTLEMENT_DEBOUNCE_MS = 3000L;
    private static final long NOTE_SUPPLY_CACHE_MS = 60_000L;

    public TreasuryManager(
            JavaPlugin plugin,
            EconomyStore economy,
            TreasuryLedgerStore ledger,
            boolean transactionTaxEnabled,
            double transactionTaxRate,
            boolean dynamicTaxEnabled,
            double dynamicTaxShortfallFactor,
            double dynamicTaxMaxRate,
            boolean dynamicTaxRetireOverIssue) {
        this.plugin = plugin;
        this.economy = economy;
        this.ledger = ledger;
        this.treasuryUuid = EconomySystemAccounts.townyServerUuidValue();
        this.treasuryUsername = EconomySystemAccounts.townyServerUsername();
        this.transactionTaxEnabled = transactionTaxEnabled;
        this.transactionTaxRate = transactionTaxRate;
        this.dynamicTaxEnabled = dynamicTaxEnabled;
        this.dynamicTaxShortfallFactor = dynamicTaxShortfallFactor;
        this.dynamicTaxMaxRate = dynamicTaxMaxRate;
        this.dynamicTaxRetireOverIssue = dynamicTaxRetireOverIssue;
    }

    public void reload(
            boolean transactionTaxEnabled,
            double transactionTaxRate,
            boolean dynamicTaxEnabled,
            double dynamicTaxShortfallFactor,
            double dynamicTaxMaxRate,
            boolean dynamicTaxRetireOverIssue) {
        this.transactionTaxEnabled = transactionTaxEnabled;
        this.transactionTaxRate = Math.max(0, transactionTaxRate);
        this.dynamicTaxEnabled = dynamicTaxEnabled;
        this.dynamicTaxShortfallFactor = Math.max(0, Math.min(1, dynamicTaxShortfallFactor));
        this.dynamicTaxMaxRate = Math.max(0, dynamicTaxMaxRate);
        this.dynamicTaxRetireOverIssue = dynamicTaxRetireOverIssue;
        invalidateNoteSupplyCache();
    }

    public boolean dynamicTaxRetireOverIssue() {
        return dynamicTaxRetireOverIssue;
    }

    /** While over-issued, burn unbacked Notes â€” unless reserve ledger is negative (tax refills reserve). */
    public boolean retireTaxAsBurn() {
        if (currentLedgerNet() < -0.01) {
            return false;
        }
        return dynamicTaxRetireOverIssue && dynamicTaxEnabled && noteSupplyCached().overIssued();
    }

    private void invalidateNoteSupplyCache() {
        noteSupplyCacheAtMs = 0L;
        cachedNoteSupply = null;
    }

    public void burnNotes(double amount, UUID fromUuid, String fromName, String details) {
        if (amount <= 0 || fromUuid == null) {
            return;
        }
        try {
            ledger.insertAudit(TreasuryLedgerType.NOTE_BURN, amount, fromUuid, null, details);
            invalidateNoteSupplyCache();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Note burn audit failed: " + ex.getMessage(), ex);
        }
    }

    public boolean dynamicTaxEnabled() {
        return dynamicTaxEnabled;
    }

    /** Base configured rate (used only when dynamic tax is off). */
    @Override
    public double transactionTaxRate() {
        return transactionTaxRate;
    }

    public double dynamicTaxShortfallFactor() {
        return dynamicTaxShortfallFactor;
    }

    /** 0.05% floor on every taxed transfer, regardless of reserve tier or solar. */
    public static final double MIN_RESERVE_TAX_RATE = 0.0005d;

    /** Solar offline env tax (1%) from {@link com.rootrecord.minecraft.rootessentials.service.SolarMiningMultiplierService}. */
    public void setPowerTaxRateSource(DoubleSupplier src) {
        this.powerTaxRate = src != null ? src : () -> 0;
    }

    public double powerTaxRate() {
        try {
            return Math.max(0, powerTaxRate.getAsDouble());
        } catch (Exception ex) {
            return 0;
        }
    }

    /**
     * Reserve-tier tax (backing − circulating Notes), never below {@link #MIN_RESERVE_TAX_RATE}.
     */
    public double reserveTierTaxRate() {
        if (!transactionTaxEnabled) {
            return 0;
        }
        double rate;
        if (!dynamicTaxEnabled) {
            rate = Math.max(0, transactionTaxRate);
        } else {
            rate = ReserveLedgerTaxTiers.rateForLedgerNet(noteSupplyCached().reserveNotesG());
            if (dynamicTaxMaxRate > 0) {
                rate = Math.min(dynamicTaxMaxRate, rate);
            }
            rate = Math.max(0, rate);
        }
        return Math.max(MIN_RESERVE_TAX_RATE, rate);
    }

    /**
     * Reserve tier (min 0.05%) plus offline env tax (1% when solar disconnected).
     * Applies to pay, shops, mine Notes, and {@code /mint}.
     */
    @Override
    public double effectiveTransactionTaxRate() {
        if (!transactionTaxEnabled) {
            return 0;
        }
        return Math.max(0, reserveTierTaxRate() + powerTaxRate());
    }

    /** Mint backing − circulating Notes (displayed Server Reserve; can be negative). */
    public double reserveUtilization() {
        return noteSupplyCached().reserveNotesG();
    }

    /** Post-reset treasury ledger net (July 1+), used for tax tiers and reserve headline. */
    public double currentLedgerNet() {
        try {
            return ledger.totalsAllTime().net();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.FINE, "Ledger net read failed: " + ex.getMessage());
            return 0;
        }
    }

    @Override
    public double reserveLedgerNet() {
        return currentLedgerNet();
    }

    public double computeTaxAmount(double gross) {
        return computeTaxAmount(null, gross);
    }

    public double computeTaxAmount(UUID payerUuid, double gross) {
        return taxAtRate(gross, effectiveTransactionTaxRate() * voteTaxMultiplier(payerUuid));
    }

    /** Mint peg conversion tax — same reserve + offline env stack as other transfers. */
    public double computeMintTaxAmount(double gross) {
        return computeTaxAmount(null, gross);
    }

    public double computeMintTaxAmount(UUID payerUuid, double gross) {
        return computeTaxAmount(payerUuid, gross);
    }

    private double voteTaxMultiplier(UUID payerUuid) {
        if (payerUuid == null) {
            return 1.0d;
        }
        try {
            RootMcVotePerk perk = ShadedServiceBridge.resolveVotePerk(plugin);
            if (perk == null) {
                return 1.0d;
            }
            double multiplier = perk.taxMultiplier(payerUuid);
            if (multiplier <= 0 || multiplier > 1.0d) {
                return 1.0d;
            }
            return multiplier;
        } catch (Exception ex) {
            return 1.0d;
        }
    }

    private double taxAtRate(double gross, double rate) {
        if (!transactionTaxEnabled() || gross <= 0 || rate <= 0) {
            return 0;
        }
        double tax = GoldMoney.round(gross * rate);
        if (tax < GoldMoney.MIN_AMOUNT) {
            return 0;
        }
        if (tax >= gross) {
            return Math.max(0, gross - 0.01);
        }
        return tax;
    }

    private NoteSupplySnapshot noteSupplyCached() {
        long now = System.currentTimeMillis();
        if (cachedNoteSupply != null && now - noteSupplyCacheAtMs < NOTE_SUPPLY_CACHE_MS) {
            return cachedNoteSupply;
        }
        try {
            cachedNoteSupply = EconomyBaseline.computeNoteSupply(
                    ledger.totalGoldMinedGross(),
                    economy.sumCirculatingNotes(),
                    0,
                    ledger.totalNoteBurnRetired(),
                    ledger.totalDonationBurnRetired(),
                    ledger.totalTaxMiscreditedSinceJuly(),
                    ledger.totalOverIssueShortfallRepaid());
            noteSupplyCacheAtMs = now;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.FINE, "Note supply cache refresh failed: " + ex.getMessage());
            if (cachedNoteSupply == null) {
                cachedNoteSupply = EconomyBaseline.computeNoteSupply(0, 0, 0, 0, 0, 0);
            }
        }
        return cachedNoteSupply;
    }

    @Override
    public boolean transactionTaxEnabled() {
        if (!transactionTaxEnabled) {
            return false;
        }
        if (dynamicTaxEnabled) {
            return true;
        }
        return transactionTaxRate > 0;
    }

    @Override
    public double withholdTransactionTax(UUID payerUuid, String payerName, double gross, String channel) {
        if (gross <= 0 || payerUuid == null) {
            return -1;
        }
        double tax = computeTax(payerUuid, gross);
        double net = gross - tax;
        try {
            boolean ok;
            boolean burnTax = tax > 0 && retireTaxAsBurn();
            if (burnTax) {
                ok = economy.withholdTransactionTaxBurn(
                        payerUuid,
                        payerName,
                        gross,
                        tax,
                        ledger,
                        channel);
            } else {
                ok = economy.withholdTransactionTax(
                        payerUuid,
                        payerName,
                        treasuryUuid,
                        treasuryUsername,
                        gross,
                        tax,
                        ledger,
                        TreasuryLedgerType.TAX,
                        channel);
            }
            if (ok) {
                invalidateNoteSupplyCache();
            }
            return ok ? net : -1;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Treasury tax withhold failed: " + ex.getMessage(), ex);
            return -1;
        }
    }

    @Override
    public void creditTreasury(
            double amount,
            TreasuryLedgerType type,
            UUID sourceUuid,
            String sourceName,
            String details) {
        if (amount <= 0) {
            return;
        }
        try {
            economy.depositTreasury(treasuryUuid, treasuryUsername, amount, ledger, type, sourceUuid, details);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Treasury credit failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public DeathFeeSettlement settleDeathFee(
            UUID victimUuid,
            String victimName,
            UUID killerUuid,
            String killerName,
            double victimBalancePercent,
            double treasuryShareOfFee,
            double minFeeGold) {
        if (victimUuid == null || victimBalancePercent <= 0) {
            return null;
        }
        try {
            double balance = economy.balance(victimUuid, victimName);
            EconomyStore.DeathFeeResult result = economy.applyDeathFee(
                    victimUuid,
                    victimName,
                    killerUuid,
                    killerName,
                    balance,
                    victimBalancePercent,
                    treasuryShareOfFee,
                    minFeeGold,
                    treasuryUuid,
                    treasuryUsername,
                    ledger);
            if (result == null) {
                return null;
            }
            invalidateNoteSupplyCache();
            return new DeathFeeSettlement(result.grossFee(), result.treasuryAmount(), result.killerAmount());
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Death fee settlement failed: " + ex.getMessage(), ex);
            return null;
        }
    }

    @Override
    public void depositClosedLoopVault(double amount) {
        if (amount <= 0) {
            return;
        }
        try {
            economy.deposit(treasuryUuid, treasuryUsername, amount);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Treasury closed-loop deposit failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void settleClosedLoopPayment(UUID payerUuid, String payerName, double gross, String channel) {
        if (gross <= 0 || isDuplicateSettlement(payerUuid, gross, channel)) {
            return;
        }
        double tax = computeTax(payerUuid, gross);
        double net = Math.max(0, gross - tax);
        String details = channel == null || channel.isBlank()
                ? String.format(Locale.US, "gross=%.3f", gross)
                : channel + String.format(Locale.US, ":gross=%.3f", gross);
        if (tax > 0) {
            if (retireTaxAsBurn()) {
                burnNotes(tax, payerUuid, payerName, details);
            } else {
                creditTreasury(tax, TreasuryLedgerType.TAX, payerUuid, payerName, details);
            }
        }
        if (net > 0) {
            creditTreasury(net, TreasuryLedgerType.TOWNY_SINK, payerUuid, payerName, details);
        }
    }

    @Override
    public boolean grantToPlayer(
            UUID recipientUuid,
            String recipientName,
            double amount,
            UUID operatorUuid,
            String operatorName,
            String reason) {
        if (amount <= 0 || recipientUuid == null) {
            return false;
        }
        try {
            return economy.grantFromTreasury(
                    treasuryUuid,
                    treasuryUsername,
                    recipientUuid,
                    recipientName,
                    amount,
                    ledger,
                    TreasuryLedgerType.GRANT,
                    operatorUuid,
                    reason == null ? "" : reason);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Treasury grant failed: " + ex.getMessage(), ex);
            return false;
        }
    }

    @Override
    public RootMcIncomeSweepResult payVoteReward(UUID recipientUuid, String recipientName, double amount, String service) {
        if (amount <= 0 || recipientUuid == null) {
            return null;
        }
        try {
            String details = "service=" + (service == null ? "" : service);
            if (!economy.debitTreasuryForPayout(
                    treasuryUuid,
                    treasuryUsername,
                    recipientUuid,
                    recipientName,
                    amount,
                    ledger,
                    TreasuryLedgerType.VOTE,
                    treasuryUuid,
                    details)) {
                return null;
            }
            if (plugin instanceof RootEconomyPlugin essentials) {
                return essentials.depositIncome(recipientUuid, recipientName, amount);
            }
            economy.deposit(recipientUuid, recipientName, amount);
            return RootMcIncomeSweepResult.allToWallet(amount);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Treasury vote payout failed: " + ex.getMessage(), ex);
            return null;
        }
    }

    @Override
    public RootMcIncomeSweepResult payPlaytimeReward(
            UUID recipientUuid, String recipientName, double amount, String details) {
        if (amount <= 0 || recipientUuid == null) {
            return null;
        }
        try {
            String ledgerDetails = details == null ? "" : details;
            if (!economy.debitTreasuryForPayout(
                    treasuryUuid,
                    treasuryUsername,
                    recipientUuid,
                    recipientName,
                    amount,
                    ledger,
                    TreasuryLedgerType.PLAYTIME,
                    treasuryUuid,
                    ledgerDetails)) {
                return null;
            }
            if (plugin instanceof RootEconomyPlugin essentials) {
                return essentials.depositIncome(recipientUuid, recipientName, amount);
            }
            economy.deposit(recipientUuid, recipientName, amount);
            return RootMcIncomeSweepResult.allToWallet(amount);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Treasury playtime payout failed: " + ex.getMessage(), ex);
            return null;
        }
    }

    @Override
    public RootMcIncomeSweepResult payTryReward(
            UUID recipientUuid, String recipientName, double amount, String tryId) {
        if (amount <= 0 || recipientUuid == null) {
            return null;
        }
        try {
            String details = "root-try:" + (tryId == null ? "" : tryId);
            if (!economy.debitTreasuryForPayout(
                    treasuryUuid,
                    treasuryUsername,
                    recipientUuid,
                    recipientName,
                    amount,
                    ledger,
                    TreasuryLedgerType.GRANT,
                    treasuryUuid,
                    details)) {
                return null;
            }
            if (plugin instanceof RootEconomyPlugin essentials) {
                return essentials.depositIncome(recipientUuid, recipientName, amount);
            }
            economy.deposit(recipientUuid, recipientName, amount);
            return RootMcIncomeSweepResult.allToWallet(amount);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Treasury try payout failed: " + ex.getMessage(), ex);
            return null;
        }
    }

    @Override
    public boolean disburseLoan(UUID borrowerUuid, String borrowerName, double principal) {
        if (principal <= 0 || borrowerUuid == null) {
            return false;
        }
        try {
            return economy.grantFromTreasury(
                    treasuryUuid,
                    treasuryUsername,
                    borrowerUuid,
                    borrowerName,
                    principal,
                    ledger,
                    TreasuryLedgerType.LOAN_DISBURSE,
                    treasuryUuid,
                    "personal-loan");
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Treasury loan disburse failed: " + ex.getMessage(), ex);
            return false;
        }
    }

    @Override
    public boolean disburseTownLoan(String townName, UUID mayorUuid, double principal) {
        if (principal <= 0 || townName == null || townName.isBlank()) {
            return false;
        }
        try {
            var bank = com.rootrecord.minecraft.rootessentials.towny.TownyEconomyAccounts
                    .townBankByName(townName.trim())
                    .orElse(null);
            if (bank == null) {
                return false;
            }
            UUID bankUuid = bank.uuid();
            String bankName = bank.username();
            return economy.grantFromTreasury(
                    treasuryUuid,
                    treasuryUsername,
                    bankUuid,
                    bankName,
                    principal,
                    ledger,
                    TreasuryLedgerType.LOAN_DISBURSE,
                    mayorUuid == null ? treasuryUuid : mayorUuid,
                    "town-loan:" + townName.trim());
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Treasury town loan disburse failed: " + ex.getMessage(), ex);
            return false;
        }
    }

    @Override
    public boolean receiveTownLoanRepayment(
            String townName,
            UUID mayorUuid,
            double principalPart,
            double interestPart) {
        if (townName == null || townName.isBlank()) {
            return false;
        }
        try {
            var bank = com.rootrecord.minecraft.rootessentials.towny.TownyEconomyAccounts
                    .townBankByName(townName.trim())
                    .orElse(null);
            if (bank == null) {
                return false;
            }
            UUID bankUuid = bank.uuid();
            String bankName = bank.username();
            boolean ok = economy.applyLoanRepayment(
                    bankUuid,
                    bankName,
                    treasuryUuid,
                    treasuryUsername,
                    principalPart,
                    interestPart,
                    ledger,
                    true);
            return ok;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Treasury town loan repayment failed: " + ex.getMessage(), ex);
            return false;
        }
    }

    @Override
    public boolean receiveLoanRepayment(
            UUID payerUuid,
            String payerName,
            double principalPart,
            double interestPart,
            boolean withdrawFromPayerWallet) {
        if (payerUuid == null) {
            return false;
        }
        try {
            boolean ok = economy.applyLoanRepayment(
                    payerUuid,
                    payerName,
                    treasuryUuid,
                    treasuryUsername,
                    principalPart,
                    interestPart,
                    ledger,
                    withdrawFromPayerWallet);
            return ok;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Treasury loan repayment failed: " + ex.getMessage(), ex);
            return false;
        }
    }

    @Override
    public boolean payDividend(UUID recipientUuid, String recipientName, double amount, String monthKey) {
        if (amount <= 0 || recipientUuid == null) {
            return false;
        }
        try {
            return economy.grantFromTreasury(
                    treasuryUuid,
                    treasuryUsername,
                    recipientUuid,
                    recipientName,
                    amount,
                    ledger,
                    TreasuryLedgerType.DIVIDEND,
                    treasuryUuid,
                    "month=" + (monthKey == null ? "" : monthKey));
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Treasury dividend failed: " + ex.getMessage(), ex);
            return false;
        }
    }

    @Override
    public boolean payBondCouponWallet(UUID recipientUuid, String recipientName, double amount, String details) {
        if (amount <= 0 || recipientUuid == null) {
            return false;
        }
        try {
            return economy.grantFromTreasury(
                    treasuryUuid,
                    treasuryUsername,
                    recipientUuid,
                    recipientName,
                    amount,
                    ledger,
                    TreasuryLedgerType.BOND_COUPON,
                    treasuryUuid,
                    details == null ? "bond-coupon" : details);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Treasury bond coupon wallet payout failed: " + ex.getMessage(), ex);
            return false;
        }
    }

    @Override
    public boolean payBondRedeemWallet(UUID recipientUuid, String recipientName, double amount, String details) {
        if (amount <= 0 || recipientUuid == null) {
            return false;
        }
        try {
            return economy.grantFromTreasury(
                    treasuryUuid,
                    treasuryUsername,
                    recipientUuid,
                    recipientName,
                    amount,
                    ledger,
                    TreasuryLedgerType.BOND_REDEEM,
                    treasuryUuid,
                    details == null ? "bond-expiry-redeem" : details);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Treasury bond redeem wallet payout failed: " + ex.getMessage(), ex);
            return false;
        }
    }

    @Override
    public boolean debitTreasuryPhysical(
            double amount,
            TreasuryLedgerType type,
            UUID playerUuid,
            String playerName,
            String details) {
        if (amount <= 0 || playerUuid == null) {
            return false;
        }
        try {
            return economy.debitTreasuryPhysical(
                    treasuryUuid,
                    treasuryUsername,
                    playerUuid,
                    playerName,
                    amount,
                    ledger,
                    type,
                    details);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Treasury physical debit failed: " + ex.getMessage(), ex);
            return false;
        }
    }

    @Override
    public double treasuryBalance() {
        try {
            return economy.balance(treasuryUuid, treasuryUsername);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Treasury balance read failed: " + ex.getMessage());
            return 0;
        }
    }

    /** Headline Server Reserve (opening + ledger net âˆ’ shortfall settled), floored at 0. */
    @Override
    public double headlineReserveBalance() {
        try {
            return EconomyBaseline.headlineReserveBalance(
                    ledger.totalsAllTime().net(),
                    ledger.totalOverIssueShortfallRepaid());
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Headline reserve read failed: " + ex.getMessage());
            return treasuryBalance();
        }
    }

    /** Gross reserve ledger (opening + post-reset net) â€” can be negative; matches rootmc.net/reserve. */
    public double grossReserveBalance() {
        try {
            return EconomyBaseline.trueReserveBalance(ledger.totalsAllTime().net());
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Gross reserve read failed: " + ex.getMessage());
            return treasuryBalance();
        }
    }

    /** Audit trail when /mint converts physical gold to Notes at 0% tax (no separate TAX row). */
    public void recordMintGrossAudit(UUID playerUuid, double grossG) {
        if (grossG <= 0 || playerUuid == null) {
            return;
        }
        try {
            ledger.insertMintTrail(
                    playerUuid,
                    String.format(java.util.Locale.US, "mint:gross=%.3f", grossG));
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Mint gross audit failed: " + ex.getMessage());
        }
    }

    /** /mint gold ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â burns wallet Notes and logs mint:redeem (reduces net /mint backing). */
    public boolean acceptMintRedemption(UUID playerUuid, String playerName, double grossG) {
        if (grossG <= 0 || playerUuid == null) {
            return false;
        }
        try {
            boolean ok = economy.burnMintRedemption(playerUuid, playerName, grossG, ledger);
            if (ok) {
                invalidateNoteSupplyCache();
            }
            return ok;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Mint redemption burn failed: " + ex.getMessage(), ex);
            return false;
        }
    }

    /** Voluntary /pay reserve â€” credits Server Reserve vault + DONATION ledger inflow. */
    public boolean acceptDonation(UUID donorUuid, String donorName, double amount) {
        if (amount <= 0 || donorUuid == null) {
            return false;
        }
        try {
            boolean ok = economy.creditReserveDonation(
                    donorUuid, donorName, amount, treasuryUuid, treasuryUsername, ledger);
            if (ok) {
                invalidateNoteSupplyCache();
            }
            return ok;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Reserve donation failed: " + ex.getMessage(), ex);
            return false;
        }
    }

    @Override
    public java.util.List<TreasuryLedgerEntry> ledgerEntriesAfter(long afterMysqlId, int limit) {
        try {
            return ledger.listAfterId(afterMysqlId, limit).stream()
                    .map(row -> {
                        TreasuryLedgerType type;
                        try {
                            type = TreasuryLedgerType.valueOf(row.entryType());
                        } catch (IllegalArgumentException ex) {
                            type = TreasuryLedgerType.OTHER;
                        }
                        return new TreasuryLedgerEntry(
                                row.id(),
                                type,
                                row.amount(),
                                row.fromUuid(),
                                row.toUuid(),
                                row.details(),
                                row.createdAt() == null ? null : row.createdAt().toInstant().toString());
                    })
                    .toList();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Treasury ledger read failed: " + ex.getMessage());
            return java.util.List.of();
        }
    }

    @Override
    public UUID treasuryUuid() {
        return treasuryUuid;
    }

    @Override
    public String treasuryUsername() {
        return treasuryUsername;
    }

    private double computeTax(UUID payerUuid, double gross) {
        return computeTaxAmount(payerUuid, gross);
    }

    /** Collapse duplicate Towny Vault callbacks within ~3s (same payer, gross, channel). */
    private boolean isDuplicateSettlement(UUID payerUuid, double gross, String channel) {
        String payer = payerUuid == null ? "anon" : payerUuid.toString();
        String ch = channel == null || channel.isBlank() ? "towny:other" : channel.trim();
        long bucket = System.currentTimeMillis() / SETTLEMENT_DEBOUNCE_MS;
        String key = payer + ":" + String.format(Locale.US, "%.3f", gross) + ":" + ch + ":" + bucket;
        Long prior = recentSettlements.putIfAbsent(key, bucket);
        if (prior != null) {
            return true;
        }
        if (recentSettlements.size() > 512) {
            recentSettlements.entrySet().removeIf(e -> e.getValue() < bucket - 2);
        }
        return false;
    }
}
