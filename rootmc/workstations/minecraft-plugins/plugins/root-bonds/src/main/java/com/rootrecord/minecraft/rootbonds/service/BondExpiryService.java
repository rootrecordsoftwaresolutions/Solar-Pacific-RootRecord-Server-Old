package com.rootrecord.minecraft.rootbonds.service;

import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.common.RootMcTreasuryResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.common.TreasuryLedgerType;
import com.rootrecord.minecraft.rootbonds.RootBondsPlugin;
import com.rootrecord.minecraft.rootbonds.data.BondsStore;
import com.rootrecord.minecraft.rootbonds.gui.BondsMenuRegistry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class BondExpiryService {

    private final RootBondsPlugin plugin;

    public BondExpiryService(RootBondsPlugin plugin) {
        this.plugin = plugin;
    }

    public void sweepExpiredCoupons() {
        BondsStore store = plugin.bonds().store();
        if (store == null || !plugin.bonds().enabled()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sweepLegacyCouponExpiry(store);
                sweepInactiveBondHolders(store);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Bond expiry sweep failed: " + ex.getMessage(), ex);
            }
        });
    }

    private void sweepLegacyCouponExpiry(BondsStore store) throws Exception {
        List<BondsStore.ForfeitRow> forfeited = store.forfeitExpiredLots();
        if (forfeited.isEmpty()) {
            return;
        }
        creditForfeitsToTreasury(forfeited);
        Map<UUID, Double> forfeitedByOwner = new HashMap<>();
        for (BondsStore.ForfeitRow row : forfeited) {
            forfeitedByOwner.merge(row.ownerUuid(), row.amountG(), Double::sum);
        }
        for (Map.Entry<UUID, Double> entry : forfeitedByOwner.entrySet()) {
            notifyPartialForfeit(entry.getKey(), GoldMoney.round(entry.getValue()));
        }
        plugin.getLogger().info("Forfeited " + forfeited.size() + " legacy expired bond coupon lot(s).");
        plugin.cloudSync().syncSnapshot(false);
        Bukkit.getScheduler().runTask(plugin, () -> plugin.menuRegistry().refreshOpenMenus());
    }

    private void sweepInactiveBondHolders(BondsStore store) throws Exception {
        int inactivityHours = plugin.bonds().config().claimExpiryHours();
        int graceDays = plugin.bonds().config().graceDays();
        Set<UUID> owners = new HashSet<>(store.listActiveBondOwnerUuids());
        int settled = 0;
        for (UUID owner : owners) {
            Player online = Bukkit.getPlayer(owner);
            if (online != null && online.isOnline()) {
                continue;
            }
            if (!store.hasBondPosition(owner)) {
                continue;
            }
            Instant lastLogin = resolveLastActive(owner, store.lastLoginAt(owner));
            if (lastLogin == null
                    || ChronoUnit.HOURS.between(lastLogin, Instant.now()) < inactivityHours) {
                continue;
            }
            double forfeitedG = store.forfeitAllUnclaimedForOwner(owner);
            if (forfeitedG > 0) {
                creditForfeitToTreasury(owner, forfeitedG);
            }
            BondService.AutoRedeemResult redeemed = plugin.bonds().autoRedeemAllForOwner(owner);
            store.beginInactivityTaxWindow(owner, graceDays);
            notifyInactivitySettlement(owner, forfeitedG, redeemed);
            settled++;
        }
        if (settled > 0) {
            plugin.getLogger().info(
                    "Bond inactivity settlement for " + settled + " holder(s) after "
                            + plugin.bonds().config().inactivityForfeitDays() + " days idle.");
            plugin.cloudSync().syncSnapshot(false);
            Bukkit.getScheduler().runTask(plugin, () -> plugin.menuRegistry().refreshOpenMenus());
        }
    }

    private static Instant resolveLastActive(UUID owner, Instant dbLogin) {
        if (Bukkit.getPlayer(owner) != null) {
            return Instant.now();
        }
        if (dbLogin != null) {
            return dbLogin;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(owner);
        long lastPlayed = offline.getLastPlayed();
        return lastPlayed > 0 ? Instant.ofEpochMilli(lastPlayed) : null;
    }

    private void creditForfeitsToTreasury(List<BondsStore.ForfeitRow> forfeited) {
        RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(plugin);
        if (treasury == null) {
            return;
        }
        for (BondsStore.ForfeitRow row : forfeited) {
            creditForfeitToTreasury(row.ownerUuid(), row.amountG());
        }
    }

    private void creditForfeitToTreasury(UUID owner, double amountG) {
        if (amountG < GoldMoney.MIN_AMOUNT) {
            return;
        }
        RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(plugin);
        if (treasury == null) {
            return;
        }
        treasury.creditTreasury(
                amountG,
                TreasuryLedgerType.BOND_COUPON_FORFEIT,
                owner,
                "bond-forfeit",
                "inactive-or-expired");
    }

    private void notifyPartialForfeit(UUID owner, double forfeitedG) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(owner);
            if (player == null || !player.isOnline()) {
                return;
            }
            player.sendMessage(BondsMenuRegistry.legacyColor(
                    plugin.msg("claim-forfeited")
                            .replace("{amount}", GoldMoney.format(forfeitedG))));
        });
    }

    private void notifyInactivitySettlement(
            UUID owner,
            double forfeitedG,
            BondService.AutoRedeemResult redeemed) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(owner);
            if (player == null || !player.isOnline()) {
                return;
            }
            String body = plugin.msg("bond-inactivity-settlement")
                    .replace("{days}", String.valueOf(plugin.bonds().config().inactivityForfeitDays()))
                    .replace("{forfeited}", GoldMoney.format(forfeitedG))
                    .replace("{bonds}", String.valueOf(redeemed.bondCount()))
                    .replace("{principal}", GoldMoney.format(redeemed.principalG()));
            player.sendMessage(BondsMenuRegistry.legacyColor(body));
        });
    }
}
