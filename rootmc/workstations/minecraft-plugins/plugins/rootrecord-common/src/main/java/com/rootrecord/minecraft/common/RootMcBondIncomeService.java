package com.rootrecord.minecraft.common;

import java.util.UUID;

/** Cross-plugin bridge for reserve inflows that should accrue Root-Bonds coupon income. */
public interface RootMcBondIncomeService {

    void recordTreasuryIncome(double amount, String type, UUID sourceUuid, String details);

    /** Stops automated town/nation bond deposits for the current MC day once inactivity tax applies. */
    void suspendGovernmentBondCoupons(UUID accountUuid);
}
