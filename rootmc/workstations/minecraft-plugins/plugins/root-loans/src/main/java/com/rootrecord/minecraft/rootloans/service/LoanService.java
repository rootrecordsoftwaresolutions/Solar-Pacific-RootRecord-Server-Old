package com.rootrecord.minecraft.rootloans.service;

import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.common.RootMcIncomeSweepResult;
import com.rootrecord.minecraft.common.RootMcLoanService;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.rootloans.RootLoansPlugin;
import com.rootrecord.minecraft.rootloans.config.LoansConfig;
import com.rootrecord.minecraft.rootloans.data.LoansStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class LoanService implements RootMcLoanService {

    private final RootLoansPlugin plugin;
    private LoansConfig config;
    private LoansStore store;
    private RootMcTreasuryService treasury;
    private RankLoanLimitService rankLimits;

    public LoanService(RootLoansPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload(
            LoansConfig config,
            LoansStore store,
            RootMcTreasuryService treasury,
            RankLoanLimitService rankLimits) {
        this.config = config;
        this.store = store;
        this.treasury = treasury;
        this.rankLimits = rankLimits;
    }

    private double borrowLimit(UUID uuid) {
        double rankCap = rankLimits != null ? rankLimits.limitFor(uuid) : config.defaultMaxLoan();
        String mode = config != null && config.maxCapMode() != null
                ? config.maxCapMode()
                : "rank";
        RootMcEconomyService economy = RootMcEconomyResolver.resolve(plugin);
        double wallet = 0;
        if (economy != null) {
            try {
                wallet = Math.max(0, economy.balance(uuid));
            } catch (Exception ignored) {
                wallet = 0;
            }
        }
        return switch (mode) {
            case "balance" -> wallet;
            case "min_both" -> Math.min(rankCap, wallet);
            default -> rankCap;
        };
    }

    public double borrowLimitFor(UUID uuid) {
        return borrowLimit(uuid);
    }

    public boolean enabled() {
        return config != null && config.enabled() && store != null && treasury != null;
    }

    @Override
    public RootMcIncomeSweepResult applyIncome(UUID uuid, String username, double grossIncome) {
        if (!enabled() || grossIncome <= 0) {
            return RootMcIncomeSweepResult.allToWallet(grossIncome);
        }
        try {
            List<LoansStore.ActiveLoan> loans = store.findActiveAll(uuid);
            if (loans.isEmpty()) {
                return RootMcIncomeSweepResult.allToWallet(grossIncome);
            }
            double totalOwed = loans.stream().mapToDouble(LoansStore.ActiveLoan::amountOwed).sum();
            double sweepAmount = Math.min(grossIncome * config.incomeSweepPercent(), totalOwed);
            if (sweepAmount <= 0) {
                return RootMcIncomeSweepResult.allToWallet(grossIncome);
            }
            RepaymentSplit split = splitAcrossLoans(loans, sweepAmount);
            if (!treasury.receiveLoanRepayment(uuid, username, split.principal(), split.interest(), false)) {
                return RootMcIncomeSweepResult.allToWallet(grossIncome);
            }
            LoansStore.RepayResult result = store.repay(uuid, username, sweepAmount);
            if (result.applied() <= 0) {
                return RootMcIncomeSweepResult.allToWallet(grossIncome);
            }
            double toWallet = grossIncome - result.applied();
            notifyIncomeSweep(uuid, result.applied(), result.remainingTotal(), result.payoff());
            return new RootMcIncomeSweepResult(Math.max(0, toWallet), result.applied());
        } catch (Exception ex) {
            plugin.getLogger().warning("Loan income sweep failed for " + uuid + ": " + ex.getMessage());
            return RootMcIncomeSweepResult.allToWallet(grossIncome);
        }
    }

    public void applyOreIncome(Player player, double goldValue) {
        if (!enabled() || !config.goldOreRepayment() || goldValue <= 0) {
            return;
        }
        try {
            List<LoansStore.ActiveLoan> loans = store.findActiveAll(player.getUniqueId());
            if (loans.isEmpty()) {
                return;
            }
            double totalOwed = loans.stream().mapToDouble(LoansStore.ActiveLoan::amountOwed).sum();
            double appliedWanted = Math.min(goldValue * config.incomeSweepPercent(), totalOwed);
            if (appliedWanted <= 0) {
                return;
            }
            RepaymentSplit split = splitAcrossLoans(loans, appliedWanted);
            if (!treasury.receiveLoanRepayment(
                    player.getUniqueId(), player.getName(), split.principal(), split.interest(), false)) {
                return;
            }
            LoansStore.RepayResult result = store.repay(player.getUniqueId(), player.getName(), appliedWanted);
            if (result.applied() > 0) {
                player.sendMessage(plugin.msg("ore-sweep").replace("{amount}", plugin.money(result.applied())));
                result.payoff().ifPresent(payoff ->
                        player.sendMessage(plugin.msg("paid-off")
                                .replace("{max}", plugin.money(borrowLimit(player.getUniqueId())))));
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Ore loan sweep failed for " + player.getName() + ": " + ex.getMessage());
        }
    }

    public TakeResult takeLoan(Player player, double principal) {
        if (!enabled()) {
            return TakeResult.fail("disabled");
        }
        if (principal <= 0) {
            return TakeResult.fail("invalid-amount");
        }
        UUID uuid = player.getUniqueId();
        try {
            int activeCount = store.countActive(uuid);
            if (activeCount >= config.maxConcurrentLoans()) {
                return TakeResult.fail("max-loans");
            }
            int takes = store.countTakesInRolling24h(uuid);
            if (takes >= config.maxTakesPer24h()) {
                long waitMs = store.oldestTakeInRolling24h(uuid)
                        .map(t -> Duration.between(Instant.now(), t.plus(24, java.time.temporal.ChronoUnit.HOURS)).toMillis())
                        .orElse(0L);
                return TakeResult.rateLimited(waitMs);
            }
            double maxLoan = borrowLimit(uuid);
            double totalOwed = store.sumOwed(uuid);
            double headroom = Math.max(0, maxLoan - totalOwed);
            if (principal > headroom + 0.0001d) {
                return TakeResult.overLimit(headroom);
            }
            if (!treasury.disburseLoan(uuid, player.getName(), principal)) {
                return TakeResult.fail("treasury-empty");
            }
            double owed = principal * (1.0 + config.interestRate());
            try {
                store.createLoan(uuid, player.getName(), principal, owed);
            } catch (Exception createEx) {
                treasury.receiveLoanRepayment(uuid, player.getName(), principal, 0, true);
                throw createEx;
            }
            return TakeResult.success(principal, owed);
        } catch (Exception ex) {
            plugin.getLogger().warning("Loan take failed for " + player.getName() + ": " + ex.getMessage());
            return TakeResult.fail("take-failed");
        }
    }

    public ManualRepayResult manualRepay(Player player, double requested) {
        if (!enabled()) {
            return ManualRepayResult.error("disabled");
        }
        try {
            List<LoansStore.ActiveLoan> loans = store.findActiveAll(player.getUniqueId());
            if (loans.isEmpty()) {
                return ManualRepayResult.error("no-loan");
            }
            double totalOwed = loans.stream().mapToDouble(LoansStore.ActiveLoan::amountOwed).sum();
            if (totalOwed <= 0) {
                return ManualRepayResult.error("repay-none");
            }
            double amount = requested > 0 ? Math.min(requested, totalOwed) : totalOwed;
            RepaymentSplit split = splitAcrossLoans(loans, amount);
            double total = split.principal() + split.interest();
            if (!treasury.receiveLoanRepayment(
                    player.getUniqueId(), player.getName(), split.principal(), split.interest(), true)) {
                RootMcEconomyService economy = plugin.resolveEconomy();
                double balance = economy != null ? economy.balance(player.getUniqueId()) : 0;
                return ManualRepayResult.insufficient(balance, total);
            }
            LoansStore.RepayResult result = store.repay(player.getUniqueId(), player.getName(), amount);
            if (result.applied() <= 0) {
                return ManualRepayResult.error("repay-none");
            }
            return ManualRepayResult.ok(result.applied(), result.remainingTotal(), result.payoff());
        } catch (Exception ex) {
            plugin.getLogger().warning("Manual repay failed for " + player.getName() + ": " + ex.getMessage());
            return ManualRepayResult.error("take-failed");
        }
    }

    @Override
    public Optional<LoanBalanceSummary> balanceSummary(UUID uuid) {
        if (!enabled()) {
            return Optional.empty();
        }
        try {
            double maxLoan = borrowLimit(uuid);
            int takes = store.countTakesInRolling24h(uuid);
            long waitMs = 0;
            if (takes >= config.maxTakesPer24h()) {
                waitMs = store.oldestTakeInRolling24h(uuid)
                        .map(t -> Math.max(0, Duration.between(Instant.now(), t.plus(24, java.time.temporal.ChronoUnit.HOURS)).toMillis()))
                        .orElse(0L);
            }
            double owed = store.sumOwed(uuid);
            return Optional.of(new LoanBalanceSummary(
                    owed, maxLoan, takes, config.maxTakesPer24h(), waitMs));
        } catch (Exception ex) {
            plugin.getLogger().warning("Loan summary failed for " + uuid + ": " + ex.getMessage());
            return Optional.empty();
        }
    }

    /** Highest-balance loan first (same order as store.repay). */
    static RepaymentSplit splitAcrossLoans(List<LoansStore.ActiveLoan> loans, double payment) {
        double remaining = Math.max(0, payment);
        double principalPaid = 0;
        double interestPaid = 0;
        for (LoansStore.ActiveLoan loan : loans) {
            if (remaining <= 0.0001d) {
                break;
            }
            double chunk = Math.min(remaining, loan.amountOwed());
            if (chunk <= 0) {
                continue;
            }
            RepaymentSplit part = splitRepayment(loan, chunk);
            principalPaid += part.principal();
            interestPaid += part.interest();
            remaining -= chunk;
        }
        return new RepaymentSplit(roundMoney(principalPaid), roundMoney(interestPaid));
    }

    static RepaymentSplit splitRepayment(LoansStore.ActiveLoan loan, double applied) {
        double payment = Math.max(0, Math.min(applied, loan.amountOwed()));
        double principalOutstanding = Math.min(loan.principal(), loan.amountOwed());
        double interestOutstanding = Math.max(0, loan.amountOwed() - principalOutstanding);
        double interestPart = Math.min(payment, interestOutstanding);
        double principalPart = payment - interestPart;
        return new RepaymentSplit(roundMoney(principalPart), roundMoney(interestPart));
    }

    private static double roundMoney(double value) {
        return GoldMoney.round(value);
    }

    private void notifyIncomeSweep(UUID uuid, double applied, double remainingAfter, Optional<LoansStore.PayoffResult> payoff) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        if (payoff.isPresent()) {
            player.sendMessage(plugin.msg("paid-off")
                    .replace("{max}", plugin.money(borrowLimit(uuid))));
            return;
        }
        if (remainingAfter <= 0.0001d) {
            return;
        }
        player.sendMessage(plugin.msg("income-sweep")
                .replace("{amount}", plugin.money(applied))
                .replace("{owed}", plugin.money(Math.max(0, remainingAfter))));
    }

    record RepaymentSplit(double principal, double interest) {}

    public record TakeResult(
            boolean ok,
            String messageKey,
            double principal,
            double owed,
            double maxLoan,
            long waitMs) {

        static TakeResult success(double principal, double owed) {
            return new TakeResult(true, "take-success", principal, owed, 0, 0);
        }

        static TakeResult fail(String key) {
            return new TakeResult(false, key, 0, 0, 0, 0);
        }

        static TakeResult overLimit(double max) {
            return new TakeResult(false, "over-limit", 0, 0, max, 0);
        }

        static TakeResult rateLimited(long waitMs) {
            return new TakeResult(false, "rate-limited", 0, 0, 0, waitMs);
        }
    }

    public record ManualRepayResult(
            boolean ok,
            String messageKey,
            double applied,
            double remaining,
            double balance,
            double needed,
            Optional<LoansStore.PayoffResult> payoff) {

        static ManualRepayResult ok(double applied, double remaining, Optional<LoansStore.PayoffResult> payoff) {
            return new ManualRepayResult(true, "repay-success", applied, remaining, 0, 0, payoff);
        }

        static ManualRepayResult insufficient(double balance, double needed) {
            return new ManualRepayResult(false, "repay-insufficient", 0, 0, balance, needed, Optional.empty());
        }

        static ManualRepayResult error(String key) {
            return new ManualRepayResult(false, key, 0, 0, 0, 0, Optional.empty());
        }
    }
}
