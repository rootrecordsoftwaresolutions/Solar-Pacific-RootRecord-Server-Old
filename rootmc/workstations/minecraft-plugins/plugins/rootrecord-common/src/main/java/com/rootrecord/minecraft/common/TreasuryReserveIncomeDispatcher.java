package com.rootrecord.minecraft.common;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fan-out for Server Reserve inflows. Each plugin shades {@code rootrecord-common}, so
 * {@link TreasuryIncomeHub} is not shared across jars. Essentials wires this dispatcher
 * to Root-Bonds (and any same-process listeners) once at startup.
 */
public final class TreasuryReserveIncomeDispatcher {

    @FunctionalInterface
    public interface Handler {
        void onReserveIncome(double amount, TreasuryLedgerType type, UUID sourceUuid, String details);
    }

    private static volatile Handler handler;
    private static volatile Logger logger = Logger.getLogger("RootRecord-TreasuryIncome");

    private TreasuryReserveIncomeDispatcher() {}

    public static void setLogger(Logger log) {
        logger = log != null ? log : Logger.getLogger("RootRecord-TreasuryIncome");
    }

    public static void setHandler(Handler next) {
        handler = next;
    }

    public static void clearHandler() {
        handler = null;
    }

    public static void dispatch(double amount, TreasuryLedgerType type, UUID sourceUuid, String details) {
        if (amount <= 0 || type == null) {
            return;
        }
        Handler active = handler;
        if (active != null) {
            try {
                active.onReserveIncome(amount, type, sourceUuid, details);
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Reserve income handler failed: " + ex.getMessage(), ex);
            }
        }
        TreasuryIncomeHub.dispatch(amount, type, sourceUuid, details);
    }
}
