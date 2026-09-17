package com.rootrecord.minecraft.common;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.Locale;

/**
 * Ava Ivy player-facing chat lines (cyan hologram voice — Daily Broadcast palette).
 * Prefix: aqua/cyan name · dark separator.
 */
@SuppressWarnings("deprecation")
public final class AvaChat {

    /** &#00E5FF-ish via legacy &b aqua (matches stream cyan). */
    public static final String PREFIX = "&bAva &8· ";

    private AvaChat() {}

    /** EcoFlow / solar-mine snapshot from Root-Economy (reflection — no compile dep). */
    public record SolarGoldState(boolean online, double multiplier, Double batteryPercent, double taxRate) {}

    public static boolean solarGoldBonusActive(boolean online, double multiplier) {
        return online && Double.isFinite(multiplier) && multiplier > 1.0000001d;
    }

    public static boolean solarGoldTaxActive(double multiplier, double taxRate) {
        return (Double.isFinite(taxRate) && taxRate >= 0.05d)
                || (Double.isFinite(multiplier) && multiplier < 0.999d);
    }

    public static String solarGoldBonusLine(double multiplier, Double batteryPercent) {
        String mult = String.format(Locale.US, "%.3fx", multiplier);
        String battery =
                batteryPercent != null && Double.isFinite(batteryPercent)
                        ? Math.round(batteryPercent) + "%"
                        : "?";
        return PREFIX
                + "&bGold multiplier &8· &f"
                + mult
                + " &7from solar battery &f"
                + battery
                + " &8(10–100% track)";
    }

    public static String solarGoldTaxLine(double taxRate, Double batteryPercent) {
        String battery =
                batteryPercent != null && Double.isFinite(batteryPercent)
                        ? Math.round(batteryPercent) + "%"
                        : "?";
        double pct = Double.isFinite(taxRate) && taxRate > 0 ? taxRate * 100.0d : 10.0d;
        return PREFIX
                + "&cGold tax &8· &f"
                + String.format(Locale.US, "%.0f%%", pct)
                + " &7offline / underpowered battery &f"
                + battery;
    }

    public static void sendLegacy(Player player, String legacy) {
        if (player == null || legacy == null || legacy.isBlank()) {
            return;
        }
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', legacy));
    }

    public static void sendSolarGoldBonus(Player player, boolean online, double multiplier, Double batteryPercent) {
        if (!solarGoldBonusActive(online, multiplier)) {
            return;
        }
        sendLegacy(player, solarGoldBonusLine(multiplier, batteryPercent));
    }

    /** Soft-depend Root-Economy — bonus when &gt;1×, tax line when underpowered/offline. */
    public static void sendSolarGoldBonusIfActive(Player player) {
        if (player == null) {
            return;
        }
        SolarGoldState state = peekRootEconomySolar(player.getServer().getPluginManager());
        if (state == null) {
            return;
        }
        if (solarGoldBonusActive(state.online(), state.multiplier())) {
            sendLegacy(player, solarGoldBonusLine(state.multiplier(), state.batteryPercent()));
            return;
        }
        if (solarGoldTaxActive(state.multiplier(), state.taxRate())) {
            sendLegacy(player, solarGoldTaxLine(state.taxRate(), state.batteryPercent()));
        }
    }

    /**
     * Soft-depend lookup of Root-Economy's solar mining feed via reflection
     * so softdepend plugins compile without a root-economy classpath.
     */
    public static SolarGoldState peekRootEconomySolar(PluginManager pluginManager) {
        if (pluginManager == null) {
            return null;
        }
        try {
            Plugin eco = pluginManager.getPlugin("Root-Economy");
            if (eco == null || !eco.isEnabled()) {
                return null;
            }
            Object solar = eco.getClass().getMethod("solarMining").invoke(eco);
            if (solar == null) {
                return null;
            }
            boolean online = (Boolean) solar.getClass().getMethod("online").invoke(solar);
            double multiplier = ((Number) solar.getClass().getMethod("multiplier").invoke(solar)).doubleValue();
            double taxRate = 0.0d;
            try {
                taxRate = ((Number) solar.getClass().getMethod("taxRate").invoke(solar)).doubleValue();
            } catch (NoSuchMethodException ignored) {
                if (!online || multiplier < 0.999d) {
                    taxRate = 0.10d;
                }
            }
            Double bank = null;
            Object snap = solar.getClass().getMethod("current").invoke(solar);
            if (snap != null) {
                Object pct = snap.getClass().getMethod("batteryPercent").invoke(snap);
                if (pct instanceof Number n) {
                    bank = n.doubleValue();
                }
            }
            return new SolarGoldState(online, multiplier, bank, taxRate);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
