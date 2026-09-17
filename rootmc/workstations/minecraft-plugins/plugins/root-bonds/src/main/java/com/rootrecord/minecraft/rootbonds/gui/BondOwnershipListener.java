package com.rootrecord.minecraft.rootbonds.gui;

import com.rootrecord.minecraft.rootbonds.RootBondsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/** Keeps bond registration aligned with whoever holds the physical note. */
public final class BondOwnershipListener implements Listener {

    private final RootBondsPlugin plugin;

    public BondOwnershipListener(RootBondsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        registerHeldBonds(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> registerHeldBonds(player));
    }

    private void registerHeldBonds(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        var transfer = plugin.bondTransfer();
        if (transfer == null) {
            return;
        }
        ItemStack[] combined = new ItemStack[player.getInventory().getSize() + player.getEnderChest().getSize()];
        ItemStack[] inv = player.getInventory().getContents();
        ItemStack[] ender = player.getEnderChest().getContents();
        System.arraycopy(inv, 0, combined, 0, inv.length);
        System.arraycopy(ender, 0, combined, inv.length, ender.length);
        for (ItemStack stack : combined) {
            if (plugin.bonds().certificates().isBondedRoot(stack)) {
                plugin.bonds().ensureBondedRootRegistered(player, stack);
            }
        }
        transfer.registerCertificatesInInventory(player, combined);
    }
}
