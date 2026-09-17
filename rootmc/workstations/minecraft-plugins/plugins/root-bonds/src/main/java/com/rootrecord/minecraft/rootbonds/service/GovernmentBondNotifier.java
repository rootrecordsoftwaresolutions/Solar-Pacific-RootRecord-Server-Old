package com.rootrecord.minecraft.rootbonds.service;

import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.rootbonds.RootBondsPlugin;
import com.rootrecord.minecraft.rootbonds.data.BondsStore;
import com.rootrecord.minecraft.rootbonds.towny.TownyReflection;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Notifies town mayors / nation kings when auto-bond reserve earnings deposit to a government bank. */
public final class GovernmentBondNotifier {

    private static final DecimalFormat MONEY = new DecimalFormat("0.##");

    private GovernmentBondNotifier() {}

    public static void notifyDeposit(RootBondsPlugin plugin, String details, double amountG) {
        if (amountG + 1e-9 < GoldMoney.MIN_AMOUNT || !TownyReflection.isAvailable()) {
            return;
        }
        String parsed = parseDetails(details);
        if (parsed == null) {
            return;
        }
        int colon = parsed.indexOf(':');
        if (colon < 1) {
            return;
        }
        String kind = parsed.substring(0, colon).toLowerCase(Locale.ROOT);
        String name = parsed.substring(colon + 1).trim();
        if (name.isEmpty()) {
            return;
        }

        Set<UUID> leaders = switch (kind) {
            case "town" -> leadersForTown(name);
            case "nation" -> leadersForNation(name);
            default -> Set.of();
        };
        if (leaders.isEmpty()) {
            return;
        }

        String amount = MONEY.format(GoldMoney.round(amountG));
        String template = switch (kind) {
            case "town" -> plugin.msg("gov-bond-deposit-town");
            case "nation" -> plugin.msg("gov-bond-deposit-nation");
            default -> "";
        };
        if (template.isBlank()) {
            return;
        }
        String message = colorize(template
                .replace("{town}", name)
                .replace("{nation}", name)
                .replace("{amount}", amount));
        for (UUID leader : leaders) {
            Player online = Bukkit.getPlayer(leader);
            if (online != null && online.isOnline()) {
                online.sendMessage(message);
            } else {
                queueOffline(plugin, leader, kind, name, amountG);
            }
        }
    }

    /** Persists the deposit for an offline leader — delivered as a summary on next login. */
    private static void queueOffline(RootBondsPlugin plugin, UUID leader, String kind, String name, double amountG) {
        BondsStore store = plugin.store();
        if (store == null || !plugin.mysqlReady()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                store.addPendingGovNotify(leader, kind, name, GoldMoney.round(amountG));
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Bond offline notify queue failed: " + ex.getMessage(), ex);
            }
        });
    }

    /** Sends the accumulated while-away summary to a freshly joined leader. */
    public static void deliverPending(RootBondsPlugin plugin, Player player) {
        BondsStore store = plugin.store();
        if (store == null || !plugin.mysqlReady() || player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            java.util.List<BondsStore.PendingGovNotifyRow> rows;
            try {
                rows = store.takePendingGovNotify(uuid);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Bond offline notify read failed: " + ex.getMessage(), ex);
                return;
            }
            if (rows.isEmpty()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player online = Bukkit.getPlayer(uuid);
                if (online == null || !online.isOnline()) {
                    return;
                }
                for (BondsStore.PendingGovNotifyRow row : rows) {
                    String template = "nation".equalsIgnoreCase(row.kind())
                            ? plugin.msg("gov-bond-away-nation")
                            : plugin.msg("gov-bond-away-town");
                    if (template.isBlank()) {
                        continue;
                    }
                    online.sendMessage(colorize(template
                            .replace("{town}", row.displayName())
                            .replace("{nation}", row.displayName())
                            .replace("{amount}", MONEY.format(GoldMoney.round(row.amountG())))));
                }
            });
        });
    }

    private static String parseDetails(String details) {
        if (details == null) {
            return null;
        }
        String trimmed = details.trim();
        int semi = trimmed.indexOf(';');
        if (semi >= 0) {
            trimmed = trimmed.substring(0, semi).trim();
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Set<UUID> leadersForTown(String townName) {
        Set<UUID> out = new LinkedHashSet<>();
        Object town = TownyReflection.townByName(townName);
        if (town == null) {
            return out;
        }
        UUID mayor = residentUuid(TownyReflection.invokeNoArg(town, "getMayor"));
        if (mayor != null) {
            out.add(mayor);
        }
        return out;
    }

    private static Set<UUID> leadersForNation(String nationName) {
        Set<UUID> out = new LinkedHashSet<>();
        Object nation = TownyReflection.nationByName(nationName);
        if (nation == null) {
            return out;
        }
        UUID king = residentUuid(TownyReflection.invokeNoArg(nation, "getKing"));
        if (king != null) {
            out.add(king);
        }
        Object capital = TownyReflection.invokeNoArg(nation, "getCapital");
        UUID capitalMayor = residentUuid(TownyReflection.invokeNoArg(capital, "getMayor"));
        if (capitalMayor != null) {
            out.add(capitalMayor);
        }
        return out;
    }

    private static UUID residentUuid(Object resident) {
        return TownyReflection.uuidOrNull(resident);
    }

    private static String colorize(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw == null ? "" : raw);
    }
}
