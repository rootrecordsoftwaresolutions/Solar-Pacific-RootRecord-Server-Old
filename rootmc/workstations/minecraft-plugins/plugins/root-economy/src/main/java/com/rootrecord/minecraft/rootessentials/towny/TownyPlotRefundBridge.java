package com.rootrecord.minecraft.rootessentials.towny;

import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Collection;
import java.util.UUID;

/**
 * Refunds the full marginal claim cost when a town plot is unclaimed (100% refund).
 * Pays from towny-server (reserve) into the town bank â€” closed-loop, not minted.
 *
 * <p>Must hook {@code TownPreUnclaimEvent} (per block). {@code TownPreUnclaimCmdEvent} has no
 * {@code getTownBlock()} and must not be used here.
 */
public final class TownyPlotRefundBridge {

    private static final String REFUND_REASON = "towny:unclaim-refund";
    private static final String PRE_UNCLAIM_EVENT =
            "com.palmergames.bukkit.towny.event.town.TownPreUnclaimEvent";

    private TownyPlotRefundBridge() {}

    public static void register(RootEconomyPlugin plugin) {
        if (TownyReflection.townyPlugin() == null) {
            return;
        }
        try {
            Class<? extends Event> eventClass = TownyReflection.loadEventClass(PRE_UNCLAIM_EVENT);
            if (eventClass == null) {
                plugin.getLogger().warning(
                        "Towny plot refund hook failed: TownPreUnclaimEvent not found on this Towny build.");
                return;
            }
            Listener listener = new Listener() {};
            plugin.getServer().getPluginManager().registerEvent(
                    eventClass,
                    listener,
                    EventPriority.MONITOR,
                    (l, event) -> onPreUnclaim(plugin, event),
                    plugin,
                    true);
            plugin.getLogger().info(
                    "Towny plot unclaim refunds: 100% claim/outpost cost via TownPreUnclaimEvent (paid from reserve).");
        } catch (Exception ex) {
            plugin.getLogger().warning("Towny plot refund hook failed: " + ex.getMessage());
        }
    }

    private static void onPreUnclaim(RootEconomyPlugin plugin, Event event) {
        if (event instanceof org.bukkit.event.Cancellable cancellable && cancellable.isCancelled()) {
            return;
        }
        try {
            Object cause = event.getClass().getMethod("getCause").invoke(event);
            if (cause != null) {
                String causeName = String.valueOf(cause);
                if ("DELETE".equals(causeName) || "ADMIN_COMMAND".equals(causeName)) {
                    return;
                }
            }

            Object town = event.getClass().getMethod("getTown").invoke(event);
            Object townBlock = event.getClass().getMethod("getTownBlock").invoke(event);
            if (town == null || townBlock == null) {
                return;
            }

            double refund = marginalClaimRefund(town, townBlock);
            if (refund <= 0) {
                return;
            }

            Object account = town.getClass().getMethod("getAccount").invoke(town);
            if (account == null) {
                return;
            }
            String bankName = String.valueOf(account.getClass().getMethod("getName").invoke(account));
            UUID bankUuid = toUuid(account.getClass().getMethod("getUUID").invoke(account));
            if (bankUuid == null || bankName.isBlank()) {
                return;
            }

            if (plugin.treasury() == null) {
                plugin.getLogger().warning("Town unclaim refund skipped â€” treasury unavailable.");
                return;
            }

            final double amount = refund;
            plugin.getServer().getScheduler().runTask(plugin, () -> payFromReserve(plugin, bankUuid, bankName, amount));
        } catch (NoSuchMethodException ex) {
            plugin.getLogger().warning("Town unclaim refund hook mismatch (wrong Towny event?): " + ex.getMessage());
        } catch (Exception ex) {
            plugin.getLogger().warning("Town unclaim refund failed: " + ex.getMessage());
        }
    }

    private static void payFromReserve(
            RootEconomyPlugin plugin, UUID bankUuid, String bankName, double refund) {
        try {
            boolean ok = plugin.treasury().grantToPlayer(
                    bankUuid,
                    bankName,
                    refund,
                    plugin.treasury().treasuryUuid(),
                    plugin.treasury().treasuryUsername(),
                    REFUND_REASON);
            if (!ok) {
                plugin.getLogger().warning("Town unclaim refund failed for " + bankName + " (" + refund + " G).");
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Town unclaim refund deposit failed: " + ex.getMessage());
        }
    }

    private static UUID toUuid(Object raw) {
        if (raw instanceof UUID uuid) {
            return uuid;
        }
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(raw));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static double marginalClaimRefund(Object town, Object townBlock) throws Exception {
        Class<?> settings = TownyReflection.loadClass("com.palmergames.bukkit.towny.TownySettings");
        if ((boolean) townBlock.getClass().getMethod("isOutpost").invoke(townBlock)) {
            // Towny charges getOutpostCost() for /t claim outpost (not claim price + outpost fee).
            return (double) settings.getMethod("getOutpostCost").invoke(null);
        }

        double base = (double) settings.getMethod("getClaimPrice").invoke(null);
        double increase = (double) settings.getMethod("getClaimPriceIncreaseValue").invoke(null);
        double max = (double) settings.getMethod("getMaxClaimPrice").invoke(null);

        @SuppressWarnings("unchecked")
        Collection<Object> blocks = (Collection<Object>) town.getClass().getMethod("getTownBlocks").invoke(town);
        int size = blocks == null ? 0 : blocks.size();
        if (size <= 0) {
            return 0;
        }

        // Cost paid when this block was the next claim at (size - 1) existing plots.
        double price = Math.round(Math.pow(increase, Math.max(0, size - 1)) * base);
        if (max != -1) {
            price = Math.min(price, max);
        }
        return price;
    }
}
