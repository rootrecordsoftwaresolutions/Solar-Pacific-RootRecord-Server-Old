package com.rootrecord.minecraft.rootbonds.gui;

import com.rootrecord.minecraft.common.RootMcEnderChestResolver;
import com.rootrecord.minecraft.common.RootMcEnderChestService;
import com.rootrecord.minecraft.rootbonds.RootBondsPlugin;
import com.rootrecord.minecraft.rootbonds.data.BondsStore;
import com.rootrecord.minecraft.rootbonds.item.BondCertificate;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Register ownership from /ec + inventory/hand. Unregister only when the note leaves the player. */
public final class BondOwnershipListener implements Listener {

    private final RootBondsPlugin plugin;

    public BondOwnershipListener(RootBondsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin.host(), () -> registerHeldBonds(event.getPlayer()), 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin.host(), () -> registerHeldBonds(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin.host(), () -> registerHeldBonds(player));
    }

    private void registerHeldBonds(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        var transfer = plugin.bondTransfer();
        if (transfer == null || plugin.bonds() == null || plugin.bonds().store() == null) {
            return;
        }
        ItemStack[] ender = enderContents(player);
        ItemStack[] inv = player.getInventory().getContents();
        Set<UUID> held = new HashSet<>();
        BondCertificate certs = plugin.bonds().certificates();
        collectHeld(certs, held, ender);
        collectHeld(certs, held, inv);
        for (ItemStack stack : ender) {
            if (stack != null && certs.isBondedRoot(stack)) {
                plugin.bonds().ensureBondedRootRegistered(player, stack);
            }
        }
        for (ItemStack stack : inv) {
            if (stack != null && certs.isBondedRoot(stack)) {
                plugin.bonds().ensureBondedRootRegistered(player, stack);
            }
        }
        ItemStack[] all = new ItemStack[ender.length + inv.length];
        System.arraycopy(ender, 0, all, 0, ender.length);
        System.arraycopy(inv, 0, all, ender.length, inv.length);
        transfer.registerCertificatesInInventory(player, all);
        try {
            for (BondsStore.BondRow bond : plugin.bonds().store().listActiveForOwner(player.getUniqueId())) {
                if (!held.contains(bond.id())) {
                    plugin.bonds().store().clearOwner(bond.id());
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Bond possession deregister: " + ex.getMessage());
        }
    }

    private static void collectHeld(BondCertificate certs, Set<UUID> held, ItemStack[] stacks) {
        if (stacks == null) {
            return;
        }
        for (ItemStack stack : stacks) {
            UUID bondId = certs.readBondId(stack);
            if (bondId != null) {
                held.add(bondId);
            }
        }
    }

    ItemStack[] enderContents(Player player) {
        RootMcEnderChestService ec = RootMcEnderChestResolver.resolve(plugin.host());
        if (ec != null) {
            return ec.contents(player.getUniqueId());
        }
        return player.getEnderChest().getContents();
    }
}
