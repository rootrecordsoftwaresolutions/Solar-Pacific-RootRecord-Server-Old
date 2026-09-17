package com.rootrecord.minecraft.rootbonds.gui;

import com.rootrecord.minecraft.rootbonds.RootBondsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** Right-click a bonded note in hand to open its detail menu. */
public final class BondNoteInteractListener implements Listener {

    private final RootBondsPlugin plugin;

    public BondNoteInteractListener(RootBondsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("rootbonds.use")) {
            return;
        }
        ItemStack item = event.getItem();
        UUID bondId = plugin.bonds().certificates().readBondId(item);
        if (bondId == null) {
            return;
        }
        event.setCancelled(true);
        if (plugin.bonds().certificates().isBondedRoot(item)) {
            plugin.bonds().ensureBondedRootRegistered(player, item);
        }
        var transfer = plugin.bondTransfer();
        if (transfer != null) {
            transfer.transferCertificates(player, item);
        }
        plugin.menuRegistry().openNoteDetail(player, bondId, item);
    }
}
