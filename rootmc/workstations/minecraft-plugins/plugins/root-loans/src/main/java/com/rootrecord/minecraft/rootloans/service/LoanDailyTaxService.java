package com.rootrecord.minecraft.rootloans.service;


import com.rootrecord.minecraft.common.GoldMoney;

import com.rootrecord.minecraft.rootloans.RootLoansPlugin;
import com.rootrecord.minecraft.rootloans.config.LoanDailyTaxConfig;
import com.rootrecord.minecraft.rootloans.data.LoansStore;
import com.rootrecord.minecraft.rootloans.data.TownLoansStore;
import com.rootrecord.minecraft.rootloans.town.TownyAccess;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LoanDailyTaxService {

    private final RootLoansPlugin plugin;
    private LoanDailyTaxConfig config;

    public LoanDailyTaxService(RootLoansPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload(LoanDailyTaxConfig config) {
        this.config = config;
    }

    public boolean enabled() {
        return config != null && config.enabled() && plugin.store() != null && plugin.townLoansStore() != null;
    }

    public List<TaxResult> accrueAllDue() throws Exception {
        List<TaxResult> results = new ArrayList<>();
        if (!enabled() || config.rate() <= 0) {
            return results;
        }
        LoansStore personal = plugin.store();
        for (LoansStore.ActiveLoan loan : personal.listActive()) {
            if (loan.amountOwed() <= 0) {
                continue;
            }
            double tax = taxAmount(loan.amountOwed());
            if (tax <= 0) {
                continue;
            }
            personal.accrueDailyTax(loan.id(), tax);
            results.add(new TaxResult(
                    Kind.PERSONAL,
                    loan.username(),
                    loan.uuid(),
                    null,
                    tax,
                    loan.amountOwed() + tax));
        }
        TownLoansStore townStore = plugin.townLoansStore();
        for (TownLoansStore.ActiveTownLoan loan : townStore.listActive()) {
            if (loan.amountOwed() <= 0) {
                continue;
            }
            double tax = taxAmount(loan.amountOwed());
            if (tax <= 0) {
                continue;
            }
            townStore.accrueDailyTax(loan.townName(), tax);
            results.add(new TaxResult(
                    Kind.TOWN,
                    loan.townName(),
                    loan.mayorUuid(),
                    loan.townName(),
                    tax,
                    loan.amountOwed() + tax));
        }
        return results;
    }

    public void notifyApplied(List<TaxResult> results) {
        for (TaxResult result : results) {
            if (result.kind() == Kind.PERSONAL) {
                Player player = Bukkit.getPlayer(result.subjectUuid());
                if (player != null && player.isOnline()) {
                    player.sendMessage(plugin.msg("loan-daily-tax-player")
                            .replace("{amount}", plugin.money(result.tax()))
                            .replace("{owed}", plugin.money(result.newOwed()))
                            .replace("{rate}", formatRatePercent()));
                }
                continue;
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (TownyAccess.isMayorOf(online, result.townName())) {
                    online.sendMessage(plugin.msg("loan-daily-tax-town")
                            .replace("{town}", result.townName())
                            .replace("{amount}", plugin.money(result.tax()))
                            .replace("{owed}", plugin.money(result.newOwed()))
                            .replace("{rate}", formatRatePercent()));
                    break;
                }
            }
        }
    }

    private double taxAmount(double outstanding) {
        double tax = roundMoney(outstanding * config.rate());
        if (tax < config.minAccrual()) {
            return 0;
        }
        return tax;
    }

    private String formatRatePercent() {
        return String.valueOf(Math.round(config.rate() * 10000.0) / 100.0);
    }

    private static double roundMoney(double value) {
        return GoldMoney.round(value);
    }

    public enum Kind {
        PERSONAL,
        TOWN
    }

    public record TaxResult(
            Kind kind,
            String label,
            UUID subjectUuid,
            String townName,
            double tax,
            double newOwed) {}
}
