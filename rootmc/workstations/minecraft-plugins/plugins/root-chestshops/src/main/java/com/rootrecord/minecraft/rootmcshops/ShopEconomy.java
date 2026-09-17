package com.rootrecord.minecraft.rootmcshops;

import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.common.RootMcTreasuryResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/** Economy adapter: prefer Root-Economy service, optional Vault fallback (reflection — Vault may be absent). */
public final class ShopEconomy {

    private final RootMcEconomyService root;
    private final Object vault;
    private final Method vaultHas;
    private final Method vaultBalance;
    private final Method vaultWithdraw;
    private final Method vaultDeposit;

    public ShopEconomy(RootMcEconomyService root, Object vault) {
        this.root = root;
        this.vault = vault;
        Method has = null;
        Method balance = null;
        Method withdraw = null;
        Method deposit = null;
        if (vault != null) {
            try {
                Class<?> eco = Class.forName("net.milkbowl.vault.economy.Economy");
                has = eco.getMethod("has", OfflinePlayer.class, double.class);
                balance = eco.getMethod("getBalance", OfflinePlayer.class);
                withdraw = eco.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
                deposit = eco.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            } catch (ReflectiveOperationException ex) {
                has = null;
                balance = null;
                withdraw = null;
                deposit = null;
            }
        }
        this.vaultHas = has;
        this.vaultBalance = balance;
        this.vaultWithdraw = withdraw;
        this.vaultDeposit = deposit;
    }

    public boolean available() {
        return root != null || (vault != null && vaultWithdraw != null);
    }

    public boolean has(Player player, double amount) {
        if (root != null) {
            return root.has(player.getUniqueId(), amount);
        }
        return vaultInvokeBoolean(vaultHas, player, amount);
    }

    public double balance(Player player) {
        if (root != null) {
            return root.balance(player.getUniqueId());
        }
        if (vaultBalance == null || vault == null) {
            return 0;
        }
        try {
            Object v = vaultBalance.invoke(vault, player);
            return v instanceof Number n ? n.doubleValue() : 0;
        } catch (ReflectiveOperationException ex) {
            return 0;
        }
    }

    public boolean withdraw(Player player, double amount) {
        if (root != null) {
            return root.withdraw(player.getUniqueId(), amount);
        }
        return vaultWithdraw(player, amount);
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
        return vaultWithdraw(Bukkit.getOfflinePlayer(payerUuid), gross) ? gross : -1;
    }

    public void depositToPlayer(UUID playerUuid, double amount) {
        if (root != null) {
            root.depositIncome(playerUuid, amount);
            return;
        }
        if (vaultDeposit == null || vault == null) {
            return;
        }
        try {
            vaultDeposit.invoke(vault, Bukkit.getOfflinePlayer(playerUuid), amount);
        } catch (ReflectiveOperationException ignored) {
            // no-op
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
            return vaultInvokeBoolean(vaultHas, Bukkit.getOfflinePlayer(id), amount);
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
            return vaultWithdraw(Bukkit.getOfflinePlayer(id), amount);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean vaultWithdraw(OfflinePlayer player, double amount) {
        if (vaultWithdraw == null || vault == null) {
            return false;
        }
        try {
            Object resp = vaultWithdraw.invoke(vault, player, amount);
            if (resp == null) {
                return false;
            }
            Method success = resp.getClass().getMethod("transactionSuccess");
            return Boolean.TRUE.equals(success.invoke(resp));
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    private boolean vaultInvokeBoolean(Method method, OfflinePlayer player, double amount) {
        if (method == null || vault == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(method.invoke(vault, player, amount));
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }
}
