package com.rootrecord.minecraft.rootrewards.economy;

import com.rootrecord.minecraft.common.RootMcEconomyService;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class RewardsEconomy {

    private final RootMcEconomyService root;
    private final Economy vault;

    public RewardsEconomy(RootMcEconomyService root, Economy vault) {
        this.root = root;
        this.vault = vault;
    }

    public boolean available() {
        return root != null || vault != null;
    }

    public void deposit(UUID uuid, double amount) {
        if (amount <= 0) {
            return;
        }
        if (root != null) {
            root.depositIncome(uuid, amount);
            return;
        }
        if (vault != null) {
            vault.depositPlayer(Bukkit.getOfflinePlayer(uuid), amount);
        }
    }

    public void deposit(Player player, double amount) {
        deposit(player.getUniqueId(), amount);
    }
}
