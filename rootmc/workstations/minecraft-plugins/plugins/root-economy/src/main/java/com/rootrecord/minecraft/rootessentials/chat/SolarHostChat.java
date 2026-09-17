package com.rootrecord.minecraft.rootessentials.chat;

import com.rootrecord.minecraft.rootessentials.service.SolarMiningMultiplierService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Locale;

/** In-game solar bonus / offline env tax (Ava voice). */
@SuppressWarnings("deprecation")
public final class SolarHostChat {

    private static final String PREFIX = "&bAva &8· ";

    private SolarHostChat() {}

    public static String line(SolarMiningMultiplierService.Snapshot s) {
        if (s == null || !s.online()) {
            return PREFIX + "&7Solar offline &8· &f1.00x &7gold/skills &8· &c+1% env";
        }
        String bank =
                s.batteryPercent() != null && Double.isFinite(s.batteryPercent())
                        ? Math.round(s.batteryPercent()) + "%"
                        : "?";
        String watts =
                s.solarWatts() != null && Double.isFinite(s.solarWatts())
                        ? Math.round(s.solarWatts()) + "W"
                        : "?W";
        double bonusPct = Math.max(0, (s.multiplier() - 1.0d) * 100.0d);
        return PREFIX
                + "&6Solar bonus &8· &f"
                + String.format(Locale.US, "%.3fx", s.multiplier())
                + String.format(Locale.US, " &7(+%.0f%%)", bonusPct)
                + " &7bank &f"
                + bank
                + " &7in &f"
                + watts
                + " &8· gold + skills";
    }

    public static void send(Player player, SolarMiningMultiplierService.Snapshot snapshot) {
        if (player == null) {
            return;
        }
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', line(snapshot)));
    }
}
