package com.rootrecord.minecraft.rootbonds.service;

import com.rootrecord.minecraft.common.RootMcTreasuryResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import org.bukkit.plugin.Plugin;

/** Bond coupon payouts pause while the post-reset reserve ledger is below zero. */
public final class BondReserveGate {

    private static final double PAUSE_BELOW_G = -0.01;

    private BondReserveGate() {}

    public static boolean payoutsPaused(Plugin plugin) {
        RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(plugin);
        if (treasury == null) {
            return false;
        }
        return treasury.reserveLedgerNet() < PAUSE_BELOW_G;
    }
}
