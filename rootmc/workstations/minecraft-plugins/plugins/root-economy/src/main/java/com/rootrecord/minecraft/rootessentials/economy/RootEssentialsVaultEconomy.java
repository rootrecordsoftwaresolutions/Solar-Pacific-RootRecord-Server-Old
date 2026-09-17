package com.rootrecord.minecraft.rootessentials.economy;

import com.rootrecord.minecraft.common.RootMcClaimBankService;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import com.rootrecord.minecraft.rootessentials.data.EconomySystemAccounts;
import com.rootrecord.minecraft.rootessentials.towny.TownyEconomyAccounts;
import com.rootrecord.minecraft.rootessentials.towny.TownyEconomyAccounts.VaultAccount;
import com.rootrecord.minecraft.rootessentials.towny.TownyTreasuryChannels;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Bridges Root Essentials MySQL balances to Vault for Towny and other plugins. */
public final class RootEssentialsVaultEconomy implements Economy {

    private static final DecimalFormat FMT = new DecimalFormat("0.000");

    private final RootEconomyPlugin plugin;

    public RootEssentialsVaultEconomy(RootEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isEnabled() {
        return plugin.isEnabled();
    }

    @Override
    public String getName() {
        return "Root Essentials";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        return FMT.format(amount) + " " + currencyNamePlural();
    }

    @Override
    public String currencyNamePlural() {
        return plugin.currency();
    }

    @Override
    public String currencyNameSingular() {
        return plugin.currency();
    }

    @Override
    @Deprecated
    public boolean hasAccount(String playerName) {
        if (EconomySystemAccounts.isTownyServerAccount(null, playerName)
                || EconomySystemAccounts.isTownBankAccount(playerName)
                || EconomySystemAccounts.isNationBankAccount(playerName)
                || EconomySystemAccounts.isClaimBankAccount(playerName)) {
            return true;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
        return offline.getUniqueId() != null;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        if (player == null || player.getUniqueId() == null) {
            return false;
        }
        String name = player.getName();
        if (EconomySystemAccounts.isTownyServerAccount(player.getUniqueId(), name)
                || EconomySystemAccounts.isTownBankAccount(name)
                || EconomySystemAccounts.isNationBankAccount(name)
                || EconomySystemAccounts.isClaimBankAccount(name)) {
            return true;
        }
        return TownyEconomyAccounts.holderByUuid(player.getUniqueId()).isPresent()
                || player.getUniqueId() != null;
    }

    @Override
    @Deprecated
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    @Deprecated
    public double getBalance(String playerName) {
        return balance(TownyEconomyAccounts.resolve(playerName, null));
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return balance(resolve(player, null));
    }

    @Override
    @Deprecated
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    @Deprecated
    public boolean has(String playerName, double amount) {
        return has(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) + 0.0001d >= amount;
    }

    @Override
    @Deprecated
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    @Deprecated
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdrawResolved(TownyEconomyAccounts.resolve(playerName, null), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return withdrawResolved(resolve(player, null), amount);
    }

    @Override
    @Deprecated
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    @Deprecated
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return depositResolved(TownyEconomyAccounts.resolve(playerName, null), amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return depositResolved(resolve(player, null), amount);
    }

    @Override
    @Deprecated
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    @Deprecated
    public boolean createPlayerAccount(String playerName) {
        return createPlayerAccount(Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        if (!hasAccount(player)) {
            return false;
        }
        getBalance(player);
        return true;
    }

    @Override
    @Deprecated
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    @Override
    @Deprecated
    public EconomyResponse createBank(String name, String player) {
        return bankUnsupported();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return bankUnsupported();
    }

    @Override
    @Deprecated
    public EconomyResponse deleteBank(String name) {
        return bankUnsupported();
    }

    @Override
    @Deprecated
    public EconomyResponse bankBalance(String name) {
        return bankUnsupported();
    }

    @Override
    @Deprecated
    public EconomyResponse bankHas(String name, double amount) {
        return bankUnsupported();
    }

    @Override
    @Deprecated
    public EconomyResponse bankWithdraw(String name, double amount) {
        return bankUnsupported();
    }

    @Override
    @Deprecated
    public EconomyResponse bankDeposit(String name, double amount) {
        return bankUnsupported();
    }

    @Override
    @Deprecated
    public EconomyResponse isBankOwner(String name, String playerName) {
        return bankUnsupported();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return bankUnsupported();
    }

    @Override
    @Deprecated
    public EconomyResponse isBankMember(String name, String playerName) {
        return bankUnsupported();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return bankUnsupported();
    }

    @Override
    @Deprecated
    public List<String> getBanks() {
        return Collections.emptyList();
    }

    private double balance(VaultAccount account) {
        if (account.uuid() == null) {
            return 0;
        }
        try {
            return plugin.balance(account.uuid(), account.username());
        } catch (Exception ex) {
            plugin.getLogger().warning(
                    "Vault getBalance failed for " + account.username() + ": " + ex.getMessage());
            return 0;
        }
    }

    private EconomyResponse withdrawResolved(VaultAccount account, double amount) {
        if (account.uuid() == null) {
            return fail(amount, 0, "No account");
        }
        if (amount < 0) {
            return fail(amount, balance(account), "Negative amount");
        }
        UUID uuid = account.uuid();
        String name = account.username();
        if (!EconomySystemAccounts.isTownyServerAccount(uuid, name)
                && !EconomySystemAccounts.isTownBankAccount(name)
                && !EconomySystemAccounts.isNationBankAccount(name)
                && !EconomySystemAccounts.isClaimBankAccount(name)
                && TownyTreasuryChannels.isTownyEconomyCall()) {
            TownyTreasuryChannels.setPayer(uuid, name);
        }
        double before = balance(account);
        try {
            boolean ok = plugin.withdraw(uuid, name, amount);
            double after = balance(account);
            return ok
                    ? new EconomyResponse(amount, after, EconomyResponse.ResponseType.SUCCESS, "")
                    : new EconomyResponse(0, before, EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        } catch (Exception ex) {
            return new EconomyResponse(0, before, EconomyResponse.ResponseType.FAILURE, ex.getMessage());
        }
    }

    private EconomyResponse depositResolved(VaultAccount account, double amount) {
        if (account.uuid() == null) {
            return fail(amount, 0, "No account");
        }
        if (amount < 0) {
            return fail(amount, balance(account), "Negative amount");
        }
        UUID uuid = account.uuid();
        String name = account.username();
        double before = balance(account);
        try {
            if (EconomySystemAccounts.isTownyServerAccount(uuid, name)) {
                if (TownyTreasuryChannels.isClosedEconomyMirror()) {
                    TownyTreasuryChannels.clear();
                    plugin.deposit(uuid, name, amount);
                } else {
                    String details = TownyTreasuryChannels.consume();
                    if (details == null) {
                        details = TownyTreasuryChannels.inferFromStackTrace();
                    }
                    details = TownyTreasuryChannels.normalizeFoundingDetails(details, amount);
                    RootMcTreasuryService treasury = plugin.treasury();
                    if (treasury != null) {
                        UUID payerUuid = TownyTreasuryChannels.consumePayerUuid();
                        String payerName = TownyTreasuryChannels.consumePayerName();
                        treasury.settleClosedLoopPayment(
                                payerUuid,
                                payerName == null ? "player" : payerName,
                                amount,
                                details);
                    } else {
                        plugin.deposit(uuid, name, amount);
                    }
                }
            } else if (isClaimBankAccount(uuid)) {
                // Claim Camp banks hold loan Gold until withdrawn - never auto-sweep on deposit.
                plugin.deposit(uuid, name, amount);
            } else {
                plugin.depositIncome(uuid, name, amount);
            }
            double after = balance(account);
            return new EconomyResponse(amount, after, EconomyResponse.ResponseType.SUCCESS, "");
        } catch (Exception ex) {
            return new EconomyResponse(0, before, EconomyResponse.ResponseType.FAILURE, ex.getMessage());
        }
    }

    private static boolean isClaimBankAccount(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        var registration = Bukkit.getServicesManager().getRegistration(RootMcClaimBankService.class);
        if (registration == null || registration.getProvider() == null) {
            return false;
        }
        return registration.getProvider().findClaimBank(uuid) != null;
    }

    private static VaultAccount resolve(OfflinePlayer player, String explicitName) {
        return TownyEconomyAccounts.resolve(explicitName, player);
    }

    private static EconomyResponse bankUnsupported() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
    }

    private static EconomyResponse fail(double amount, double balance, String message) {
        return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.FAILURE, message);
    }
}
