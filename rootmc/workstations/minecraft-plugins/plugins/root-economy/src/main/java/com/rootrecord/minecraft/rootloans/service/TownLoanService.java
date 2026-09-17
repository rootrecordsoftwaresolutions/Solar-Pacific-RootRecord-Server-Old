package com.rootrecord.minecraft.rootloans.service;


import com.rootrecord.minecraft.common.GoldMoney;

import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.rootloans.RootLoansPlugin;
import com.rootrecord.minecraft.rootloans.config.TownLoansConfig;
import com.rootrecord.minecraft.rootloans.data.TownLoansStore;
import com.rootrecord.minecraft.rootloans.town.TownyAccess;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public final class TownLoanService {

    private final RootLoansPlugin plugin;
    private TownLoansConfig config;
    private TownLoansStore store;
    private RootMcTreasuryService treasury;

    public TownLoanService(RootLoansPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload(TownLoansConfig config, TownLoansStore store, RootMcTreasuryService treasury) {
        this.config = config;
        this.store = store;
        this.treasury = treasury;
    }

    public boolean enabled() {
        return config != null && config.enabled() && store != null && treasury != null && TownyAccess.isAvailable();
    }

    public TakeResult takeLoan(Player mayor, double principal) {
        if (!enabled()) {
            return TakeResult.fail("town-disabled");
        }
        if (principal <= 0) {
            return TakeResult.fail("invalid-amount");
        }
        if (principal > config.maxLoan() + 0.0001d) {
            return TakeResult.overLimit(config.maxLoan());
        }
        Optional<String> townOpt = TownyAccess.playerTownName(mayor);
        if (townOpt.isEmpty()) {
            return TakeResult.fail("town-no-town");
        }
        String town = townOpt.get();
        if (!TownyAccess.isMayorOf(mayor, town)) {
            return TakeResult.fail("town-not-mayor");
        }
        try {
            if (store.findByTown(town).isPresent()) {
                return TakeResult.fail("town-active-loan");
            }
            if (!treasury.disburseTownLoan(town, mayor.getUniqueId(), principal)) {
                return TakeResult.fail("town-treasury-empty");
            }
            double owed = roundMoney(principal * (1.0 + config.interestRate()));
            try {
                store.createLoan(town, mayor.getUniqueId(), mayor.getName(), principal, owed);
            } catch (Exception createEx) {
                treasury.receiveTownLoanRepayment(town, mayor.getUniqueId(), principal, 0);
                throw createEx;
            }
            return TakeResult.success(town, principal, owed);
        } catch (Exception ex) {
            plugin.getLogger().warning("Town loan take failed for " + town + ": " + ex.getMessage());
            return TakeResult.fail("town-take-failed");
        }
    }

    public ManualRepayResult manualRepay(Player mayor, double requested) {
        if (!enabled()) {
            return ManualRepayResult.error("town-disabled");
        }
        Optional<String> townOpt = TownyAccess.playerTownName(mayor);
        if (townOpt.isEmpty()) {
            return ManualRepayResult.error("town-no-town");
        }
        String town = townOpt.get();
        if (!TownyAccess.isMayorOf(mayor, town)) {
            return ManualRepayResult.error("town-not-mayor");
        }
        try {
            Optional<TownLoansStore.ActiveTownLoan> active = store.findByTown(town);
            if (active.isEmpty()) {
                return ManualRepayResult.error("town-no-loan");
            }
            TownLoansStore.ActiveTownLoan loan = active.get();
            double owed = loan.amountOwed();
            if (owed <= 0) {
                return ManualRepayResult.error("town-repay-none");
            }
            double amount = requested > 0 ? Math.min(requested, owed) : owed;
            RepaymentSplit split = splitRepayment(loan, amount);
            double total = split.principal() + split.interest();
            RootMcEconomyService economy = RootMcEconomyResolver.resolve(plugin.host());
            String bankName = TownyAccess.townBankAccountName(town);
            UUID bankUuid = Bukkit.getOfflinePlayer(bankName).getUniqueId();
            double bankBalance = economy != null ? Math.max(0, economy.balance(bankUuid)) : 0;
            if (bankBalance + 0.0001d < total) {
                return ManualRepayResult.insufficient(bankBalance, total);
            }
            if (!treasury.receiveTownLoanRepayment(town, mayor.getUniqueId(), split.principal(), split.interest())) {
                return ManualRepayResult.error("town-repay-failed");
            }
            TownLoansStore.RepayResult result = store.repay(town, mayor.getName(), amount);
            if (result.applied() <= 0) {
                return ManualRepayResult.error("town-repay-none");
            }
            double remaining = store.findByTown(town).map(TownLoansStore.ActiveTownLoan::amountOwed).orElse(0.0);
            return ManualRepayResult.ok(town, result.applied(), remaining, result.payoff());
        } catch (Exception ex) {
            plugin.getLogger().warning("Town loan repay failed for " + town + ": " + ex.getMessage());
            return ManualRepayResult.error("town-repay-failed");
        }
    }

    public Optional<TownLoansStore.ActiveTownLoan> activeLoanForMayor(Player mayor) {
        if (!enabled() || mayor == null) {
            return Optional.empty();
        }
        return TownyAccess.playerTownName(mayor)
                .flatMap(town -> {
                    if (!TownyAccess.isMayorOf(mayor, town)) {
                        return Optional.empty();
                    }
                    try {
                        return store.findByTown(town);
                    } catch (Exception ex) {
                        return Optional.empty();
                    }
                });
    }

    static RepaymentSplit splitRepayment(TownLoansStore.ActiveTownLoan loan, double applied) {
        double payment = Math.max(0, Math.min(applied, loan.amountOwed()));
        double principalOutstanding = Math.min(loan.principal(), loan.amountOwed());
        double interestOutstanding = Math.max(0, loan.amountOwed() - principalOutstanding);
        double interestPaid = Math.min(payment, interestOutstanding);
        double principalPaid = payment - interestPaid;
        return new RepaymentSplit(roundMoney(principalPaid), roundMoney(interestPaid));
    }

    private static double roundMoney(double value) {
        return GoldMoney.round(value);
    }

    record RepaymentSplit(double principal, double interest) {}

    public record TakeResult(
            boolean ok,
            String messageKey,
            String townName,
            double principal,
            double owed,
            double maxLoan) {

        static TakeResult success(String town, double principal, double owed) {
            return new TakeResult(true, "town-take-success", town, principal, owed, 0);
        }

        static TakeResult fail(String key) {
            return new TakeResult(false, key, null, 0, 0, 0);
        }

        static TakeResult overLimit(double max) {
            return new TakeResult(false, "town-over-limit", null, 0, 0, max);
        }
    }

    public record ManualRepayResult(
            boolean ok,
            String messageKey,
            String townName,
            double applied,
            double remaining,
            double balance,
            double needed,
            Optional<TownLoansStore.PayoffResult> payoff) {

        static ManualRepayResult ok(
                String town,
                double applied,
                double remaining,
                Optional<TownLoansStore.PayoffResult> payoff) {
            return new ManualRepayResult(true, "town-repay-success", town, applied, remaining, 0, 0, payoff);
        }

        static ManualRepayResult insufficient(double balance, double needed) {
            return new ManualRepayResult(false, "town-bank-insufficient", null, 0, 0, balance, needed, Optional.empty());
        }

        static ManualRepayResult error(String key) {
            return new ManualRepayResult(false, key, null, 0, 0, 0, 0, Optional.empty());
        }
    }
}
