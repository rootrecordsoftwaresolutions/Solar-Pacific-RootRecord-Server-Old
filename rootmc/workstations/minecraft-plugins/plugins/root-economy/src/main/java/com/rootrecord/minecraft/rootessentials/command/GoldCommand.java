package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import com.rootrecord.minecraft.rootessentials.chat.HostPowerInfo;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/** Live gold mining bonus, site taxes, CPU tax, and solar XP. */
public final class GoldCommand implements CommandExecutor {

    private final RootEconomyPlugin plugin;

    public GoldCommand(RootEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            ChatUi.banner(sender, "Gold");
            HostPowerInfo.sendLive(plugin, sender);
            HostPowerInfo.sendSiteRates(plugin, sender);
            HostPowerInfo.sendGoldRules(sender);
            HostPowerInfo.sendLinks(sender);
            ChatUi.tip(sender, "/radar  |  /mint  |  /tax  |  /economy");
            return true;
        } catch (Exception ex) {
            sender.sendMessage(plugin.colorize("&cGold lookup failed: &f" + ex.getMessage()));
            return true;
        }
    }
}
