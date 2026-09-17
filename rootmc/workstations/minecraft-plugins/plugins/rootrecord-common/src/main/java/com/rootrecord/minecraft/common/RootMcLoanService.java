package com.rootrecord.minecraft.common;

import java.util.Optional;
import java.util.UUID;

/** Server loan ledger — implemented by Root-Loans; consumed by Root Essentials income paths. */
public interface RootMcLoanService {

    RootMcIncomeSweepResult applyIncome(UUID uuid, String username, double grossIncome);

    Optional<LoanBalanceSummary> balanceSummary(UUID uuid);

    record LoanBalanceSummary(
            double owed,
            double maxLoan,
            int takesInRolling24h,
            int maxTakesPer24h,
            long millisUntilNextTakeSlot) {}
}
