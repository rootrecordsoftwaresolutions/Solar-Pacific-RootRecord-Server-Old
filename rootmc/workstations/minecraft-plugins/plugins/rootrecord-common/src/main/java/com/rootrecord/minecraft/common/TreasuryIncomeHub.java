package com.rootrecord.minecraft.common;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Fan-out for reserve inflows — bond coupons, analytics, etc. */
public final class TreasuryIncomeHub {

    private static final List<RootMcTreasuryIncomeListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile Logger logger = Logger.getLogger("RootRecord-TreasuryIncome");

    private TreasuryIncomeHub() {}

    public static void setLogger(Logger log) {
        logger = log != null ? log : Logger.getLogger("RootRecord-TreasuryIncome");
    }

    public static void register(RootMcTreasuryIncomeListener listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    public static void unregister(RootMcTreasuryIncomeListener listener) {
        LISTENERS.remove(listener);
    }

    public static void dispatch(double amount, TreasuryLedgerType type, UUID sourceUuid, String details) {
        if (amount <= 0 || type == null || LISTENERS.isEmpty()) {
            return;
        }
        for (RootMcTreasuryIncomeListener listener : LISTENERS) {
            try {
                listener.onTreasuryIncome(amount, type, sourceUuid, details);
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Treasury income listener failed: " + ex.getMessage(), ex);
            }
        }
    }
}
