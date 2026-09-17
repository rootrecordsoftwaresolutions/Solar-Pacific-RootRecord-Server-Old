package com.rootrecord.minecraft.rootbonds.command;

import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.rootbonds.RootBondsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class BondsCommand implements CommandExecutor, TabCompleter {

    private final RootBondsPlugin plugin;

    public BondsCommand(RootBondsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color(plugin.msg("players-only")));
            return true;
        }
        if (!player.hasPermission("rootbonds.use")) {
            player.sendMessage(color(plugin.msg("no-permission")));
            return true;
        }
        if (args.length >= 1 && "create".equalsIgnoreCase(args[0])) {
            return handleCreate(player, args);
        }
        plugin.menuRegistry().open(player);
        return true;
    }

    private boolean handleCreate(Player player, String[] args) {
        if (!plugin.bonds().enabled()) {
            player.sendMessage(color(plugin.msg("disabled")));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(color(plugin.msg("create-usage")));
            return true;
        }
        if ("root".equalsIgnoreCase(args[1])) {
            return handleCreateRoot(player);
        }
        double amount;
        try {
            amount = GoldMoney.round(Double.parseDouble(args[1]));
        } catch (NumberFormatException ex) {
            player.sendMessage(color(plugin.msg("create-amount-invalid")));
            return true;
        }
        if (amount + 1e-9 < plugin.bonds().config().minPrincipalG()) {
            player.sendMessage(color(plugin.msg("create-min")
                    .replace("{min}", GoldMoney.format(plugin.bonds().config().minPrincipalG()))));
            return true;
        }
        var economy = RootMcEconomyResolver.resolve(plugin);
        double balance = economy == null ? 0 : economy.balance(player.getUniqueId());
        if (balance + 1e-9 < amount) {
            player.sendMessage(color(plugin.msg("create-insufficient")
                    .replace("{amount}", GoldMoney.format(amount))
                    .replace("{balance}", GoldMoney.format(balance))));
            return true;
        }
        if (plugin.bonds().createBond(player, amount)) {
            player.sendMessage(color(plugin.msg("create-success")
                    .replace("{amount}", GoldMoney.format(amount))));
        } else {
            player.sendMessage(color(plugin.msg("create-failed")));
        }
        return true;
    }

    private boolean handleCreateRoot(Player player) {
        double amount = plugin.bonds().config().bondedRootCostG();
        var economy = RootMcEconomyResolver.resolve(plugin);
        double balance = economy == null ? 0 : economy.balance(player.getUniqueId());
        if (balance + 1e-9 < amount) {
            player.sendMessage(color(plugin.msg("create-insufficient")
                    .replace("{amount}", GoldMoney.format(amount))
                    .replace("{balance}", GoldMoney.format(balance))));
            return true;
        }
        if (plugin.bonds().createBondedRoot(player)) {
            player.sendMessage(color(plugin.msg("create-root-success")
                    .replace("{amount}", GoldMoney.format(amount))));
        } else {
            player.sendMessage(color(plugin.msg("create-root-failed")));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("rootbonds.use")) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            if ("create".startsWith(prefix)) {
                return List.of("create");
            }
            return List.of();
        }
        if (args.length == 2 && "create".equalsIgnoreCase(args[0])) {
            return List.of("root", "1", "5", "10", "25", "50", "100");
        }
        return List.of();
    }

    private String color(String raw) {
        return raw == null ? "" : raw.replace('&', '\u00A7');
    }
}
