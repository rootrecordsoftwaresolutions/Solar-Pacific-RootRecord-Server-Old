package com.rootrecord.minecraft.rootappreciation;

import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AppreciationService {

    private final RootAppreciationPlugin plugin;

    public AppreciationService(RootAppreciationPlugin plugin) {
        this.plugin = plugin;
    }

    public void giveTokens(Player target, int amount, String reason, boolean notifyTarget) {
        if (amount <= 0 || target == null) {
            return;
        }
        String batch = UUID.randomUUID().toString();
        // Keep ledger batches unique, but make the in-item PDC issue-id stable so multiple purchase/issue batches merge into fewer stacks.
        String stackIssueId = "rootmc-appreciation";
        int left = amount;
        while (left > 0) {
            int n = Math.min(64, left);
            ItemStack stack = plugin.tokens().create(plugin.config(), n, stackIssueId);
            Map<Integer, ItemStack> overflow = target.getInventory().addItem(stack);
            for (ItemStack drop : overflow.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), drop);
                target.sendMessage(plugin.msg("inventory-full"));
            }
            left -= n;
        }
        plugin.store().recordIssue(target.getUniqueId(), target.getName(), amount, reason, batch);
        if (notifyTarget) {
            target.sendMessage(plugin.msg("give-received").replace("{amount}", String.valueOf(amount)));
        }
    }

    /**
     * Queue tokens with an idempotency key. Delivers immediately when the player is online;
     * otherwise waits for join backfill.
     *
     * @return true if a new grant was queued (or already existed — still attempts online deliver)
     */
    public boolean grantOrQueue(UUID uuid, String username, int amount, String reason, String idempotencyKey) {
        if (uuid == null || amount <= 0 || idempotencyKey == null || idempotencyKey.isBlank()) {
            return false;
        }
        String name = username == null || username.isBlank() ? "player" : username;
        boolean inserted = plugin.store().enqueuePending(uuid, name, amount, reason, idempotencyKey);
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, () -> deliverPending(online));
        }
        return inserted;
    }

    /** Soft-SPI friendly: grant 1+ tokens for a vote row id (idempotent). */
    public boolean grantVoteTokens(UUID uuid, String username, int amount, long voteRowId) {
        if (voteRowId <= 0) {
            return false;
        }
        return grantOrQueue(uuid, username, amount, "vote", "vote:" + voteRowId);
    }

    /** Ensure every recorded vote has a pending/delivered token grant, then deliver inventory. */
    public int backfillVotesAndDeliver(Player player) {
        if (player == null) {
            return 0;
        }
        UUID uuid = player.getUniqueId();
        for (AppreciationStore.VoteRow vote : plugin.store().listVotesForPlayer(uuid)) {
            plugin.store().enqueuePending(uuid, player.getName(), 1, "vote", "vote:" + vote.id());
        }
        return deliverPending(player);
    }

    /** Deliver all undelivered pending grants into inventory + ledger. Returns tokens given. */
    public int deliverPending(Player player) {
        if (player == null || !player.isOnline()) {
            return 0;
        }
        List<AppreciationStore.PendingGrant> pending = plugin.store().listUndelivered(player.getUniqueId());
        if (pending.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (AppreciationStore.PendingGrant row : pending) {
            // Claim row first so parallel join/vote deliver cannot double-issue.
            if (!plugin.store().markDelivered(row.id())) {
                continue;
            }
            giveTokens(player, row.amount(), row.reason(), false);
            total += row.amount();
        }
        if (total > 0) {
            player.sendMessage(plugin.msg("pending-delivered").replace("{amount}", String.valueOf(total)));
        }
        return total;
    }

    public boolean redeemTokens(Player player, int amount) {
        if (amount != 1) {
            // One token = one roll. Multi-redeem runs sequentially.
            if (amount < 1) {
                player.sendMessage(plugin.msg("invalid-amount"));
                return false;
            }
            boolean any = false;
            for (int i = 0; i < amount; i++) {
                if (!redeemOne(player)) {
                    break;
                }
                any = true;
            }
            return any;
        }
        return redeemOne(player);
    }

    /** Redeem exactly one Appreciation Token for one weighted catalog reward. */
    public boolean redeemOne(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (!plugin.config().enabled()) {
            player.sendMessage(plugin.msg("disabled"));
            return false;
        }
        if (!plugin.config().redeemEnabled() || plugin.rewardPool() == null || !plugin.rewardPool().ready()) {
            player.sendMessage(plugin.msg("redeem-none"));
            return false;
        }
        if (!AppreciationRewardGranter.hasEmptySlot(player)) {
            player.sendMessage(plugin.msg("redeem-need-space"));
            return false;
        }
        int have = plugin.tokens().countInInventory(player);
        if (have < 1) {
            player.sendMessage(plugin.msg("redeem-need").replace("{amount}", "1"));
            return false;
        }
        AppreciationRewardPool.Reward reward = plugin.rewardPool().roll();
        if (reward == null) {
            player.sendMessage(plugin.msg("redeem-none"));
            return false;
        }
        int removed = plugin.tokens().removeFromInventory(player, 1);
        if (removed < 1) {
            player.sendMessage(plugin.msg("redeem-need").replace("{amount}", "1"));
            return false;
        }
        boolean granted = plugin.rewardGranter().grant(player, reward);
        if (!granted) {
            // Refund token if grant failed
            giveTokens(player, 1, "redeem-refund", false);
            player.sendMessage(plugin.msg("redeem-failed"));
            return false;
        }
        plugin.store().recordRedeem(player.getUniqueId(), player.getName(), 1, "catalog:" + reward.id());
        player.sendMessage(plugin.msg("redeem-success").replace("{reward}", reward.entry()));
        String broadcast = plugin.msg("redeem-broadcast")
                .replace("{player}", player.getName())
                .replace("{reward}", reward.entry());
        Bukkit.broadcastMessage(broadcast);
        return true;
    }

    public void claimBonus(Player player) {
        AppreciationConfig cfg = plugin.config();
        if (!cfg.bonusEnabled()) {
            player.sendMessage(plugin.msg("bonus-disabled"));
            return;
        }
        long now = System.currentTimeMillis();
        var streakOpt = plugin.store().playerStreak(player.getUniqueId());
        int nextDay = 1;
        if (streakOpt.isPresent()) {
            var streak = streakOpt.get();
            long last = streak.lastBonusAtMs();
            if (last > 0) {
                long since = now - last;
                if (since < cfg.cooldownMs()) {
                    long remain = cfg.cooldownMs() - since;
                    double hours = remain / 3_600_000.0;
                    player.sendMessage(plugin.msg("bonus-too-soon")
                            .replace("{hours}", String.format(Locale.US, "%.1f", hours)));
                    return;
                }
                if (since <= cfg.missAfterMs()) {
                    nextDay = Math.min(cfg.maxStreakDay(), streak.streakDay() + 1);
                    if (streak.streakDay() <= 0) {
                        nextDay = 1;
                    }
                } else {
                    nextDay = 1;
                }
            }
        }

        AppreciationConfig.DayReward reward = cfg.rewardForDay(nextDay);
        if (!plugin.store().updateStreak(player.getUniqueId(), player.getName(), nextDay, now)) {
            player.sendMessage(plugin.msg("bonus-db"));
            return;
        }
        giveTokens(player, reward.tokens(), "bonus-day-" + nextDay, false);
        if (reward.gold() > 0) {
            RootMcEconomyService eco = RootMcEconomyResolver.resolve(plugin);
            if (eco != null) {
                try {
                    eco.deposit(player.getUniqueId(), reward.gold());
                } catch (Exception ex) {
                    plugin.getLogger().warning("Bonus gold deposit failed: " + ex.getMessage());
                }
            }
        }
        player.sendMessage(plugin.msg("bonus-success")
                .replace("{day}", String.valueOf(nextDay))
                .replace("{tokens}", String.valueOf(reward.tokens()))
                .replace("{gold}", GoldMoney.format(reward.gold())));
    }

    public boolean bonusAvailable(UUID uuid) {
        AppreciationConfig cfg = plugin.config();
        if (!cfg.bonusEnabled()) {
            return false;
        }
        var streak = plugin.store().playerStreak(uuid);
        if (streak.isEmpty() || streak.get().lastBonusAtMs() <= 0) {
            return true;
        }
        return System.currentTimeMillis() - streak.get().lastBonusAtMs() >= cfg.cooldownMs();
    }

    public String nextBonusHint(UUID uuid) {
        AppreciationConfig cfg = plugin.config();
        var streak = plugin.store().playerStreak(uuid);
        int nextDay = 1;
        if (streak.isPresent() && streak.get().lastBonusAtMs() > 0) {
            long since = System.currentTimeMillis() - streak.get().lastBonusAtMs();
            if (since < cfg.cooldownMs()) {
                double hours = (cfg.cooldownMs() - since) / 3_600_000.0;
                return String.format(Locale.US, "in %.1fh", hours);
            }
            if (since <= cfg.missAfterMs()) {
                nextDay = Math.min(cfg.maxStreakDay(), Math.max(1, streak.get().streakDay()) + 1);
            }
        }
        AppreciationConfig.DayReward r = cfg.rewardForDay(nextDay);
        return "Day " + nextDay + " (" + r.tokens() + " tokens + " + GoldMoney.format(r.gold()) + " G)";
    }
}
