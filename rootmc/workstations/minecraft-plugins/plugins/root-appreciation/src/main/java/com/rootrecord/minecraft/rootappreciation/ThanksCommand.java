package com.rootrecord.minecraft.rootappreciation;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ThanksCommand implements CommandExecutor, TabCompleter {

    private final RootAppreciationPlugin plugin;

    public ThanksCommand(RootAppreciationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.config().enabled()) {
            sender.sendMessage(plugin.msg("disabled"));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("rootappreciation.admin")) {
                sender.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            plugin.reloadAll();
            sender.sendMessage(plugin.msg("reload-done"));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("give")) {
            if (sender instanceof Player) {
                sender.sendMessage(plugin.msg("give-console-only"));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(plugin.msg("give-usage"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().equalsIgnoreCase(args[1])) {
                        target = p;
                        break;
                    }
                }
            }
            if (target == null) {
                sender.sendMessage(plugin.msg("player-not-found").replace("{player}", args[1]));
                return true;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(plugin.msg("invalid-amount"));
                return true;
            }
            if (amount < 1) {
                sender.sendMessage(plugin.msg("invalid-amount"));
                return true;
            }
            plugin.service().giveTokens(target, amount, "console-give", true);
            sender.sendMessage(plugin.msg("give-success")
                    .replace("{amount}", String.valueOf(amount))
                    .replace("{player}", target.getName()));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("redeem")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.msg("players-only"));
                return true;
            }
            if (!player.hasPermission("rootappreciation.redeem")) {
                player.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            int amount = 1;
            if (args.length >= 2) {
                try {
                    amount = Integer.parseInt(args[1]);
                } catch (NumberFormatException ex) {
                    player.sendMessage(plugin.msg("invalid-amount"));
                    return true;
                }
            }
            plugin.service().redeemTokens(player, amount);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!player.hasPermission("rootappreciation.use")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        sendStats(player);
        return true;
    }

    private void sendStats(Player player) {
        AppreciationStore.Stats global = plugin.store().globalStats();
        AppreciationStore.Stats you = plugin.store().playerStats(player.getUniqueId());
        var streak = plugin.store().playerStreak(player.getUniqueId());
        int streakDay = streak.map(AppreciationStore.PlayerStreak::streakDay).orElse(0);
        player.sendMessage(plugin.msg("thanks-header"));
        player.sendMessage(plugin.msg("thanks-global-issued").replace("{count}", String.valueOf(global.issued())));
        player.sendMessage(plugin.msg("thanks-global-redeemed").replace("{count}", String.valueOf(global.redeemed())));
        player.sendMessage(plugin.msg("thanks-you-issued").replace("{count}", String.valueOf(you.issued())));
        player.sendMessage(plugin.msg("thanks-you-redeemed").replace("{count}", String.valueOf(you.redeemed())));
        player.sendMessage(plugin.msg("thanks-supply").replace("{count}", String.valueOf(global.supply())));
        int inv = plugin.tokens().countInInventory(player);
        int votes = plugin.voteShardService() != null
                ? plugin.voteShardService().listingVoteCount(player.getUniqueId())
                : 0;
        player.sendMessage(plugin.msg("thanks-inventory").replace("{count}", String.valueOf(inv)));
        player.sendMessage(plugin.msg("thanks-vote")
                .replace("{votes}", String.valueOf(votes))
                .replace("{tokens}", String.valueOf(votes)));
        player.sendMessage(plugin.msg("thanks-shards"));
        player.sendMessage(plugin.msg("thanks-streak")
                .replace("{streak}", String.valueOf(streakDay))
                .replace("{next}", plugin.service().nextBonusHint(player.getUniqueId())));
        player.sendMessage(plugin.msg("thanks-usage"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("give", "redeem", "reload")) {
                if (!s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    continue;
                }
                if (s.equals("give")) {
                    if (!(sender instanceof Player)) {
                        out.add(s);
                    }
                } else if (s.equals("reload")) {
                    if (sender.hasPermission("rootappreciation.admin")) {
                        out.add(s);
                    }
                } else {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")
                && !(sender instanceof Player)) {
            String q = args[1].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(q)) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
