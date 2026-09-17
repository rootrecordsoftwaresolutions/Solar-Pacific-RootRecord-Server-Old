package com.rootrecord.minecraft.rootappreciation;

import com.rootrecord.minecraft.common.ChatUi;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

/** /voteshard — Council Vote Shards: power, merge, and [Buy Voteshards]. */
public final class VoteShardCommand implements CommandExecutor {

    private static final String LINK_BUY = "https://rootmc.net/pro/vote-shards/";
    private static final String LINK_GUIDE = "https://rootmc.net/vote-shards/";
    private static final String LINK_PRO = "https://rootmc.net/pro/";

    private final RootAppreciationPlugin plugin;

    public VoteShardCommand(RootAppreciationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("ava")) {
            if (!(sender instanceof ConsoleCommandSender)
                    && !sender.hasPermission("rootappreciation.admin")) {
                sender.sendMessage(plugin.colorize(plugin.yaml().config()
                        .getString("messages.no-permission", "&cNo permission.")));
                return true;
            }
            int issued = plugin.voteShardService().matchAvaToHumans();
            sender.sendMessage(plugin.colorize(
                    "&bAva Council seat is a locked &f25%&d share (synthetic) · EC issued &f"
                            + issued + " &7(not minted to match humans)"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only. Console: /voteshard ava");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("craft")) {
            String err = plugin.voteShardService().craftPhysical(player);
            if ("none".equals(err)) {
                player.sendMessage(plugin.colorize("&eNo digital Vote Shards to craft."));
            } else if ("players_only".equals(err)) {
                player.sendMessage(plugin.colorize("&cPlayers only."));
            }
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("merge")) {
            player.sendMessage(plugin.colorize(
                    "&e/voteshard merge is retired. Use &f/voteshard craft &eto convert 1 digital shard into a physical Vote Shard."));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("power")) {
            sendPower(player);
            sendBuyButton(player);
            return true;
        }
        sendExplainer(player);
        return true;
    }

    private void sendPower(Player player) {
        int votes = plugin.voteShardService().listingVoteCount(player.getUniqueId());
        int issued = plugin.voteShardService().digitalIssued(player.getUniqueId());
        int ec = plugin.voteShardService().ecPower(player);
        ChatUi.banner(player, "Vote Shards");
        ChatUi.entry(player, "Listing", votes + " votes");
        ChatUi.entry(player, "Digital", issued + " issued");
        ChatUi.entry(player, "EC items", ec + " unused");
        ChatUi.tip(player, "Council uses digital EC = listing vote count. Physical /ec merge is not live.");
    }

    private void sendExplainer(Player player) {
        ChatUi.banner(player, "Vote Shards");
        ChatUi.entry(player, "What", "Council voting weight only — never Gold, homes, or combat");
        ChatUi.entry(player, "Paid", "$1 = 100 shards · Stripe min $0.50 (50 shards)");
        ChatUi.entry(player, "Listing", "/vote still +1 shard + tokens + 1–20 G · Lifetime sites ×2");
        ChatUi.entry(player, "Pro", "Paid Pro: 500 shards/mo + ×2 · Lifetime: 500/mo for life + ×3 · both ×5");
        ChatUi.entry(player, "Ava", "Locked 25% Council seat no matter the shard count");
        ChatUi.entry(player, "Power", "/voteshard power · full vote sites: /vote");
        sendBuyButton(player);
        ChatUi.links(player, "How shards work", LINK_GUIDE, "Membership", LINK_PRO);
        ChatUi.tip(player, "Also: /voteshard power · /voteshard craft · /vote · /donor");
    }

    private static void sendBuyButton(Player player) {
        ChatUi.links(player, "Buy Voteshards", LINK_BUY);
    }
}
