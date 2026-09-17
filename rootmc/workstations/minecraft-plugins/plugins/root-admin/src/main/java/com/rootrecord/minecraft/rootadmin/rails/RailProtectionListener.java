package com.rootrecord.minecraft.rootadmin.rails;

import com.rootrecord.minecraft.rootadmin.RootAdminPlugin;
import com.rootrecord.minecraft.rootadmin.util.AdminPermissions;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public final class RailProtectionListener implements Listener {

    private final RootAdminPlugin plugin;
    private final RailOwnershipStore store;

    public RailProtectionListener(RootAdminPlugin plugin, RailOwnershipStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRailPlace(BlockPlaceEvent event) {
        if (!plugin.railsEnabled()) {
            return;
        }
        Block placed = event.getBlockPlaced();
        if (!RailMaterials.isRail(placed.getType())) {
            return;
        }
        Player player = event.getPlayer();
        RailColumn column = RailColumn.fromRail(placed);
        if (denyIfForeignColumn(player, column, event)) {
            return;
        }
        store.trackColumn(column, player.getUniqueId(), player.getName());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlaceInClearance(BlockPlaceEvent event) {
        if (!plugin.railsEnabled()) {
            return;
        }
        if (RailMaterials.isRail(event.getBlockPlaced().getType())) {
            return;
        }
        Player player = event.getPlayer();
        if (canBypass(player)) {
            return;
        }
        denyIfForeignBlock(player, event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.railsEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        Block block = event.getBlock();
        Block railAnchor = RailColumn.findRailAnchor(block);
        RailColumn column = railAnchor != null ? RailColumn.fromRail(railAnchor) : null;

        if (canBypass(player)) {
            if (column != null) {
                store.forgetColumn(column);
            } else if (RailMaterials.isRail(block.getType())) {
                store.forget(block);
            }
            return;
        }

        if (column != null) {
            if (denyIfForeignColumn(player, column, event)) {
                return;
            }
            store.forgetColumn(column);
            return;
        }

        if (denyIfForeignBlock(player, block, event)) {
            return;
        }

        if (RailMaterials.isRail(block.getType())) {
            store.forget(block);
        }
    }

    private boolean denyIfForeignColumn(Player actor, RailColumn column, BlockBreakEvent event) {
        for (Block slot : column.allSlots()) {
            if (denyIfForeignBlock(actor, slot, event)) {
                return true;
            }
        }
        return false;
    }

    private boolean denyIfForeignColumn(Player actor, RailColumn column, BlockPlaceEvent event) {
        for (Block slot : column.allSlots()) {
            RailOwnershipStore.Owner owner = store.ownerOf(slot);
            if (owner == null) {
                continue;
            }
            if (owner.uuid().equals(actor.getUniqueId())) {
                continue;
            }
            event.setCancelled(true);
            plugin.sendRailDenyMessage(actor, owner.name());
            return true;
        }
        return false;
    }

    private boolean denyIfForeignBlock(Player breaker, Block block, BlockBreakEvent event) {
        RailOwnershipStore.Owner owner = store.ownerOf(block);
        if (owner == null) {
            Block railAnchor = RailColumn.findRailAnchor(block);
            if (railAnchor != null) {
                owner = store.ownerOf(railAnchor);
            }
            if (owner == null && plugin.railsProtectUnknown() && railAnchor != null) {
                event.setCancelled(true);
                plugin.sendRailDenyMessage(breaker, "Unknown");
                return true;
            }
            if (owner == null) {
                return false;
            }
        }
        if (owner.uuid().equals(breaker.getUniqueId())) {
            return false;
        }
        event.setCancelled(true);
        plugin.sendRailDenyMessage(breaker, owner.name());
        return true;
    }

    private boolean denyIfForeignBlock(Player placer, Block block, BlockPlaceEvent event) {
        RailOwnershipStore.Owner owner = store.ownerOf(block);
        if (owner == null || owner.uuid().equals(placer.getUniqueId())) {
            return false;
        }
        event.setCancelled(true);
        plugin.sendRailDenyMessage(placer, owner.name());
        return true;
    }

    private boolean canBypass(Player player) {
        return AdminPermissions.has(player, "rails.bypass");
    }
}
