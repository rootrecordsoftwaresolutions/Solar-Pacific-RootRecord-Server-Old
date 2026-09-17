package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import com.rootrecord.minecraft.rootessentials.treasury.EconomyNoteSupplyDisplay;
import com.rootrecord.minecraft.rootessentials.treasury.ReserveStatsService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class EconomyCommand implements CommandExecutor {

    private final RootEconomyPlugin plugin;

    public EconomyCommand(RootEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            ReserveStatsService.ReserveSnapshot snap = plugin.reserveSnapshot(null);
            EconomyNoteSupplyDisplay.sendHeadline(
                    plugin,
                    sender,
                    snap.noteSupply(),
                    snap.grossReserveBalance(),
                    "Economy",
                    true);
            ChatUi.tip(sender, "/gold  |  /tax  |  /mint  |  /reserve");
            return true;
        } catch (Exception ex) {
            sender.sendMessage(plugin.colorize("&cEconomy stats failed: &f" + ex.getMessage()));
            return true;
        }
    }
}
