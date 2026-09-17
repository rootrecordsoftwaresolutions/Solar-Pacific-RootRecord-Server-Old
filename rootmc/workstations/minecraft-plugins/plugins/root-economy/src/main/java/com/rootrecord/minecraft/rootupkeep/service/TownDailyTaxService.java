package com.rootrecord.minecraft.rootupkeep.service;

import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.common.RootMcTreasuryResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.common.TreasuryLedgerType;
import com.rootrecord.minecraft.rootessentials.towny.TownyEconomyAccounts;
import com.rootrecord.minecraft.rootupkeep.RootUpkeepPlugin;
import com.rootrecord.minecraft.rootupkeep.config.UpkeepConfig;
import com.rootrecord.minecraft.rootupkeep.data.UpkeepStateStore;
import com.rootrecord.minecraft.rootupkeep.towny.TownyGroups;
import com.rootrecord.minecraft.rootupkeep.towny.TownyReflection;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Debits each town bank a flat percent of balance once per Minecraft day → Server Reserve. */
public final class TownDailyTaxService {

    public record TaxResult(String townName, UUID bankUuid, String bankName, double amount, double balanceBefore) {}

    private final RootUpkeepPlugin plugin;
    private final UpkeepStateStore state;
    private final AtomicBoolean running = new AtomicBoolean();
    private final DecimalFormat money = new DecimalFormat("0.###");
    private final DecimalFormat rate = new DecimalFormat("0.##");

    public TownDailyTaxService(RootUpkeepPlugin plugin, UpkeepStateStore state) {
        this.plugin = plugin;
        this.state = state;
    }

    public void processMcDayRollover(long firstCompletedDay, long currentMcDayId, Runnable onComplete) {
        UpkeepConfig cfg = plugin.config();
        if (cfg == null || !cfg.enabled() || !cfg.townDailyTaxEnabled() || cfg.townDailyTaxRatePercent() <= 0) {
            onComplete.run();
            return;
        }
        if (!TownyReflection.isAvailable()) {
            onComplete.run();
            return;
        }
        if (firstCompletedDay >= currentMcDayId) {
            onComplete.run();
            return;
        }
        long last = state.lastTownTaxMcDayId();
        long firstDue = Math.max(firstCompletedDay, last + 1);
        long lastDue = currentMcDayId - 1;
        if (firstDue > lastDue) {
            onComplete.run();
            return;
        }
        long span = lastDue - firstDue + 1;
        int maxCatchUp = cfg.townDailyTaxCatchUpMaxMcDays();
        if (span > maxCatchUp) {
            firstDue = lastDue - maxCatchUp + 1;
        }
        processDay(firstDue, lastDue, onComplete);
    }

    private void processDay(long mcDayId, long lastDue, Runnable onComplete) {
        if (mcDayId > lastDue) {
            onComplete.run();
            return;
        }
        if (!running.compareAndSet(false, true)) {
            onComplete.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin.host(), () -> {
            try {
                List<TaxResult> applied = applyAllTowns();
                state.markTownTaxMcDay(mcDayId);
                if (!applied.isEmpty()) {
                    plugin.getLogger().info(
                            "Town daily tax MC day " + mcDayId + ": " + applied.size() + " town(s), total "
                                    + money.format(sum(applied)) + " G.");
                    notifyMayors(applied);
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Town daily tax failed for MC day " + mcDayId + ": " + ex.getMessage());
            } finally {
                running.set(false);
                if (mcDayId + 1 <= lastDue) {
                    processDay(mcDayId + 1, lastDue, onComplete);
                } else {
                    onComplete.run();
                }
            }
        });
    }

    private List<TaxResult> applyAllTowns() {
        UpkeepConfig cfg = plugin.config();
        RootMcEconomyService economy = RootMcEconomyResolver.resolve(plugin.host());
        RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(plugin.host());
        List<TaxResult> applied = new ArrayList<>();
        if (economy == null || treasury == null) {
            return applied;
        }
        double rateFraction = cfg.townDailyTaxRatePercent() / 100.0;
        for (TownyGroups.NamedGroup town : TownyGroups.allTowns()) {
            TaxResult result = taxTown(economy, treasury, town.name(), rateFraction, cfg);
            if (result != null) {
                applied.add(result);
            }
        }
        return applied;
    }

    private TaxResult taxTown(
            RootMcEconomyService economy,
            RootMcTreasuryService treasury,
            String townName,
            double rateFraction,
            UpkeepConfig cfg) {
        var bankOpt = TownyEconomyAccounts.townBankByName(townName);
        if (bankOpt.isEmpty()) {
            return null;
        }
        TownyEconomyAccounts.VaultAccount bank = bankOpt.get();
        double balance = GoldMoney.round(economy.balance(bank.uuid(), bank.username()));
        if (balance < cfg.townDailyTaxMinBalance()) {
            return null;
        }
        double tax = GoldMoney.round(balance * rateFraction);
        if (tax < cfg.townDailyTaxMinTax()) {
            return null;
        }
        tax = Math.min(tax, balance);
        if (!economy.withdrawAccount(bank.uuid(), bank.username(), tax)) {
            return null;
        }
        treasury.creditTreasury(
                tax,
                TreasuryLedgerType.TAX,
                bank.uuid(),
                bank.username(),
                "town-daily-tax:" + townName);
        // TAX is bond-eligible: BondIncomeService accumulates into the current MC day pool;
        // the 25% coupon share compounds to holders when that day settles (next rollover).
        return new TaxResult(townName, bank.uuid(), bank.username(), tax, balance);
    }

    private void notifyMayors(List<TaxResult> applied) {
        UpkeepConfig cfg = plugin.config();
        String template = cfg.townDailyTaxMessage();
        String rateLabel = rate.format(cfg.townDailyTaxRatePercent());
        for (TaxResult result : applied) {
            String msg = ChatColor.translateAlternateColorCodes(
                    '&',
                    template
                            .replace("{town}", result.townName())
                            .replace("{amount}", money.format(result.amount()))
                            .replace("{rate}", rateLabel)
                            .replace("{balance}", money.format(result.balanceBefore())));
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (isMayorOf(online, result.townName())) {
                    online.sendMessage(msg);
                    break;
                }
            }
        }
    }

    private static boolean isMayorOf(Player player, String townName) {
        Object resident = TownyReflection.resident(player);
        Object town = TownyReflection.invokeNoArg(resident, "getTownOrNull", "getTown");
        if (town == null) {
            return false;
        }
        String name = TownyReflection.stringOrNull(TownyReflection.invokeNoArg(town, "getName"));
        if (name == null || !name.equalsIgnoreCase(townName)) {
            return false;
        }
        UUID mayor = TownyReflection.uuidOrNull(TownyReflection.invokeNoArg(town, "getMayor"));
        return mayor != null && mayor.equals(player.getUniqueId());
    }

    private static double sum(List<TaxResult> results) {
        double total = 0;
        for (TaxResult result : results) {
            total += result.amount();
        }
        return GoldMoney.round(total);
    }
}
