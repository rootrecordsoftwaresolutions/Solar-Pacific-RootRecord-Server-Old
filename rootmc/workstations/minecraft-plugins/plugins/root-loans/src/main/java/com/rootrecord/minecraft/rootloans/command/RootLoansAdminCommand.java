package com.rootrecord.minecraft.rootloans.command;

import com.rootrecord.minecraft.rootloans.RootLoansPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class RootLoansAdminCommand implements CommandExecutor {

    private final RootLoansPlugin plugin;

    public RootLoansAdminCommand(RootLoansPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rootloans.reload")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (args.length == 1 && "reload".equalsIgnoreCase(args[0])) {
            plugin.reloadLocalConfig();
            sender.sendMessage(plugin.msg(
                    plugin.isLoansReady() ? "reload-done" : "reload-treasury-pending"));
            return true;
        }
        if (args.length == 2 && "tax-run".equalsIgnoreCase(args[0]) && "now".equalsIgnoreCase(args[1])) {
            if (plugin.loanDailyTaxScheduler() == null) {
                sender.sendMessage(plugin.colorize("&cLoan daily tax scheduler unavailable."));
                return true;
            }
            plugin.loanDailyTaxScheduler().runNow(true);
            sender.sendMessage(plugin.colorize("&aLoan daily tax run queued."));
            return true;
        }
        sender.sendMessage(plugin.colorize("&eUsage: /rootloans reload | /rootloans tax-run now"));
        return true;
    }
}
