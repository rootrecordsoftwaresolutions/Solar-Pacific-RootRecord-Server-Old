package com.rootrecord.minecraft.rootbonds.service;

import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.common.RootMcTreasuryResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.common.TreasuryLedgerType;
import com.rootrecord.minecraft.rootbonds.RootBondsPlugin;
import com.rootrecord.minecraft.rootbonds.config.BondsConfig;
import com.rootrecord.minecraft.rootbonds.data.BondsStore;
import com.rootrecord.minecraft.rootbonds.item.BondCertificate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class BondService {

    public static final String BONDED_NOTE_LABEL = "Bonded note";
    public static final String BONDED_ROOT_LABEL = "Bonded Root";

    private final RootBondsPlugin plugin;
    private volatile BondsConfig config;
    private volatile BondsStore store;
    private volatile BondCertificate certificates;

    public BondService(RootBondsPlugin plugin) {
        this.plugin = plugin;
        this.certificates = new BondCertificate(plugin);
    }

    public void reload(BondsConfig config, BondsStore store) {
        this.config = config;
        this.store = store;
        this.certificates = new BondCertificate(plugin);
    }

    public boolean enabled() {
        return config != null && config.enabled() && store != null && resolveTreasury() != null;
    }

    public boolean createBond(Player player, double amount) {
        if (!enabled() || player == null) {
            return false;
        }
        double principal = GoldMoney.round(amount);
        if (principal + 1e-9 < config.minPrincipalG()) {
            return false;
        }
        RootMcEconomyService economy = RootMcEconomyResolver.resolve(plugin);
        RootMcTreasuryService treasury = resolveTreasury();
        if (economy == null || treasury == null) {
            return false;
        }
        UUID owner = player.getUniqueId();
        double balance = economy.balance(owner);
        if (balance + 1e-9 < principal) {
            return false;
        }
        if (!economy.withdraw(owner, principal)) {
            return false;
        }
        treasury.creditTreasury(
                principal,
                TreasuryLedgerType.BOND_ISSUE,
                owner,
                player.getName(),
                "bonded-note");
        UUID bondId = UUID.randomUUID();
        Instant issued = Instant.now();
        BondsStore.BondRow row = new BondsStore.BondRow(
                bondId, owner, player.getName(), BONDED_NOTE_LABEL, principal, issued);
        try {
            store.insertBond(row);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Bond insert failed — manual treasury adjustment may be needed", ex);
            economy.deposit(owner, principal);
            return false;
        }
        ItemStack cert = certificates.create(bondId, principal, issued);
        player.getInventory().addItem(cert).values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> plugin.cloudSync().syncSnapshot(false));
        return true;
    }

    public boolean createBondedRoot(Player player) {
        if (!enabled() || player == null) {
            return false;
        }
        RootMcEconomyService economy = RootMcEconomyResolver.resolve(plugin);
        if (economy == null) {
            return false;
        }
        double principal = GoldMoney.round(config.bondedRootCostG());
        UUID owner = player.getUniqueId();
        double balance = economy.balance(owner);
        if (balance + 1e-9 < principal || !economy.withdraw(owner, principal)) {
            return false;
        }
        auditBondedRootBurn(resolveTreasury(), principal, player);
        UUID bondId = UUID.randomUUID();
        Instant issued = Instant.now();
        ItemStack cert = certificates.createRoot(bondId, principal, issued);
        player.getInventory().addItem(cert).values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        if (config.bondedRootRegisterForEarnings()) {
            registerBondedRoot(player, bondId, issued);
        }
        return true;
    }

    public boolean ensureBondedRootRegistered(Player player, ItemStack item) {
        if (!enabled() || player == null || item == null || !certificates.isBondedRoot(item)) {
            return false;
        }
        if (!config.bondedRootRegisterForEarnings()) {
            return false;
        }
        UUID bondId = certificates.readBondId(item);
        if (bondId == null) {
            return false;
        }
        try {
            if (store.findBond(bondId).isPresent()) {
                return true;
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Bonded Root lookup failed: " + ex.getMessage(), ex);
            return false;
        }
        return registerBondedRoot(player, bondId, Instant.now());
    }

    private boolean registerBondedRoot(Player player, UUID bondId, Instant issued) {
        BondsStore.BondRow row = new BondsStore.BondRow(
                bondId,
                player.getUniqueId(),
                player.getName(),
                BONDED_ROOT_LABEL,
                GoldMoney.round(config.bondedRootCostG()),
                issued);
        try {
            store.insertBond(row);
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> plugin.cloudSync().syncSnapshot(false));
            return true;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Bonded Root register failed: " + ex.getMessage(), ex);
            return false;
        }
    }

    private void auditBondedRootBurn(RootMcTreasuryService treasury, double principal, Player player) {
        if (treasury == null || player == null || principal <= 0) {
            return;
        }
        try {
            treasury.getClass()
                    .getMethod("burnNotes", double.class, UUID.class, String.class, String.class)
                    .invoke(treasury, principal, player.getUniqueId(), player.getName(), "bonded-root:create");
        } catch (NoSuchMethodException ignored) {
            plugin.getLogger().fine("Treasury burn audit unavailable for Bonded Root creation.");
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Bonded Root burn audit failed: " + ex.getMessage(), ex);
        }
    }

    public boolean redeemBond(Player player, UUID bondId) {
        if (!enabled() || player == null || bondId == null) {
            return false;
        }
        RootMcTreasuryService treasury = resolveTreasury();
        if (treasury == null) {
            return false;
        }
        try {
            Optional<BondsStore.BondRow> bondOpt = store.findBond(bondId);
            if (bondOpt.isEmpty() || !bondOpt.get().ownerUuid().equals(player.getUniqueId())) {
                return false;
            }
            BondsStore.BondRow bond = bondOpt.get();
            if (isBondedRoot(bond)) {
                return false;
            }
            if (!treasury.debitTreasuryPhysical(
                    bond.principal(),
                    TreasuryLedgerType.BOND_REDEEM,
                    player.getUniqueId(),
                    player.getName(),
                    "bond:" + bond.displayName())) {
                return false;
            }
            store.markRedeemed(bondId);
            removeCertificateFromPlayer(player, bondId);
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> plugin.cloudSync().syncSnapshot(false));
            return plugin.menuRegistry().payoutPhysical(player, bond.principal());
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Bond redeem failed: " + ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * After all unclaimed coupon lots expire: redeem every active bonded note for the owner.
     * Online players receive physical gold; offline players are credited to wallet.
     */
    public AutoRedeemResult autoRedeemAllForOwner(UUID ownerUuid) {
        if (!enabled() || ownerUuid == null) {
            return AutoRedeemResult.EMPTY;
        }
        RootMcTreasuryService treasury = resolveTreasury();
        if (treasury == null) {
            return AutoRedeemResult.EMPTY;
        }
        try {
            var active = store.listActiveForOwner(ownerUuid);
            if (active.isEmpty()) {
                return AutoRedeemResult.EMPTY;
            }
            Player online = Bukkit.getPlayer(ownerUuid);
            boolean physical = online != null && online.isOnline();
            int redeemed = 0;
            double principalTotal = 0;
            for (BondsStore.BondRow bond : active) {
                if (isBondedRoot(bond)) {
                    continue;
                }
                if (!redeemBondRow(treasury, bond, online, physical)) {
                    plugin.getLogger().warning(
                            "Bond auto-redeem failed for " + ownerUuid + " note " + bond.id());
                    continue;
                }
                redeemed++;
                principalTotal += bond.principal();
            }
            if (redeemed > 0) {
                plugin.getServer().getScheduler().runTaskAsynchronously(
                        plugin, () -> plugin.cloudSync().syncSnapshot(false));
            }
            return new AutoRedeemResult(redeemed, GoldMoney.round(principalTotal), physical);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Bond auto-redeem failed: " + ex.getMessage(), ex);
            return AutoRedeemResult.EMPTY;
        }
    }

    private boolean redeemBondRow(
            RootMcTreasuryService treasury,
            BondsStore.BondRow bond,
            Player online,
            boolean physical) throws Exception {
        UUID owner = bond.ownerUuid();
        String ownerName = bond.ownerName() == null ? owner.toString().substring(0, 8) : bond.ownerName();
        boolean paid;
        if (physical && online != null) {
            paid = treasury.debitTreasuryPhysical(
                    bond.principal(),
                    TreasuryLedgerType.BOND_REDEEM,
                    owner,
                    ownerName,
                    "bond-expiry:" + bond.displayName());
            if (!paid) {
                return false;
            }
            store.markRedeemed(bond.id());
            double principal = bond.principal();
            UUID bondId = bond.id();
            Bukkit.getScheduler().runTask(plugin, () -> {
                removeCertificateFromPlayer(online, bondId);
                plugin.menuRegistry().payoutPhysical(online, principal);
            });
            return true;
        }
        paid = treasury.payBondRedeemWallet(
                owner,
                ownerName,
                bond.principal(),
                "bond-expiry:" + bond.id());
        if (!paid) {
            return false;
        }
        store.markRedeemed(bond.id());
        return true;
    }

    public boolean collectAccrued(Player player, double amountG) {
        if (!enabled() || player == null || amountG < GoldMoney.MIN_AMOUNT) {
            return false;
        }
        if (BondReserveGate.payoutsPaused(plugin)) {
            return false;
        }
        RootMcTreasuryService treasury = resolveTreasury();
        if (treasury == null) {
            return false;
        }
        try {
            if (!store.takeAccrued(player.getUniqueId(), amountG)) {
                return false;
            }
            if (!treasury.debitTreasuryPhysical(
                    amountG,
                    TreasuryLedgerType.BOND_COUPON,
                    player.getUniqueId(),
                    player.getName(),
                    "coupon")) {
                store.addCouponLot(player.getUniqueId(), amountG, config.claimExpiryHours());
                return false;
            }
            return true;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Bond coupon collect failed: " + ex.getMessage(), ex);
            return false;
        }
    }

    public boolean payPlayerEarning(UUID ownerUuid, String ownerName, double amountG, String details) {
        if (amountG < GoldMoney.MIN_AMOUNT || ownerUuid == null) {
            return false;
        }
        if (BondReserveGate.payoutsPaused(plugin)) {
            return false;
        }
        RootMcTreasuryService treasury = resolveTreasury();
        if (treasury == null) {
            return false;
        }
        return treasury.payBondCouponWallet(
                ownerUuid,
                ownerName == null || ownerName.isBlank()
                        ? ownerUuid.toString().substring(0, 8)
                        : ownerName,
                amountG,
                details == null ? "bond-earning" : details)
                && notifyWalletEarning(ownerUuid, amountG);
    }

    private boolean notifyWalletEarning(UUID ownerUuid, double amountG) {
        Player player = Bukkit.getPlayer(ownerUuid);
        if (player == null || !player.isOnline() || amountG < GoldMoney.MIN_AMOUNT) {
            return true;
        }
        player.sendMessage(com.rootrecord.minecraft.rootbonds.gui.BondsMenuRegistry.legacyColor(
                plugin.msg("bond-earning-wallet").replace("{amount}", GoldMoney.format(amountG))));
        return true;
    }

    public boolean payGovernmentCoupon(UUID bankUuid, String bankName, double amountG, String details) {
        if (amountG < GoldMoney.MIN_AMOUNT || bankUuid == null || bankName == null || bankName.isBlank()) {
            return false;
        }
        if (BondReserveGate.payoutsPaused(plugin)) {
            return false;
        }
        BondIncomeService income = plugin.bondIncome();
        if (income != null && income.isGovernmentSuspended(bankUuid)) {
            return false;
        }
        RootMcTreasuryService treasury = resolveTreasury();
        if (treasury == null) {
            return false;
        }
        if (!treasury.payBondCouponWallet(bankUuid, bankName, amountG, details)) {
            return false;
        }
        GovernmentBondNotifier.notifyDeposit(plugin, details, amountG);
        return true;
    }

    private void removeCertificateFromPlayer(Player player, UUID bondId) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            UUID id = certificates.readBondId(contents[i]);
            if (bondId.equals(id)) {
                contents[i] = null;
            }
        }
        player.getInventory().setContents(contents);
        ItemStack[] ender = player.getEnderChest().getContents();
        for (int i = 0; i < ender.length; i++) {
            UUID id = certificates.readBondId(ender[i]);
            if (bondId.equals(id)) {
                ender[i] = null;
            }
        }
        player.getEnderChest().setContents(ender);
    }

    public Optional<BondHeartbeatSummary> heartbeatSummary(UUID ownerUuid) {
        if (!enabled() || store == null || ownerUuid == null) {
            return Optional.empty();
        }
        try {
            var active = store.listActiveForOwner(ownerUuid);
            double principal = 0;
            for (BondsStore.BondRow row : active) {
                principal += row.principal();
            }
            BondsStore.AccruedRow accrued = store.accrued(ownerUuid);
            return Optional.of(new BondHeartbeatSummary(
                    active.size(),
                    GoldMoney.round(principal),
                    GoldMoney.round(accrued.accruedG()),
                    GoldMoney.round(accrued.lifetimeEarnedG())));
        } catch (Exception ex) {
            plugin.getLogger().log(Level.FINE, "Bond heartbeat summary failed: " + ex.getMessage());
            return Optional.empty();
        }
    }

    public record BondHeartbeatSummary(
            int activeBonds,
            double principalG,
            double uncollectedG,
            double lifetimeEarnedG) {}

    public record AutoRedeemResult(int bondCount, double principalG, boolean physicalPayout) {
        static final AutoRedeemResult EMPTY = new AutoRedeemResult(0, 0, false);
    }

    public BondsStore store() {
        return store;
    }

    public BondsConfig config() {
        return config;
    }

    public static boolean isBondedRoot(BondsStore.BondRow bond) {
        return bond != null && BONDED_ROOT_LABEL.equalsIgnoreCase(bond.displayName());
    }

    public BondCertificate certificates() {
        return certificates;
    }

    private RootMcTreasuryService resolveTreasury() {
        return RootMcTreasuryResolver.resolve(plugin);
    }
}
