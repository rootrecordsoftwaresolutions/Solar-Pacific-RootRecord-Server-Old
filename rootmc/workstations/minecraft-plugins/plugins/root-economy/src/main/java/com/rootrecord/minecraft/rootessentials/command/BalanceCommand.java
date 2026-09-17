package com.rootrecord.minecraft.rootessentials.command;

import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import com.rootrecord.minecraft.rootessentials.util.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class BalanceCommand implements CommandExecutor {

    private final RootEconomyPlugin plugin;

    public BalanceCommand(RootEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!Permissions.has(player, "balance")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        try {
            if (args.length == 0) {
                var heartbeat = plugin.economyHeartbeat();
                if (heartbeat != null) {
                    heartbeat.sendTo(player);
                    return true;
                }
                double bal = plugin.balance(player.getUniqueId(), player.getName());
                player.sendMessage(plugin.msg("balance-self")
                        .replace("{balance}", plugin.money(bal))
                        .replace("{currency}", plugin.currency()));
                plugin.loanSummary(player.getUniqueId()).ifPresent(summary ->
                        player.sendMessage(plugin.colorize("&7Loan owed: &c" + plugin.money(summary.owed()) + " G"
                                + " &7| Limit: &f" + plugin.money(summary.maxLoan()) + " G"
                                + " &7| Takes (24h): &f" + summary.takesInRolling24h() + "/" + summary.maxTakesPer24h())));
                return true;
            }
            var target = plugin.getServer().getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0]));
                return true;
            }
            double bal = plugin.balance(target.getUniqueId(), target.getName());
            player.sendMessage(plugin.msg("balance-other")
                    .replace("{player}", target.getName())
                    .replace("{balance}", plugin.money(bal))
                    .replace("{currency}", plugin.currency()));
            return true;
        } catch (Exception ex) {
            player.sendMessage(plugin.colorize("&cBalance lookup failed: &f" + ex.getMessage()));
            return true;
        }
    }
}
