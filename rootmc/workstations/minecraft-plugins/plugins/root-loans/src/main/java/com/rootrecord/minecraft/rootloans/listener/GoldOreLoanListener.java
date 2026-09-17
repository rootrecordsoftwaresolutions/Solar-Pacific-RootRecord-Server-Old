package com.rootrecord.minecraft.rootloans.listener;

import com.rootrecord.minecraft.rootloans.RootLoansPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Retired: gold ore now pays wallet Notes via DigitalGoldMineListener.
 * Loan repayment still happens through depositIncome sweep.
 */
public final class GoldOreLoanListener implements Listener {

    public GoldOreLoanListener(RootLoansPlugin plugin) {
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        // no-op — digital mine + income sweep replaced physical ore→loan.
    }
}
