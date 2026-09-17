package com.rootrecord.minecraft.rootmcshops;

import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.common.RootMcTreasuryResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Economy adapter: prefer Root Essentials service, fallback to Vault. */
public final class ShopEconomy {

    private final RootMcEconomyService root;
    private final Economy vault;

    public ShopEconomy(RootMcEconomyService root, Economy vault) {
        this.root = root;
        this.vault = vault;
    }

    public boolean available() {
        return root != null || vault != null;
    }

    public boolean has(Player player, double amount) {
        if (root != null) {
            return root.has(player.getUniqueId(), amount);
        }
        return vault != null && vault.has(player, amount);
    }

    public double balance(Player player) {
        if (root != null) {
            return root.balance(player.getUniqueId());
        }
        return vault != null ? vault.getBalance(player) : 0;
    }

    public boolean withdraw(Player player, double amount) {
        if (root != null) {
            return root.withdraw(player.getUniqueId(), amount);
        }
        if (vault == null) return false;
        return vault.withdrawPlayer(player, amount).transactionSuccess();
    }

    /**
     * Withdraws gross from payer (with treasury tax if enabled). Caller credits recipient the return value.
     *
     * @return net for recipient, or {@code -1} on failure
     */
    public double withholdTaxedPayment(Player payer, double gross, String channel) {
        if (gross <= 0) {
            return -1;
        }
        RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(
                (org.bukkit.plugin.java.JavaPlugin) org.bukkit.Bukkit.getPluginManager().getPlugin("Root-Essentials"));
        if (treasury != null && treasury.transactionTaxEnabled()) {
            return treasury.withholdTransactionTax(payer.getUniqueId(), payer.getName(), gross, channel);
        }
        return withdraw(payer, gross) ? gross : -1;
    }

    public double withholdTaxedPayment(UUID payerUuid, String payerName, double gross, String channel) {
        if (gross <= 0 || payerUuid == null) {
            return -1;
        }
        RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(
                (org.bukkit.plugin.java.JavaPlugin) org.bukkit.Bukkit.getPluginManager().getPlugin("Root-Essentials"));
        if (treasury != null && treasury.transactionTaxEnabled()) {
            return treasury.withholdTransactionTax(payerUuid, payerName, gross, channel);
        }
        if (root != null) {
            return root.withdraw(payerUuid, gross) ? gross : -1;
        }
        if (vault == null) {
            return -1;
        }
        return vault.withdrawPlayer(Bukkit.getOfflinePlayer(payerUuid), gross).transactionSuccess() ? gross : -1;
    }

    public void depositToPlayer(UUID playerUuid, double amount) {
        if (root != null) {
            root.depositIncome(playerUuid, amount);
            return;
        }
        if (vault != null) {
            vault.depositPlayer(Bukkit.getOfflinePlayer(playerUuid), amount);
        }
    }

    public boolean hasOwner(String ownerUuid, double amount) {
        if (ownerUuid == null || ownerUuid.isBlank() || amount <= 0) {
            return false;
        }
        try {
            UUID id = UUID.fromString(ownerUuid);
            if (root != null) {
                return root.has(id, amount);
            }
            return vault != null && vault.has(Bukkit.getOfflinePlayer(id), amount);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean withdrawOwner(String ownerUuid, double amount) {
        if (ownerUuid == null || ownerUuid.isBlank() || amount <= 0) {
            return false;
        }
        try {
            UUID id = UUID.fromString(ownerUuid);
            if (root != null) {
                return root.withdraw(id, amount);
            }
            if (vault == null) {
                return false;
            }
            return vault.withdrawPlayer(Bukkit.getOfflinePlayer(id), amount).transactionSuccess();
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
