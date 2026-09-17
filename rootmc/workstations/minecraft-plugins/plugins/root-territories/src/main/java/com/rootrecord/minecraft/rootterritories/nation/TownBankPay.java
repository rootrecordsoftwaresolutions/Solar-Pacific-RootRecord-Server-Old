package com.rootrecord.minecraft.rootterritories.nation;


import com.rootrecord.minecraft.common.GoldMoney;

import com.rootrecord.minecraft.common.RootMcTreasuryService;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TownBankPay {

    public record Share(String townName, double amountGold) {}

    public record PaymentResult(boolean success, String failedTown, double failedAmount) {
        static PaymentResult ok() {
            return new PaymentResult(true, null, 0);
        }

        static PaymentResult fail(String town, double amount) {
            return new PaymentResult(false, town, amount);
        }
    }

    private TownBankPay() {}

    public static Economy economyOrNull() {
        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    public static List<Share> splitCost(double totalGold, List<String> townNames) {
        if (townNames.isEmpty() || totalGold <= 0) {
            return List.of();
        }
        int n = townNames.size();
        double base = GoldMoney.round(Math.floor((totalGold / n) * GoldMoney.SCALE) / GoldMoney.SCALE);
        List<Share> shares = new ArrayList<>();
        double assigned = 0;
        for (int i = 0; i < n; i++) {
            double amount = i == n - 1 ? roundGold(totalGold - assigned) : base;
            assigned += amount;
            shares.add(new Share(townNames.get(i), amount));
        }
        return shares;
    }

    public static PaymentResult collectSplit(
            RootMcTreasuryService treasury,
            Economy economy,
            List<Share> shares,
            String proposalId) {
        if (treasury == null || economy == null) {
            return PaymentResult.fail("treasury", 0);
        }
        Map<String, Double> debited = new LinkedHashMap<>();
        for (Share share : shares) {
            if (share.amountGold() <= 0) {
                continue;
            }
            OfflinePlayer account = Bukkit.getOfflinePlayer(TownyTownAccess.townBankAccountName(share.townName()));
            if (!economy.has(account, share.amountGold())) {
                refundAll(economy, debited);
                return PaymentResult.fail(share.townName(), share.amountGold());
            }
            if (!economy.withdrawPlayer(account, share.amountGold()).transactionSuccess()) {
                refundAll(economy, debited);
                return PaymentResult.fail(share.townName(), share.amountGold());
            }
            debited.put(share.townName(), share.amountGold());
        }
        for (Map.Entry<String, Double> entry : debited.entrySet()) {
            treasury.depositClosedLoopVault(entry.getValue());
        }
        return PaymentResult.ok();
    }

    private static void refundAll(Economy economy, Map<String, Double> debited) {
        for (Map.Entry<String, Double> entry : debited.entrySet()) {
            OfflinePlayer account = Bukkit.getOfflinePlayer(TownyTownAccess.townBankAccountName(entry.getKey()));
            economy.depositPlayer(account, entry.getValue());
        }
    }

    private static double roundGold(double value) {
        return GoldMoney.round(value);
    }
}
