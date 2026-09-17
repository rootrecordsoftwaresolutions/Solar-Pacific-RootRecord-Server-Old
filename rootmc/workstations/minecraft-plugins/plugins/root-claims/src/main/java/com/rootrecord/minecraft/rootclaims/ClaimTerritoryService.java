package com.rootrecord.minecraft.rootclaims;

import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.common.RootMcClaimTerritoryService;
import com.rootrecord.minecraft.common.RootMcTreasuryResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.common.TreasuryLedgerType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Claim land + outward territory band lookups for wilderness fee exemptions and credits. */
public final class ClaimTerritoryService implements RootMcClaimTerritoryService {

    /** Outsider territory destroy: this share goes to the area-root claim bank. Rest → Server Reserve. */
    public static final double TERRITORY_CLAIM_SHARE = 0.75;

    private final RootClaimsPlugin plugin;
    private final ClaimStore store;

    public ClaimTerritoryService(RootClaimsPlugin plugin, ClaimStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @Override
    public boolean isClaimed(String worldName, int blockX, int blockZ) {
        if (!plugin.enabledFlag() || worldName == null || worldName.isBlank()) {
            return false;
        }
        for (ClaimRecord claim : store.all()) {
            if (!claim.key().world().equals(worldName)) {
                continue;
            }
            double distance = claim.horizontalDistance(blockX, blockZ);
            if (distance <= claim.radiusBlocks()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isWildernessFeeExempt(UUID playerId, String worldName, int blockX, int blockZ) {
        if (!plugin.enabledFlag() || playerId == null || worldName == null || worldName.isBlank()) {
            return false;
        }
        int buffer = plugin.territoryBufferBlocks();
        if (buffer <= 0) {
            return false;
        }
        for (ClaimRecord claim : store.all()) {
            if (!claim.containsTerritory(worldName, blockX, blockZ, buffer)) {
                continue;
            }
            if (store.areaRoot(claim).canManage(playerId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String creditWildernessDestroyFee(
            String worldName, int blockX, int blockZ, double amountG, String payerName) {
        if (!plugin.enabledFlag() || worldName == null || worldName.isBlank()) {
            return null;
        }
        double amount = GoldMoney.round(amountG);
        if (amount < GoldMoney.MIN_AMOUNT) {
            return null;
        }
        ClaimRecord claim = findTerritoryClaim(worldName, blockX, blockZ);
        if (claim == null) {
            return null;
        }
        ClaimRecord root = store.areaRoot(claim);
        if (root == null) {
            root = claim;
        }
        ClaimBankService banks = plugin.claimBanks();
        if (banks == null) {
            return null;
        }
        RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(plugin);
        double toBank = treasury == null ? amount : GoldMoney.round(amount * TERRITORY_CLAIM_SHARE);
        double toReserve = GoldMoney.round(amount - toBank);
        if (toBank < GoldMoney.MIN_AMOUNT) {
            toBank = 0;
        }
        if (toReserve < GoldMoney.MIN_AMOUNT) {
            toReserve = 0;
        }
        if (toBank > 0 && !banks.depositToClaimBank(root, toBank)) {
            return null;
        }
        if (toReserve > 0 && treasury != null) {
            Player payer = payerName == null || payerName.isBlank() ? null : Bukkit.getPlayerExact(payerName);
            UUID source = payer != null ? payer.getUniqueId() : root.ownerId();
            try {
                treasury.creditTreasury(
                        toReserve,
                        TreasuryLedgerType.TAX,
                        source,
                        payerName == null || payerName.isBlank() ? "wilderness" : payerName,
                        "territory-destroy:income");
            } catch (RuntimeException ex) {
                banks.depositToClaimBank(root, toReserve);
                toBank = GoldMoney.round(toBank + toReserve);
                plugin.getLogger().warning("Territory destroy reserve credit failed: " + ex.getMessage());
            }
        }
        double balance = banks.balance(root);
        notifyOwnerFee(root, payerName, toBank > 0 ? toBank : amount, balance);
        return root.ownerName();
    }

    @Override
    public int territoryBufferBlocks() {
        return plugin.territoryBufferBlocks();
    }

    private ClaimRecord findTerritoryClaim(String worldName, int blockX, int blockZ) {
        int buffer = plugin.territoryBufferBlocks();
        if (buffer <= 0) {
            return null;
        }
        ClaimRecord best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ClaimRecord claim : store.all()) {
            if (!claim.containsTerritory(worldName, blockX, blockZ, buffer)) {
                continue;
            }
            double distance = claim.horizontalDistance(blockX, blockZ);
            if (distance < bestDistance) {
                best = claim;
                bestDistance = distance;
            }
        }
        return best;
    }

    private void notifyOwnerFee(ClaimRecord claim, String payerName, double amount, double balance) {
        Player owner = Bukkit.getPlayer(claim.ownerId());
        if (owner == null || !owner.isOnline()) {
            return;
        }
        owner.sendMessage(plugin.msg("territory-fee-received")
                .replace("{player}", payerName == null || payerName.isBlank() ? "Someone" : payerName)
                .replace("{amount}", GoldMoney.format(amount))
                .replace("{balance}", GoldMoney.format(balance))
                .replace("{owner}", claim.ownerName()));
    }
}
