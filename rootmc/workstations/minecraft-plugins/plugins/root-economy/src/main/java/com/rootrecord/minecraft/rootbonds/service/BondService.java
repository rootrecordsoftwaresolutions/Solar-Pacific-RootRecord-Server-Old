package com.rootrecord.minecraft.rootbonds.service;

import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.common.RootMcEnderChestResolver;
import com.rootrecord.minecraft.common.RootMcEnderChestService;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
        RootMcEconomyService economy = RootMcEconomyResolver.resolve(plugin.host());
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
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin.host(), () -> plugin.cloudSync().syncSnapshot(false));
        return true;
    }

    public boolean createBondedRoot(Player player) {
        if (!enabled() || player == null) {
            return false;
        }
        RootMcEconomyService economy = RootMcEconomyResolver.resolve(plugin.host());
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
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin.host(), () -> plugin.cloudSync().syncSnapshot(false));
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
        return redeemBondDetail(player, bondId) == null;
    }

    /**
     * @return null on success, otherwise a short player-facing reason
     */
    public String redeemBondDetail(Player player, UUID bondId) {
        if (!enabled() || player == null || bondId == null) {
            return "Bonds are disabled.";
        }
        RootMcTreasuryService treasury = resolveTreasury();
        if (treasury == null) {
            return "Treasury unavailable — try again shortly.";
        }
        try {
            Optional<BondsStore.BondRow> bondOpt = store.findBond(bondId);
            if (bondOpt.isEmpty()) {
                return "That note is not in the bond ledger (already redeemed or invalid).";
            }
            BondsStore.BondRow bond = bondOpt.get();
            if (isBondedRoot(bond)) {
                return "Bonded Root principal cannot be redeemed.";
            }
            if (!playerHasCertificate(player, bondId)) {
                return "Hold this bonded note (or keep it in /ec) to redeem.";
            }
            if (!bond.ownerUuid().equals(player.getUniqueId())) {
                store.transferOwner(bondId, player.getUniqueId(), player.getName());
            }
            if (!treasury.payBondRedeemWallet(
                    player.getUniqueId(),
                    player.getName(),
                    bond.principal(),
                    "bond:" + bond.displayName())) {
                return "Reserve could not pay this redeem — check Server Reserve balance.";
            }
            store.markRedeemed(bondId);
            removeCertificateFromPlayer(player, bondId);
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin.host(), () -> plugin.cloudSync().syncSnapshot(false));
            return null;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Bond redeem failed: " + ex.getMessage(), ex);
            return "Redemption failed — see server log.";
        }
    }

    /**
     * After all unclaimed coupon lots expire: redeem every active bonded note for the owner
     * as wallet Notes (no physical gold).
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
            int redeemed = 0;
            double principalTotal = 0;
            for (BondsStore.BondRow bond : active) {
                if (isBondedRoot(bond)) {
                    continue;
                }
                if (!redeemBondRow(treasury, bond, online)) {
                    plugin.getLogger().warning(
                            "Bond auto-redeem failed for " + ownerUuid + " note " + bond.id());
                    continue;
                }
                redeemed++;
                principalTotal += bond.principal();
            }
            if (redeemed > 0) {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin.host(), () -> plugin.cloudSync().syncSnapshot(false));
            }
            return new AutoRedeemResult(redeemed, GoldMoney.round(principalTotal), false);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Bond auto-redeem failed: " + ex.getMessage(), ex);
            return AutoRedeemResult.EMPTY;
        }
    }

    private boolean redeemBondRow(
            RootMcTreasuryService treasury,
            BondsStore.BondRow bond,
            Player online) throws Exception {
        UUID owner = bond.ownerUuid();
        String ownerName = bond.ownerName() == null ? owner.toString().substring(0, 8) : bond.ownerName();
        boolean paid = treasury.payBondRedeemWallet(
                owner,
                ownerName,
                bond.principal(),
                "bond-expiry:" + bond.id());
        if (!paid) {
            return false;
        }
        store.markRedeemed(bond.id());
        if (online != null && online.isOnline()) {
            UUID bondId = bond.id();
            Bukkit.getScheduler().runTask(plugin.host(), () -> removeCertificateFromPlayer(online, bondId));
        }
        return true;
    }

    public boolean collectAccrued(Player player, double amountG) {
        if (!enabled() || player == null || amountG < GoldMoney.MIN_AMOUNT) {
            return false;
        }
        if (BondReserveGate.payoutsPaused(plugin.host())) {
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
            if (!treasury.payBondCouponWallet(
                    player.getUniqueId(),
                    player.getName(),
                    amountG,
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

    /**
     * Merge every redeemable bonded note in the player's /ec into one certificate.
     * Does not withdraw principal from the reserve.
     */
    public MergeResult mergeInventoryBonds(Player player) {
        if (!enabled() || player == null) {
            return MergeResult.FAILED;
        }
        BondCertificate certs = certificates;
        ItemStack[] contents = enderContents(player).clone();
        LinkedHashMap<UUID, Integer> slotByBond = new LinkedHashMap<>();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || certs.isBondedRoot(stack)) {
                continue;
            }
            UUID bondId = certs.readBondId(stack);
            if (bondId == null || slotByBond.containsKey(bondId)) {
                continue;
            }
            slotByBond.put(bondId, i);
        }
        if (slotByBond.size() < 2) {
            return MergeResult.NEED_TWO;
        }
        try {
            List<UUID> ids = new ArrayList<>(slotByBond.keySet());
            for (UUID bondId : ids) {
                Optional<BondsStore.BondRow> row = store.findBond(bondId);
                if (row.isEmpty() || isBondedRoot(row.get())) {
                    return MergeResult.INVALID;
                }
                BondsStore.BondRow bond = row.get();
                if (!bond.ownerUuid().equals(player.getUniqueId())) {
                    store.transferOwner(bondId, player.getUniqueId(), player.getName());
                }
            }
            BondsStore.BondRow merged = store.mergeActiveBonds(
                    ids, player.getUniqueId(), player.getName(), BONDED_NOTE_LABEL);
            if (merged == null) {
                return MergeResult.FAILED;
            }
            for (int slot : slotByBond.values()) {
                contents[slot] = null;
            }
            ItemStack note = certs.create(merged.id(), merged.principal(), merged.issuedAt());
            boolean placed = false;
            for (int i = 0; i < contents.length; i++) {
                if (contents[i] == null) {
                    contents[i] = note;
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                contents[0] = note;
            }
            writeEnderContents(player, contents);
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin.host(), () -> plugin.cloudSync().syncSnapshot(false));
            return new MergeResult(true, MergeStatus.OK, slotByBond.size(), merged.principal());
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Bond merge failed: " + ex.getMessage(), ex);
            return MergeResult.FAILED;
        }
    }

    public boolean payPlayerEarning(UUID ownerUuid, String ownerName, double amountG, String details) {
        if (amountG < GoldMoney.MIN_AMOUNT || ownerUuid == null) {
            return false;
        }
        if (BondReserveGate.payoutsPaused(plugin.host())) {
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

    /** Chat ping after MC-day compound; principal already increased in MySQL. */
    public void notifyCompoundEarning(UUID ownerUuid, double amountG) {
        if (ownerUuid == null || amountG < GoldMoney.MIN_AMOUNT) {
            return;
        }
        Player player = Bukkit.getPlayer(ownerUuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        refreshHeldCertificates(player);
        player.sendMessage(com.rootrecord.minecraft.rootbonds.gui.BondsMenuRegistry.legacyColor(
                plugin.msg("bond-earning-compound").replace("{amount}", GoldMoney.format(amountG))));
    }

    /** Sync paper note face values with DB principal after compound. */
    public void refreshHeldCertificates(Player player) {
        if (player == null || !enabled()) {
            return;
        }
        try {
            for (BondsStore.BondRow bond : store.listActiveForOwner(player.getUniqueId())) {
                if (isBondedRoot(bond)) {
                    continue;
                }
                updateCertificatePrincipal(player, bond.id(), bond.principal(), bond.issuedAt());
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.FINE, "Bond certificate refresh failed: " + ex.getMessage());
        }
    }

    private void updateCertificatePrincipal(Player player, UUID bondId, double principal, Instant issuedAt) {
        ItemStack[] ender = enderContents(player).clone();
        boolean enderChanged = false;
        for (int i = 0; i < ender.length; i++) {
            UUID id = certificates.readBondId(ender[i]);
            if (bondId.equals(id) && !certificates.isBondedRoot(ender[i])) {
                ender[i] = certificates.create(bondId, principal, issuedAt != null ? issuedAt : Instant.now());
                enderChanged = true;
            }
        }
        if (enderChanged) {
            writeEnderContents(player, ender);
        }
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
        if (BondReserveGate.payoutsPaused(plugin.host())) {
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
        ItemStack[] ender = enderContents(player).clone();
        boolean enderChanged = false;
        for (int i = 0; i < ender.length; i++) {
            UUID id = certificates.readBondId(ender[i]);
            if (bondId.equals(id)) {
                ender[i] = null;
                enderChanged = true;
            }
        }
        if (enderChanged) {
            writeEnderContents(player, ender);
        }
        var inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        boolean invChanged = false;
        for (int i = 0; i < contents.length; i++) {
            if (bondId.equals(certificates.readBondId(contents[i]))) {
                contents[i] = null;
                invChanged = true;
            }
        }
        if (invChanged) {
            inv.setContents(contents);
        }
    }

    private boolean playerHasCertificate(Player player, UUID bondId) {
        if (player == null || bondId == null) {
            return false;
        }
        if (bondId.equals(certificates.readBondId(player.getInventory().getItemInMainHand()))
                || bondId.equals(certificates.readBondId(player.getInventory().getItemInOffHand()))) {
            return true;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (bondId.equals(certificates.readBondId(stack))) {
                return true;
            }
        }
        return certificateInEnder(player, bondId);
    }

    private boolean certificateInEnder(Player player, UUID bondId) {
        for (ItemStack stack : enderContents(player)) {
            if (bondId.equals(certificates.readBondId(stack))) {
                return true;
            }
        }
        return false;
    }

    private ItemStack[] enderContents(Player player) {
        RootMcEnderChestService ec = RootMcEnderChestResolver.resolve(plugin.host());
        if (ec != null) {
            return ec.contents(player.getUniqueId());
        }
        return player.getEnderChest().getContents();
    }

    private void writeEnderContents(Player player, ItemStack[] contents) {
        RootMcEnderChestService ec = RootMcEnderChestResolver.resolve(plugin.host());
        if (ec != null) {
            var inv = ec.inventory(player);
            inv.setContents(contents);
            ec.save(player);
            return;
        }
        player.getEnderChest().setContents(contents);
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

    public enum MergeStatus {
        OK,
        NEED_TWO,
        INVALID,
        FAILED
    }

    public record MergeResult(boolean success, MergeStatus status, int mergedCount, double principalG) {
        static final MergeResult NEED_TWO = new MergeResult(false, MergeStatus.NEED_TWO, 0, 0);
        static final MergeResult INVALID = new MergeResult(false, MergeStatus.INVALID, 0, 0);
        static final MergeResult FAILED = new MergeResult(false, MergeStatus.FAILED, 0, 0);
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

    /**
     * Ban confiscation: forfeit coupons to reserve and void active bond notes without paying the owner.
     * Principal stays in the closed loop (no wallet payout).
     */
    public SeizeResult seizeAllToReserve(UUID ownerUuid, String ownerName) {
        if (!enabled() || ownerUuid == null) {
            return SeizeResult.EMPTY;
        }
        RootMcTreasuryService treasury = resolveTreasury();
        if (treasury == null) {
            return SeizeResult.EMPTY;
        }
        String name = ownerName == null || ownerName.isBlank() ? ownerUuid.toString().substring(0, 8) : ownerName;
        double coupons = 0;
        int notes = 0;
        double principal = 0;
        try {
            double forfeited = store.forfeitAllUnclaimedForOwner(ownerUuid);
            if (forfeited > 0) {
                treasury.creditTreasury(
                        forfeited,
                        com.rootrecord.minecraft.common.TreasuryLedgerType.BOND_COUPON_FORFEIT,
                        ownerUuid,
                        name,
                        "ban-seize:coupons");
                coupons = forfeited;
            }
            Player online = Bukkit.getPlayer(ownerUuid);
            for (BondsStore.BondRow bond : store.listActiveForOwner(ownerUuid)) {
                store.markRedeemed(bond.id());
                notes++;
                principal += bond.principal();
                if (online != null && online.isOnline()) {
                    UUID bondId = bond.id();
                    Bukkit.getScheduler().runTask(plugin.host(), () -> removeCertificateFromPlayer(online, bondId));
                }
            }
            if (notes > 0 || coupons > 0) {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin.host(), () -> plugin.cloudSync().syncSnapshot(false));
            }
            return new SeizeResult(notes, GoldMoney.round(principal), GoldMoney.round(coupons));
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Bond ban-seize failed: " + ex.getMessage(), ex);
            return SeizeResult.EMPTY;
        }
    }

    public record SeizeResult(int notesVoided, double principalG, double couponsForfeitedG) {
        static final SeizeResult EMPTY = new SeizeResult(0, 0, 0);

        public double totalToReserveG() {
            return GoldMoney.round(couponsForfeitedG);
        }
    }

    private RootMcTreasuryService resolveTreasury() {
        return RootMcTreasuryResolver.resolve(plugin.host());
    }
}
