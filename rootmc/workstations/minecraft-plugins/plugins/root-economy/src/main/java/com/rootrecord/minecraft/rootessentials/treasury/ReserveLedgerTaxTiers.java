package com.rootrecord.minecraft.rootessentials.treasury;

import com.rootrecord.minecraft.common.GoldMoney;

/** Transaction tax tiers keyed off mint backing − circulating Notes (displayed Server Reserve). */
public final class ReserveLedgerTaxTiers {

    private ReserveLedgerTaxTiers() {}

    public static double rateForLedgerNet(double ledgerNetG) {
        double balance = GoldMoney.round(ledgerNetG);
        if (balance >= 1000) {
            return 0;
        }
        if (balance >= 0) {
            return 0.01;
        }
        if (balance >= -1000) {
            return 0.02;
        }
        if (balance >= -2000) {
            return 0.03;
        }
        if (balance >= -4000) {
            return 0.04;
        }
        if (balance >= -10000) {
            return 0.05;
        }
        return 0.10;
    }

    public static double ratePctForLedgerNet(double ledgerNetG) {
        return GoldMoney.round(rateForLedgerNet(ledgerNetG) * 100.0);
    }

    public static String tierLabel(double ledgerNetG) {
        double balance = GoldMoney.round(ledgerNetG);
        if (balance >= 1000) {
            return "reserve \u2265 1,000 G";
        }
        if (balance >= 0) {
            return "reserve below 1,000 G";
        }
        if (balance >= -1000) {
            return "reserve below 0 G";
        }
        if (balance >= -2000) {
            return "reserve below \u22121,000 G";
        }
        if (balance >= -4000) {
            return "reserve below \u22122,000 G";
        }
        if (balance >= -10000) {
            return "reserve below \u22124,000 G";
        }
        return "reserve below \u221210,000 G";
    }
}
