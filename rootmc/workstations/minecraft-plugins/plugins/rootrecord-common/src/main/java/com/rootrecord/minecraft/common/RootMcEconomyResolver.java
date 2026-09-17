package com.rootrecord.minecraft.common;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Resolves RootMC economy across plugin jars.
 *
 * <p>Each suite jar embeds {@code rootrecord-common}, so {@code ServicesManager} lookups by
 * {@link RootMcEconomyService}{@code Class} fail across plugins (different Class objects). Prefer
 * Vault when present; otherwise bridge to the live {@code Root-Economy} plugin (Essentials fallback).
 */
public final class RootMcEconomyResolver {

    private RootMcEconomyResolver() {}

    public static RootMcEconomyService resolve(JavaPlugin plugin) {
        Economy vault = vaultEconomy();
        if (vault != null) {
            return new VaultAdapter(vault);
        }

        RegisteredServiceProvider<RootMcEconomyService> rootRsp =
                Bukkit.getServicesManager().getRegistration(RootMcEconomyService.class);
        if (rootRsp != null && rootRsp.getProvider() != null) {
            return rootRsp.getProvider();
        }

        // Prefer Root-Economy (UUID SPI). Do not wrap Root-Essentials — its balance/withdraw
        // helpers call back into this resolver and would recurse after the economy split.
        Plugin host = Bukkit.getPluginManager().getPlugin("Root-Economy");
        if (host != null && host.isEnabled()) {
            RootMcEconomyService bridged = ReflectiveEssentialsEconomy.tryWrap(host);
            if (bridged != null) {
                return bridged;
            }
            if (plugin != null) {
                plugin.getLogger().log(
                        Level.WARNING,
                        "Root-Economy is enabled but economy methods were not reachable.");
            }
        } else if (plugin != null) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Economy unavailable: Vault has no provider and Root-Economy is not enabled.");
        }
        return null;
    }

    private static Economy vaultEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        return rsp != null ? rsp.getProvider() : null;
    }

    private static final class VaultAdapter implements RootMcEconomyService {

        private final Economy vault;

        private VaultAdapter(Economy vault) {
            this.vault = vault;
        }

        @Override
        public double balance(UUID playerId) {
            return vault.getBalance(offline(playerId));
        }

        @Override
        public boolean has(UUID playerId, double amount) {
            return vault.has(offline(playerId), amount);
        }

        @Override
        public boolean withdraw(UUID playerId, double amount) {
            return vault.withdrawPlayer(offline(playerId), amount).transactionSuccess();
        }

        @Override
        public void deposit(UUID playerId, double amount) {
            if (amount > 0) {
                vault.depositPlayer(offline(playerId), amount);
            }
        }

        @Override
        public void depositIncome(UUID playerId, double amount) {
            deposit(playerId, amount);
        }

        @Override
        public void sinkServiceFee(UUID payerId, String payerName, double amount, String channel) {
            Plugin host = Bukkit.getPluginManager().getPlugin("Root-Economy");
            if (host == null || amount <= 0) {
                return;
            }
            try {
                host.getClass()
                        .getMethod("sinkServiceFee", UUID.class, String.class, double.class, String.class)
                        .invoke(host, payerId, payerName == null ? "player" : payerName, amount, channel);
            } catch (ReflectiveOperationException ignored) {
                /* Root-Economy sinkServiceFee not reachable */
            }
        }

        private static OfflinePlayer offline(UUID playerId) {
            return Bukkit.getOfflinePlayer(playerId);
        }
    }

    /** Cross-classloader bridge: call Root-Essentials economy methods by name. */
    private static final class ReflectiveEssentialsEconomy implements RootMcEconomyService {

        private final Object target;
        private final Method balance;
        private final Method has;
        private final Method withdraw;
        private final Method deposit;
        private final Method depositIncome;

        private ReflectiveEssentialsEconomy(
                Object target,
                Method balance,
                Method has,
                Method withdraw,
                Method deposit,
                Method depositIncome) {
            this.target = target;
            this.balance = balance;
            this.has = has;
            this.withdraw = withdraw;
            this.deposit = deposit;
            this.depositIncome = depositIncome;
        }

        static RootMcEconomyService tryWrap(Plugin essentials) {
            try {
                Class<?> type = essentials.getClass();
                Method balance = type.getMethod("balance", UUID.class);
                Method has = type.getMethod("has", UUID.class, double.class);
                Method withdraw = type.getMethod("withdraw", UUID.class, double.class);
                Method deposit = type.getMethod("deposit", UUID.class, double.class);
                Method depositIncome;
                try {
                    depositIncome = type.getMethod("depositIncome", UUID.class, double.class);
                } catch (NoSuchMethodException missing) {
                    depositIncome = null;
                }
                return new ReflectiveEssentialsEconomy(
                        essentials, balance, has, withdraw, deposit, depositIncome);
            } catch (ReflectiveOperationException ex) {
                return null;
            }
        }

        @Override
        public double balance(UUID playerId) {
            try {
                Object v = balance.invoke(target, playerId);
                return v instanceof Number n ? n.doubleValue() : 0.0;
            } catch (ReflectiveOperationException ex) {
                return 0.0;
            }
        }

        @Override
        public boolean has(UUID playerId, double amount) {
            try {
                Object v = has.invoke(target, playerId, amount);
                return Boolean.TRUE.equals(v);
            } catch (ReflectiveOperationException ex) {
                return false;
            }
        }

        @Override
        public boolean withdraw(UUID playerId, double amount) {
            try {
                Object v = withdraw.invoke(target, playerId, amount);
                return Boolean.TRUE.equals(v);
            } catch (ReflectiveOperationException ex) {
                return false;
            }
        }

        @Override
        public void deposit(UUID playerId, double amount) {
            try {
                deposit.invoke(target, playerId, amount);
            } catch (ReflectiveOperationException ignored) {
                // ignore
            }
        }

        @Override
        public void depositIncome(UUID playerId, double amount) {
            if (depositIncome == null) {
                deposit(playerId, amount);
                return;
            }
            try {
                depositIncome.invoke(target, playerId, amount);
            } catch (ReflectiveOperationException ex) {
                deposit(playerId, amount);
            }
        }
    }
}
