package com.rootrecord.minecraft.rootranks.economy;

import com.rootrecord.minecraft.common.RootMcEconomyService;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;

import java.util.UUID;

public final class RanksEconomy {

    private final RootMcEconomyService root;
    private final Economy vault;

    public RanksEconomy(RootMcEconomyService root, Economy vault) {
        this.root = root;
        this.vault = vault;
    }

    public boolean available() {
        return root != null || vault != null;
    }

    public double balance(UUID uuid) {
        if (root != null) {
            return root.balance(uuid);
        }
        if (vault != null) {
            return vault.getBalance(Bukkit.getOfflinePlayer(uuid));
        }
        return 0.0;
    }

    public boolean has(UUID uuid, double amount) {
        if (amount <= 0) {
            return true;
        }
        if (root != null) {
            return root.has(uuid, amount);
        }
        if (vault != null) {
            return vault.has(Bukkit.getOfflinePlayer(uuid), amount);
        }
        return false;
    }

    public boolean withdraw(UUID uuid, double amount) {
        if (amount <= 0) {
            return true;
        }
        if (root != null) {
            return root.withdraw(uuid, amount);
        }
        if (vault != null) {
            return vault.withdrawPlayer(Bukkit.getOfflinePlayer(uuid), amount).transactionSuccess();
        }
        return false;
    }
}
