package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import com.rootrecord.minecraft.rootessentials.treasury.ReserveStatsService;
import com.rootrecord.minecraft.rootessentials.util.HstTime;
import com.rootrecord.minecraft.rootessentials.web.RootMcEconomyWeb;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class ReserveCommand implements CommandExecutor {

    private final RootEconomyPlugin plugin;

    public ReserveCommand(RootEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        UUID playerUuid = sender instanceof Player p ? p.getUniqueId() : null;
        try {
            ReserveStatsService.ReserveSnapshot snap = plugin.reserveSnapshot(playerUuid);
            sendReserve(sender, snap);
            return true;
        } catch (Exception ex) {
            sender.sendMessage(plugin.colorize("&cReserve stats failed: &f" + ex.getMessage()));
            return true;
        }
    }

    private void sendReserve(CommandSender sender, ReserveStatsService.ReserveSnapshot snap) {
        ChatUi.banner(sender, "Server Reserve");

        String cur = plugin.currency();
        var supply = snap.noteSupply();
        ChatUi.gold(sender, "Reserve", money(supply.reserveNotesG()), cur);
        ChatUi.gold(sender, "Notes", money(supply.totalNotesG()), cur);
        ChatUi.gold(sender, "Minted", money(supply.goldMinedG()), cur);
        ChatUi.gold(sender, "Ledger", money(snap.grossReserveBalance()), cur);

        if (sender instanceof Player player) {
            double bonds = 0;
            var feature = plugin.bondsFeature();
            if (feature != null) {
                try {
                    var summary = feature.bondHeartbeatSummary(player.getUniqueId());
                    if (summary != null) {
                        bonds = summary.principalG();
                    }
                } catch (Exception ignored) {
                    // leave 0
                }
            }
            if (bonds > 0) {
                ChatUi.gold(sender, "Your bonds", money(bonds), cur);
            }
        }

        var month = snap.currentMonthTotals();
        ChatUi.entry(sender, "Month", snap.currentMonthKey() + " net " + money(month.net()) + " " + cur);

        if (snap.player() != null) {
            ReserveStatsService.PlayerMonthStatus p = snap.player();
            ChatUi.entry(sender, "You",
                    money(p.taxPaidMtd()) + " " + cur + " tax | "
                            + HstTime.formatPlaytime(p.monthlyPlaytimeSeconds()));
        }

        ChatUi.tip(sender, "Ava /bal is this reserve. Hire + fees sink here.");
        ChatUi.links(
                sender,
                "Reserve", RootMcEconomyWeb.reserve(),
                "Economy", RootMcEconomyWeb.economy(),
                "Tax", "https://rootmc.net/wiki/constitution/#taxes-fees-reference");
        ChatUi.tip(sender, "/tax  |  /mint  |  /gold  |  /economy");
    }

    private String money(double value) {
        return plugin.money(value);
    }
}
