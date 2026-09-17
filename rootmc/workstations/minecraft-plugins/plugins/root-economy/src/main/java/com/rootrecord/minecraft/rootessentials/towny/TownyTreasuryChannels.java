package com.rootrecord.minecraft.rootessentials.towny;

import java.util.Locale;
import java.util.UUID;

/** Thread-local channel tags for Towny closed-economy deposits into towny-server. */
public final class TownyTreasuryChannels {

    private static final ThreadLocal<String> PENDING = new ThreadLocal<>();
    private static final ThreadLocal<UUID> PENDING_PAYER_UUID = new ThreadLocal<>();
    private static final ThreadLocal<String> PENDING_PAYER_NAME = new ThreadLocal<>();
    private static final double NEW_TOWN_GOLD = 400.0;
    private static final double NEW_NATION_GOLD = 2000.0;
    private static final double OUTPOST_GOLD = 350.0;
    private static final double BONUS_TOWNBLOCK_GOLD = 500.0;
    private static final double FOUNDING_TOLERANCE = 0.05;

    private TownyTreasuryChannels() {}

    public static void set(String channel) {
        if (channel != null && !channel.isBlank()) {
            PENDING.set(channel);
        }
    }

    public static String consume() {
        String value = PENDING.get();
        PENDING.remove();
        return value;
    }

    public static void clear() {
        PENDING.remove();
        PENDING_PAYER_UUID.remove();
        PENDING_PAYER_NAME.remove();
    }

    public static String peek() {
        return PENDING.get();
    }

    public static void setPayer(UUID uuid, String name) {
        if (uuid != null) {
            PENDING_PAYER_UUID.set(uuid);
        }
        if (name != null && !name.isBlank()) {
            PENDING_PAYER_NAME.set(name);
        }
    }

    public static UUID consumePayerUuid() {
        UUID value = PENDING_PAYER_UUID.get();
        PENDING_PAYER_UUID.remove();
        return value;
    }

    public static String consumePayerName() {
        String value = PENDING_PAYER_NAME.get();
        PENDING_PAYER_NAME.remove();
        return value;
    }

    /**
     * Towny closed economy routes every {@code Account.payTo} through towny-server:
     * sender withdraw → payToServer (deposit server), receiver deposit → payFromServer (withdraw server).
     * Those mirror legs are not fees and must not hit the reserve ledger.
     */
    public static boolean isClosedEconomyMirror() {
        boolean sawPayTo = false;
        boolean sawPayToServer = false;
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String className = frame.getClassName().toLowerCase(Locale.ROOT);
            if (className.contains("rootrecord")) {
                continue;
            }
            String method = frame.getMethodName().toLowerCase(Locale.ROOT);
            if ("payto".equals(method)) {
                sawPayTo = true;
            }
            if ("paytoserver".equals(method)) {
                sawPayToServer = true;
            }
            if (sawPayTo && sawPayToServer) {
                return true;
            }
        }
        return false;
    }

    /** True when Vault is handling a Towny closed-economy payment (not town/nation bank internal moves). */
    public static boolean isTownyEconomyCall() {
        if (peek() != null) {
            return true;
        }
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String className = frame.getClassName().toLowerCase(Locale.ROOT);
            if (className.contains("rootrecord")) {
                continue;
            }
            if (className.contains("palmergames") || className.contains("towny")) {
                return true;
            }
        }
        return false;
    }

    /** Re-tag founding/outpost/bonus fees when event hooks missed. */
    public static String normalizeFoundingDetails(String details, double amount) {
        String channel = details == null || details.isBlank() ? "towny:other" : details.trim();
        if (channel.startsWith("towny:new-town") || channel.startsWith("towny:new-nation")) {
            return channel;
        }
        if (channel.startsWith("towny:outpost") || channel.startsWith("towny:bonus-townblock")) {
            return channel;
        }
        if (Math.abs(amount - NEW_NATION_GOLD) <= FOUNDING_TOLERANCE) {
            return "towny:new-nation";
        }
        if (Math.abs(amount - NEW_TOWN_GOLD) <= FOUNDING_TOLERANCE) {
            return "towny:new-town";
        }
        if (Math.abs(amount - BONUS_TOWNBLOCK_GOLD) <= FOUNDING_TOLERANCE) {
            return "towny:bonus-townblock";
        }
        if (Math.abs(amount - OUTPOST_GOLD) <= FOUNDING_TOLERANCE) {
            return "towny:outpost";
        }
        return channel;
    }

    public static String inferFromStackTrace() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String className = frame.getClassName().toLowerCase(Locale.ROOT);
            if (!className.contains("towny") && !className.contains("palmergames")) {
                continue;
            }
            String method = frame.getMethodName().toLowerCase(Locale.ROOT);
            if (className.contains("unclaim") || method.contains("unclaim")) {
                continue;
            }
            if (className.contains("prenewtownevent") || className.contains("newtownevent")) {
                return "towny:new-town";
            }
            if (className.contains("prenewnationevent") || className.contains("newnationevent")) {
                return "towny:new-nation";
            }
            if (method.contains("newtown") || method.contains("createtown") || method.contains("foundtown")) {
                return "towny:new-town";
            }
            if (method.contains("newnation") || method.contains("createnation") || method.contains("foundnation")) {
                return "towny:new-nation";
            }
            if (className.contains("claim") || method.contains("claim")) {
                return "towny:claim";
            }
            if (method.contains("outpost")) {
                return "towny:outpost";
            }
            if (method.contains("bonustownblock") || method.contains("bonustownblocks") || method.contains("buybonus")) {
                return "towny:bonus-townblock";
            }
            if (method.contains("merge")) {
                return "towny:merge";
            }
        }
        return "towny:other";
    }
}
