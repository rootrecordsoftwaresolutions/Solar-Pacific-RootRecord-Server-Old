package com.rootrecord.minecraft.rootranks.service;

import com.rootrecord.minecraft.common.RootMcTreasuryResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.rootranks.RootRanksPlugin;
import com.rootrecord.minecraft.rootranks.config.RankTier;
import com.rootrecord.minecraft.rootranks.config.RanksConfig;
import com.rootrecord.minecraft.rootranks.economy.RanksEconomy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public final class RankPurchaseService {

    public enum PurchaseOutcome {
        SUCCESS,
        MAX_RANK,
        INSUFFICIENT,
        APPLY_FAILED,
        NO_NEXT
    }

    public record RankStatus(
            String currentLabel,
            Optional<RankTier> nextTier,
            double balance,
            int ownedIndex) {}

    public record PurchaseResult(
            PurchaseOutcome outcome,
            RankTier tier,
            double price) {
    }

    private final RootRanksPlugin plugin;
    private final RanksConfig config;
    private final RanksEconomy economy;
    private final LuckPermsRankService luckPerms;

    public RankPurchaseService(
            RootRanksPlugin plugin,
            RanksConfig config,
            RanksEconomy economy,
            LuckPermsRankService luckPerms) {
        this.plugin = plugin;
        this.config = config;
        this.economy = economy;
        this.luckPerms = luckPerms;
    }

    public RankStatus status(UUID playerId) {
        int owned = luckPerms.highestOwnedIndex(config.ranks(), playerId);
        Optional<RankTier> next = nextTier(owned);
        String current = owned >= 0
                ? config.ranks().get(owned).display()
                : "Explorer";
        return new RankStatus(current, next, economy.balance(playerId), owned);
    }

    public Optional<RankTier> nextTier(int ownedIndex) {
        int nextIndex = ownedIndex + 1;
        if (nextIndex < 0 || nextIndex >= config.ranks().size()) {
            return Optional.empty();
        }
        return Optional.of(config.ranks().get(nextIndex));
    }

    public PurchaseResult tryPurchase(Player player, boolean bypassCost) {
        UUID uuid = player.getUniqueId();
        int owned = luckPerms.highestOwnedIndex(config.ranks(), uuid);
        Optional<RankTier> next = nextTier(owned);
        if (next.isEmpty()) {
            return new PurchaseResult(
                    owned >= config.ranks().size() - 1
                            ? PurchaseOutcome.MAX_RANK
                            : PurchaseOutcome.NO_NEXT,
                    null,
                    0.0);
        }

        RankTier tier = next.get();
        double price = tier.price();

        if (!bypassCost) {
            if (!economy.has(uuid, price)) {
                return new PurchaseResult(PurchaseOutcome.INSUFFICIENT, tier, price);
            }
            if (!economy.withdraw(uuid, price)) {
                return new PurchaseResult(PurchaseOutcome.INSUFFICIENT, tier, price);
            }
        }

        try {
            if (!luckPerms.grantGroup(player, tier)) {
                if (!bypassCost) {
                    refund(uuid, price);
                }
                return new PurchaseResult(PurchaseOutcome.APPLY_FAILED, tier, price);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("LuckPerms rank grant failed for "
                    + player.getName() + ": " + ex.getMessage());
            if (!bypassCost) {
                refund(uuid, price);
            }
            return new PurchaseResult(PurchaseOutcome.APPLY_FAILED, tier, price);
        }

        if (!bypassCost) {
            sinkToTreasury(player, tier, price);
        }

        return new PurchaseResult(PurchaseOutcome.SUCCESS, tier, price);
    }

    private void sinkToTreasury(Player player, RankTier tier, double price) {
        if (!config.treasurySink() || price <= 0) {
            return;
        }
        RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(plugin);
        if (treasury == null) {
            return;
        }
        treasury.settleClosedLoopPayment(
                player.getUniqueId(),
                player.getName(),
                price,
                "service-fee:rank-purchase:" + tier.id());
    }

    private void refund(UUID uuid, double amount) {
        if (amount <= 0) {
            return;
        }
        var rootRsp = Bukkit.getServicesManager().getRegistration(
                com.rootrecord.minecraft.common.RootMcEconomyService.class);
        if (rootRsp != null && rootRsp.getProvider() != null) {
            rootRsp.getProvider().deposit(uuid, amount);
            return;
        }
        var vaultRsp = Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (vaultRsp != null && vaultRsp.getProvider() != null) {
            vaultRsp.getProvider().depositPlayer(Bukkit.getOfflinePlayer(uuid), amount);
        }
    }
}
