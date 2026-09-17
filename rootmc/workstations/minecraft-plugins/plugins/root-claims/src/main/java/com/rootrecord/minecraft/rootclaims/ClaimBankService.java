package com.rootrecord.minecraft.rootclaims;

import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.common.RootMcClaimBankService;
import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.common.RootMcTreasuryResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.common.TreasuryLedgerType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ClaimBankService implements RootMcClaimBankService {

    public enum Status {
        OK,
        NO_ECONOMY,
        CANNOT_AFFORD,
        FAILED
    }

    public record Result(Status status, double amountG, double balanceG) {}

    private final RootClaimsPlugin plugin;
    private final ClaimStore store;

    public ClaimBankService(RootClaimsPlugin plugin, ClaimStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @Override
    public List<ClaimBank> activeClaimBanks() {
        List<ClaimBank> out = new ArrayList<>();
        for (ClaimRecord claim : store.all()) {
            if (!claim.isAreaRoot()) {
                continue;
            }
            double balance = balance(claim);
            if (balance + 1e-9 < GoldMoney.MIN_AMOUNT) {
                continue;
            }
            out.add(toBank(claim, balance));
        }
        return out;
    }

    @Override
    public ClaimBank findClaimBank(UUID accountUuid) {
        if (accountUuid == null) {
            return null;
        }
        for (ClaimRecord claim : store.all()) {
            if (accountUuid.equals(claim.bankAccountUuid())) {
                return toBank(claim, balance(claim));
            }
        }
        return null;
    }

    public double balance(ClaimRecord claim) {
        RootMcEconomyService economy = RootMcEconomyResolver.resolve(plugin);
        if (economy == null || claim == null) {
            return 0;
        }
        return GoldMoney.round(economy.balance(claim.bankAccountUuid(), claim.bankAccountName()));
    }

    public Result depositFromPlayer(Player actor, ClaimRecord claim, double amountG) {
        double amount = GoldMoney.round(amountG);
        RootMcEconomyService economy = RootMcEconomyResolver.resolve(plugin);
        if (economy == null || actor == null || claim == null || amount < GoldMoney.MIN_AMOUNT) {
            return new Result(Status.NO_ECONOMY, amount, 0);
        }
        if (!economy.has(actor.getUniqueId(), amount)) {
            return new Result(Status.CANNOT_AFFORD, amount, balance(claim));
        }
        if (!economy.withdraw(actor.getUniqueId(), amount)) {
            return new Result(Status.FAILED, amount, balance(claim));
        }
        if (!depositToClaimBank(claim, amount)) {
            // Restore wallet without loan sweep - this is a failed transfer refund, not income.
            economy.deposit(actor.getUniqueId(), amount);
            return new Result(Status.FAILED, amount, balance(claim));
        }
        double bal = balance(claim);
        notifyOwnerDeposit(actor, claim, amount, bal);
        return new Result(Status.OK, amount, bal);
    }

    public Result withdrawToPlayer(Player actor, ClaimRecord claim, double amountG) {
        double amount = GoldMoney.round(amountG);
        RootMcEconomyService economy = RootMcEconomyResolver.resolve(plugin);
        if (economy == null || actor == null || claim == null || amount < GoldMoney.MIN_AMOUNT) {
            return new Result(Status.NO_ECONOMY, amount, 0);
        }
        double bankBal = balance(claim);
        if (bankBal + 1e-9 < amount) {
            return new Result(Status.CANNOT_AFFORD, amount, bankBal);
        }
        if (!withdrawFromClaimBank(claim, amount)) {
            return new Result(Status.FAILED, amount, bankBal);
        }
        try {
            economy.depositIncome(actor.getUniqueId(), amount);
        } catch (RuntimeException ex) {
            depositToClaimBank(claim, amount);
            plugin.getLogger().warning("Claim bank withdraw to player failed: " + ex.getMessage());
            return new Result(Status.FAILED, amount, balance(claim));
        }
        return new Result(Status.OK, amount, balance(claim));
    }

    public boolean withdrawFromClaimBank(ClaimRecord claim, double amountG) {
        double amount = GoldMoney.round(amountG);
        if (claim == null || amount < GoldMoney.MIN_AMOUNT) {
            return false;
        }
        RootMcEconomyService economy = RootMcEconomyResolver.resolve(plugin);
        if (economy == null || balance(claim) + 1e-9 < amount) {
            return false;
        }
        return economy.withdrawAccount(claim.bankAccountUuid(), claim.bankAccountName(), amount);
    }

    /** Credit claim bank without loan sweep (loan Gold stays in the claim until withdrawn). */
    public boolean depositToClaimBank(ClaimRecord claim, double amountG) {
        double amount = GoldMoney.round(amountG);
        if (claim == null || amount < GoldMoney.MIN_AMOUNT) {
            return false;
        }
        RootMcEconomyService economy = RootMcEconomyResolver.resolve(plugin);
        if (economy == null) {
            return false;
        }
        try {
            economy.depositAccount(claim.bankAccountUuid(), claim.bankAccountName(), amount);
            return true;
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Claim bank deposit failed: " + ex.getMessage());
            return false;
        }
    }

    /**
     * Ensure Camp/claim bank rows use {@code claim-*} usernames (Towny-style), not {@code player}.
     */
    public void repairBankAccountLabels() {
        RootMcEconomyService economy = RootMcEconomyResolver.resolve(plugin);
        if (economy == null) {
            return;
        }
        int fixed = 0;
        for (ClaimRecord claim : store.all()) {
            try {
                // ensureRow rewrites username "player" -> claim-* even at 0 amount.
                economy.depositAccount(claim.bankAccountUuid(), claim.bankAccountName(), 0);
                fixed++;
            } catch (Exception ex) {
                plugin.getLogger().warning(
                        "Claim bank label repair failed for " + claim.key().label() + ": " + ex.getMessage());
            }
        }
        if (fixed > 0) {
            plugin.getLogger().info("Claim bank account labels checked for " + fixed + " claim(s).");
        }
    }

    /**
     * Move claim-bank Gold to the owner wallet. Uses {@link RootMcEconomyService#depositIncome}
     * so active loans sweep on withdrawal from the claim.
     */
    public double drainClaimBankToOwner(ClaimRecord claim) {
        if (claim == null) {
            return 0;
        }
        RootMcEconomyService economy = RootMcEconomyResolver.resolve(plugin);
        if (economy == null) {
            return -1;
        }
        double amount = balance(claim);
        if (amount < GoldMoney.MIN_AMOUNT) {
            return 0;
        }
        if (!economy.withdrawAccount(claim.bankAccountUuid(), claim.bankAccountName(), amount)) {
            return -1;
        }
        try {
            economy.depositIncome(claim.ownerId(), amount);
            return amount;
        } catch (RuntimeException ex) {
            economy.depositAccount(claim.bankAccountUuid(), claim.bankAccountName(), amount);
            plugin.getLogger().warning("Claim bank drain to owner failed: " + ex.getMessage());
            return -1;
        }
    }

    /**
     * Ban confiscation: withdraw claim-bank Gold and credit Server Reserve (no owner payout).
     *
     * @return amount credited, or {@code -1} on failure
     */
    public double drainClaimBankToReserve(ClaimRecord claim, String details) {
        if (claim == null) {
            return 0;
        }
        RootMcEconomyService economy = RootMcEconomyResolver.resolve(plugin);
        RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(plugin);
        if (economy == null || treasury == null) {
            return -1;
        }
        double amount = balance(claim);
        if (amount < GoldMoney.MIN_AMOUNT) {
            return 0;
        }
        if (!economy.withdrawAccount(claim.bankAccountUuid(), claim.bankAccountName(), amount)) {
            return -1;
        }
        try {
            treasury.creditTreasury(
                    amount,
                    TreasuryLedgerType.OTHER,
                    claim.ownerId(),
                    claim.ownerName(),
                    details == null || details.isBlank() ? "ban-seize:claim-bank" : details);
            return amount;
        } catch (RuntimeException ex) {
            economy.depositAccount(claim.bankAccountUuid(), claim.bankAccountName(), amount);
            plugin.getLogger().warning("Claim bank drain to reserve failed: " + ex.getMessage());
            return -1;
        }
    }

    private ClaimBank toBank(ClaimRecord claim, double balance) {
        return new ClaimBank(
                claim.bankAccountUuid(),
                claim.bankAccountName(),
                displayName(claim),
                claim.ownerId(),
                claim.ownerName(),
                GoldMoney.round(balance));
    }

    private String displayName(ClaimRecord claim) {
        int ownedCount = store.countOwnedBy(claim.ownerId());
        String type = ownedCount <= 3 ? "Camp" : "Claim";
        return type + " of " + claim.ownerName() + " (" + claim.key().world() + " " + claim.key().x() + "," + claim.key().z() + ")";
    }

    private void notifyOwnerDeposit(Player actor, ClaimRecord claim, double amount, double balance) {
        Player owner = Bukkit.getPlayer(claim.ownerId());
        if (owner == null || !owner.isOnline()) {
            return;
        }
        owner.sendMessage(plugin.msg("bank-owner-deposit")
                .replace("{player}", actor.getName())
                .replace("{amount}", GoldMoney.format(amount))
                .replace("{balance}", GoldMoney.format(balance))
                .replace("{claim}", displayName(claim)));
    }
}
