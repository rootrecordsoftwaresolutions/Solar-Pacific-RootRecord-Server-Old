package com.rootrecord.minecraft.rootbonds.gui;

import com.rootrecord.minecraft.rootbonds.RootBondsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;

/** Transfer bond registration when a player picks up a bonded note. */
public final class BondTransferListener implements Listener {

    private final RootBondsPlugin plugin;

    public BondTransferListener(RootBondsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        var transfer = plugin.bondTransfer();
        if (transfer == null || !transfer.isBondCertificate(stack)) {
            return;
        }
        transfer.transferCertificates(player, stack);
    }
}
