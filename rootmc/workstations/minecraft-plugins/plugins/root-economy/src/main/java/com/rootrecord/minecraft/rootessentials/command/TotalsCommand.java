package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import com.rootrecord.minecraft.rootessentials.web.RootMcEconomyWeb;
import com.rootrecord.minecraft.rootessentials.data.ListTotalsStore;
import com.rootrecord.minecraft.rootessentials.treasury.ListTotalsRefreshService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Snapshot essentials â€” full tables on rootmc.net/list/. */
public final class TotalsCommand implements CommandExecutor {

    private static final int MAX_ROWS = 8;

    private final RootEconomyPlugin plugin;

    public TotalsCommand(RootEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ListTotalsRefreshService refresh = plugin.listTotalsRefresh();
        ListTotalsStore store = plugin.listTotalsStore();
        if (refresh == null || store == null) {
            sender.sendMessage(plugin.colorize("&cList totals unavailable."));
            return true;
        }
        try {
            refresh.refreshIfStale();
            String scope = refresh.resolveScope();
            List<ListTotalsStore.Row> rows = store.readAll(scope);
            if (rows.isEmpty()) {
                refresh.refreshNow(scope);
                rows = store.readAll(scope);
            }
            ChatUi.banner(sender, "Economy totals");
            ChatUi.row(sender, "Scope", scope);
            List<ListTotalsStore.Row> top = new ArrayList<>(rows);
            top.sort(Comparator.comparingDouble(ListTotalsStore.Row::amountG).reversed());
            int n = 0;
            for (ListTotalsStore.Row row : top) {
                if (Math.abs(row.amountG()) < 0.01) {
                    continue;
                }
                ChatUi.gold(sender, shortLabel(row.label()), plugin.money(row.amountG()), plugin.currency());
                if (++n >= MAX_ROWS) {
                    break;
                }
            }
            if (n == 0) {
                ChatUi.tip(sender, "No snapshot yet.");
            }
            ChatUi.links(sender, "Full lists", RootMcEconomyWeb.economy());
        } catch (Exception ex) {
            sender.sendMessage(plugin.colorize("&cTotals failed: &f" + ex.getMessage()));
        }
        return true;
    }

    private static String shortLabel(String label) {
        if (label == null || label.isBlank()) {
            return "Item";
        }
        String s = label.trim();
        return s.length() <= 14 ? s : s.substring(0, 14);
    }
}
