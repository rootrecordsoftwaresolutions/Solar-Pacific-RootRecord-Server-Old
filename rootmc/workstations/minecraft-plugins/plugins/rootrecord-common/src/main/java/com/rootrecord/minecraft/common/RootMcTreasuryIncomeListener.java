package com.rootrecord.minecraft.common;

import java.util.UUID;

/** Called when Gold credits the Server Reserve (tax, death fee share, loan repayments, etc.). */
@FunctionalInterface
public interface RootMcTreasuryIncomeListener {

    void onTreasuryIncome(double amount, TreasuryLedgerType type, UUID sourceUuid, String details);
}
