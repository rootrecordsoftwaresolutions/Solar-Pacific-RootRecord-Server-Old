package com.rootrecord.minecraft.rootmemberships;

import com.rootrecord.minecraft.common.ChatUi;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Player-facing membership explainer: {@code /donor}, {@code /pro}, {@code /lifetime}.
 *
 * Checkout uses masked Pages URLs ({@code https://rootmc.net/pro/...}) that redirect to Stripe.
 */
public final class ProCommand implements CommandExecutor, TabCompleter {

    static final String LINK_PRO = "https://rootmc.net/pro/";
    static final String LINK_MONTHLY = "https://rootmc.net/pro/monthly/";
    static final String LINK_ONE_MONTH = "https://rootmc.net/pro/one-month/";
    static final String LINK_LIFETIME = "https://rootmc.net/pro/lifetime/";
    static final String LINK_VOTE_SHARDS = "https://rootmc.net/pro/vote-shards/";
    static final String LINK_SHARD_GUIDE = "https://rootmc.net/vote-shards/";
    static final String LINK_VERIFY = "https://rootmc.net/verify";
    static final String LINK_COUNCIL = "https://rootmc.net/council/";
    static final String LINK_THANKS = "https://rootmc.net/thanks/";
    static final String LINK_WEEKLY = "https://rootmc.net/wiki/weekly-awards/";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args != null && args.length > 0) {
            player.sendMessage("§eUsage: /donor  /pro  /lifetime");
            return true;
        }
        sendPanel(player, focusOf(label));
        return true;
    }

    private static String focusOf(String label) {
        String key = label == null ? "donor" : label.trim().toLowerCase();
        return switch (key) {
            case "pro" -> "pro";
            case "lifetime" -> "lifetime";
            default -> "donor";
        };
    }

    private static void sendPanel(Player player, String focus) {
        ChatUi.banner(player, switch (focus) {
            case "pro" -> "RootMC Pro";
            case "lifetime" -> "Lifetime Pro";
            default -> "Support RootMC";
        });
        ChatUi.tip(player, "Not P2W. Cosmetics, extra homes, ad-free app, Council Vote Shards.");
        ChatUi.tip(player, "Link first: /link then rootmc.net/verify. Exact Minecraft name at checkout.");
        ChatUi.blank(player);

        if ("lifetime".equals(focus)) {
            sendLifetimePerks(player);
            sendProPerks(player);
            sendPlans(player);
        } else if ("pro".equals(focus)) {
            sendProPerks(player);
            sendLifetimePerks(player);
            sendPlans(player);
        } else {
            sendPlans(player);
            sendProPerks(player);
            sendLifetimePerks(player);
        }

        sendAppPerks(player);
        sendDevGate(player);
        sendShardInfo(player);
        sendCheckout(player);
        sendFreePro(player);

        ChatUi.links(
                player,
                "Guide", LINK_PRO,
                "Verify", LINK_VERIFY,
                "Council", LINK_COUNCIL,
                "Thanks", LINK_THANKS);
        ChatUi.tip(player, "Also: /donor  /pro  /lifetime  /vote  /voteshard");
    }

    private static void sendPlans(Player player) {
        ChatUi.section(player, "Plans");
        ChatUi.entry(player, "Monthly", "$4.99/mo · subscription Pro while billing is active");
        ChatUi.entry(player, "Voucher", "$4.99 · 30 days in /vault · tradeable until redeem · +500 tokens");
        ChatUi.entry(player, "Lifetime", "$75 once · forever Pro · /vault voucher · +10,000 tokens");
        ChatUi.entry(player, "Shards", "$1 = 100 Vote Shards · min $0.50 · Council weight only");
        ChatUi.links(
                player,
                "Subscribe", LINK_MONTHLY,
                "Buy voucher", LINK_ONE_MONTH,
                "Buy Lifetime", LINK_LIFETIME,
                "Buy Voteshards", LINK_VOTE_SHARDS);
        ChatUi.blank(player);
    }

    private static void sendProPerks(Player player) {
        ChatUi.section(player, "Pro perks");
        ChatUi.entry(player, "Badge", "[Pro] chat prefix");
        ChatUi.entry(player, "Cosmetics", "/nick · /hat · colored chat · colored signs · custom join");
        ChatUi.entry(player, "Homes", "5 /sethome (default players get 3)");
        ChatUi.entry(player, "Council", "×2 vote weight + 500 Vote Shards / month (paid only)");
        ChatUi.tip(player, "Weekly award Pro = cosmetics + app only. No shards and no ×2.");
        ChatUi.blank(player);
    }

    private static void sendLifetimePerks(Player player) {
        ChatUi.section(player, "Lifetime extras");
        ChatUi.entry(player, "Badge", "[Lifetime] prefix + RGB chat");
        ChatUi.entry(player, "Homes", "8 /sethome");
        ChatUi.entry(player, "Council", "×3 (×5 with paid Pro) · listing votes ×2 · 500 shards/mo for life");
        ChatUi.entry(player, "Includes", "All Pro perks, forever after /vault redeem");
        ChatUi.blank(player);
    }

    private static void sendAppPerks(Player player) {
        ChatUi.section(player, "App & web");
        ChatUi.entry(player, "Ads", "Ad-free RootMC Android app");
        ChatUi.entry(player, "AI", "100 world reports / month (free: 1 per day)");
        ChatUi.entry(player, "Groups", "Own 5 realm groups (free: 1)");
        ChatUi.entry(player, "Same", "Stats, market, vault, notes, waypoints, daily intel");
        ChatUi.blank(player);
    }

    private static void sendDevGate(Player player) {
        ChatUi.section(player, "Ava development");
        ChatUi.entry(player, "Need", "8 paid members (Pro + Lifetime) or $40/mo — else new development + support may pause");
        ChatUi.entry(player, "Best", "$200/mo development allowance · surplus also funds hardware upgrades");
        ChatUi.tip(player, "Only paid memberships count. Weekly award / unpaid Pro is not shown here.");
        ChatUi.links(player, "Live count", LINK_PRO, "Council", LINK_COUNCIL);
        ChatUi.blank(player);
    }

    private static void sendShardInfo(Player player) {
        ChatUi.section(player, "Vote Shards");
        ChatUi.entry(player, "What", "Council voting weight only — never Gold, homes, or combat");
        ChatUi.entry(player, "Paid", "$1 = 100 shards · Stripe min $0.50 (50 shards)");
        ChatUi.entry(player, "Listing", "/vote still +1 shard + tokens + 1–20 G · Lifetime sites ×2");
        ChatUi.entry(player, "Stack", "Paid Pro ×2 · Lifetime ×3 · both ×5");
        ChatUi.links(
                player,
                "Buy Voteshards", LINK_VOTE_SHARDS,
                "How shards work", LINK_SHARD_GUIDE);
        ChatUi.blank(player);
    }

    private static void sendCheckout(Player player) {
        ChatUi.section(player, "Checkout");
        ChatUi.entry(player, "1", "Link: /link then rootmc.net/verify");
        ChatUi.entry(player, "2", "Stripe: enter beneficiary Minecraft name (case-sensitive)");
        ChatUi.entry(player, "3", "Subscription activates in minutes while billing stays active");
        ChatUi.entry(player, "4", "Vouchers: claim /vault → right-click redeem · gift/trade until then");
        ChatUi.tip(player, "No /heal /feed /repair for anyone. Membership is not pay-to-win.");
        ChatUi.blank(player);
    }

    private static void sendFreePro(Player player) {
        ChatUi.section(player, "Earn Pro free");
        ChatUi.entry(player, "Weekly", "#1 Top Active Player gets 1 week of Pro");
        ChatUi.entry(player, "Top 3", "[Top Active Player] prefix (no automatic Pro)");
        ChatUi.entry(player, "Note", "Award Pro does not grant shards or ×2");
        ChatUi.links(player, "Weekly awards", LINK_WEEKLY);
        ChatUi.blank(player);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
