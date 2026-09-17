package com.rootrecord.minecraft.rootranks.command;

import com.rootrecord.minecraft.common.FancyHeadlines;
import com.rootrecord.minecraft.rootranks.RootRanksPlugin;
import com.rootrecord.minecraft.rootranks.config.RankTier;
import com.rootrecord.minecraft.rootranks.config.RanksConfig;
import com.rootrecord.minecraft.rootranks.service.RankPurchaseService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RankCommand implements CommandExecutor, TabCompleter {

    private final RootRanksPlugin plugin;

    public RankCommand(RootRanksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.ranksConfig().playersOnly());
            return true;
        }
        if (!player.hasPermission("rootranks.use")) {
            player.sendMessage(plugin.msg(plugin.ranksConfig().noPermission()));
            return true;
        }

        if ("rankup".equalsIgnoreCase(label) && args.length == 0) {
            handleBuy(player);
            return true;
        }

        String sub = args.length == 0 ? "info" : args[0].trim().toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "buy", "purchase", "up", "rankup" -> {
                handleBuy(player);
                yield true;
            }
            case "list" -> {
                sendList(player);
                yield true;
            }
            default -> {
                sendInfo(player);
                yield true;
            }
        };
    }

    private void sendInfo(Player player) {
        RanksConfig cfg = plugin.ranksConfig();
        RankPurchaseService.RankStatus status = plugin.purchases().status(player.getUniqueId());
        if (status.ownedIndex() < 0) {
            player.sendMessage(plugin.msg(cfg.explorerBenefits()));
        } else {
            RankTier owned = cfg.ranks().get(status.ownedIndex());
            sendBenefitsLine(player, cfg, owned);
        }
        if (status.nextTier().isEmpty()) {
            player.sendMessage(plugin.msg(cfg.maxRank().replace("{rank}", status.currentLabel())));
            return;
        }
        RankTier next = status.nextTier().get();
        player.sendMessage(plugin.msg(cfg.nextRank()
                .replace("{current}", status.currentLabel())
                .replace("{next}", next.display())
                .replace("{price}", plugin.formatGold(next.price()))
                .replace("{balance}", plugin.formatGold(status.balance()))));
        sendBenefitsLine(player, cfg, next);
        player.sendMessage(plugin.colorize("&7Use &f/rank buy &7to purchase the next tier."));
    }

    private void sendBenefitsLine(Player player, RanksConfig cfg, RankTier tier) {
        if (tier.benefits() == null || tier.benefits().isBlank()) {
            return;
        }
        player.sendMessage(plugin.colorize(cfg.rankBenefitsLine().replace("{benefits}", tier.benefits())));
    }

    private void sendList(Player player) {
        RanksConfig cfg = plugin.ranksConfig();
        int owned = plugin.luckPermsRanks().highestOwnedIndex(cfg.ranks(), player.getUniqueId());
        FancyHeadlines.sendBanner(player, "Player ranks");
        player.sendMessage(plugin.msg(cfg.rankListHeader()));
        List<RankTier> tiers = cfg.ranks();
        for (int i = 0; i < tiers.size(); i++) {
            RankTier tier = tiers.get(i);
            String ownedTag = i <= owned ? cfg.rankOwnedTag() : cfg.rankLockedTag();
            player.sendMessage(plugin.colorize(cfg.rankListRow()
                    .replace("{index}", Integer.toString(i + 1))
                    .replace("{display}", tier.display())
                    .replace("{price}", plugin.formatGold(tier.price()))
                    .replace("{owned}", ownedTag)));
        }
    }

    private void handleBuy(Player player) {
        RanksConfig cfgEarly = plugin.ranksConfig();
        if (!cfgEarly.purchasesEnabled()) {
            player.sendMessage(plugin.msg(cfgEarly.disabled()));
            return;
        }
        if (!player.hasPermission("rootranks.buy")) {
            player.sendMessage(plugin.msg(plugin.ranksConfig().noPermission()));
            return;
        }
        RanksConfig cfg = plugin.ranksConfig();
        // Always charge — rootranks.bypass must never skip Gold on /rank buy (ops had free rankups).
        RankPurchaseService.PurchaseResult result = plugin.purchases().tryPurchase(player, false);
        RankPurchaseService.PurchaseOutcome outcome = result.outcome();

        if (outcome == RankPurchaseService.PurchaseOutcome.MAX_RANK) {
            player.sendMessage(plugin.msg(cfg.maxRank()
                    .replace("{rank}", plugin.purchases().status(player.getUniqueId()).currentLabel())));
            return;
        }
        if (outcome == RankPurchaseService.PurchaseOutcome.INSUFFICIENT) {
            double balance = plugin.purchases().status(player.getUniqueId()).balance();
            player.sendMessage(plugin.msg(cfg.insufficient()
                    .replace("{price}", plugin.formatGold(result.price()))
                    .replace("{balance}", plugin.formatGold(balance))));
            return;
        }
        if (outcome == RankPurchaseService.PurchaseOutcome.APPLY_FAILED) {
            player.sendMessage(plugin.msg(cfg.purchaseFailed()));
            return;
        }
        if (outcome == RankPurchaseService.PurchaseOutcome.SUCCESS && result.tier() != null) {
            RankTier tier = result.tier();
            FancyHeadlines.sendBanner(player, "Rank up");
            player.sendMessage(plugin.msg(cfg.purchaseSuccess()
                    .replace("{rank}", tier.display())
                    .replace("{price}", plugin.formatGold(result.price()))));
            String broadcast = cfg.purchaseBroadcast()
                    .replace("{player}", player.getName())
                    .replace("{rank}", tier.display());
            Bukkit.broadcastMessage(plugin.colorize(broadcast));
            return;
        }
        sendInfo(player);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> options = List.of("buy", "list", "info");
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(prefix)) {
                out.add(option);
            }
        }
        return out;
    }
}
