package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import com.rootrecord.minecraft.rootessentials.util.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ExtraCommands {

    private ExtraCommands() {}

    public static final class Workbench implements CommandExecutor {
        private final RootEssentialsPlugin plugin;
        public Workbench(RootEssentialsPlugin plugin) { this.plugin = plugin; }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.msg("players-only"));
                return true;
            }
            if (!Permissions.has(player, "workbench")) {
                player.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            double cost = plugin.serviceFee("workbench", 1.0);
            if (cost > 0) {
                try {
                    double balance = plugin.balance(player.getUniqueId(), player.getName());
                    if (balance + 1e-9 < cost) {
                        player.sendMessage(plugin.msg("workbench-insufficient")
                                .replace("{amount}", plugin.money(cost))
                                .replace("{balance}", plugin.money(balance))
                                .replace("{currency}", plugin.currency()));
                        return true;
                    }
                    if (!plugin.withdraw(player.getUniqueId(), player.getName(), cost)) {
                        player.sendMessage(plugin.msg("workbench-insufficient")
                                .replace("{amount}", plugin.money(cost))
                                .replace("{balance}", plugin.money(balance))
                                .replace("{currency}", plugin.currency()));
                        return true;
                    }
                    plugin.sinkServiceFee(player.getUniqueId(), player.getName(), cost, "workbench");
                } catch (Exception ex) {
                    player.sendMessage(plugin.colorize("&cWorkbench charge failed: &f" + ex.getMessage()));
                    return true;
                }
            }
            player.openWorkbench(null, true);
            String body = cost > 0
                    ? "opened · -" + plugin.money(cost) + " " + plugin.currency()
                    : "opened";
            ChatUi.entry(player, "Workbench", body, "ok");
            return true;
        }
    }
}
