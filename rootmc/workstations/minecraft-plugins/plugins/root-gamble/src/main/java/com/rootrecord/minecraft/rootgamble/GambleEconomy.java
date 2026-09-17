package com.rootrecord.minecraft.rootgamble;

import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.common.RootMcTreasuryResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.common.TreasuryLedgerType;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

/** Withdraw / credit Reserve / grant wins. */
public final class GambleEconomy {

    private final RootGamblePlugin plugin;

    public GambleEconomy(RootGamblePlugin plugin) {
        this.plugin = plugin;
    }

    public RootMcEconomyService economy() {
        return RootMcEconomyResolver.resolve(plugin);
    }

    public RootMcTreasuryService treasury() {
        return RootMcTreasuryResolver.resolve(plugin);
    }

    public boolean available() {
        return economy() != null && treasury() != null;
    }

    public double balance(Player player) {
        RootMcEconomyService eco = economy();
        return eco == null ? 0 : eco.balance(player.getUniqueId());
    }

    /** /reserve Reserve line (mint backing − notes). Can be negative. */
    public double displayedReserve() {
        RootMcTreasuryService t = treasury();
        if (t == null) {
            return 0;
        }
        try {
            return t.displayedReserveBalance();
        } catch (Throwable ignored) {
            return t.headlineReserveBalance();
        }
    }

    /** Debit player and credit Reserve (house take). */
    public boolean takeWager(Player player, double amount, String channel) {
        if (amount <= 0) {
            return true;
        }
        RootMcEconomyService eco = economy();
        RootMcTreasuryService treasury = treasury();
        if (eco == null || treasury == null) {
            return false;
        }
        if (!eco.withdraw(player.getUniqueId(), amount)) {
            return false;
        }
        treasury.creditTreasury(
                amount,
                TreasuryLedgerType.OTHER,
                player.getUniqueId(),
                player.getName(),
                "gamble:" + channel);
        return true;
    }

    /** Escrow only (no treasury) — P2P. */
    public boolean withdrawEscrow(UUID uuid, double amount) {
        if (amount <= 0) {
            return true;
        }
        RootMcEconomyService eco = economy();
        return eco != null && eco.withdraw(uuid, amount);
    }

    public void deposit(UUID uuid, double amount) {
        if (amount <= 0) {
            return;
        }
        RootMcEconomyService eco = economy();
        if (eco != null) {
            eco.deposit(uuid, amount);
        }
    }

    /** Reserve → player payout. */
    public boolean payWin(Player player, double amount, String reason) {
        if (amount <= 0) {
            return true;
        }
        RootMcTreasuryService treasury = treasury();
        if (treasury == null) {
            return false;
        }
        return treasury.grantToPlayer(
                player.getUniqueId(),
                player.getName(),
                amount,
                treasury.treasuryUuid(),
                treasury.treasuryUsername(),
                reason);
    }

    public boolean payWinOffline(UUID uuid, String name, double amount, String reason) {
        if (amount <= 0) {
            return true;
        }
        RootMcTreasuryService treasury = treasury();
        if (treasury == null) {
            return false;
        }
        String n = name == null || name.isBlank() ? "player" : name;
        return treasury.grantToPlayer(
                uuid, n, amount, treasury.treasuryUuid(), treasury.treasuryUsername(), reason);
    }

    public static String money(double amount) {
        if (Math.abs(amount - Math.rint(amount)) < 1e-9) {
            return String.valueOf((long) Math.rint(amount));
        }
        return String.format(Locale.US, "%.2f", amount);
    }
}
