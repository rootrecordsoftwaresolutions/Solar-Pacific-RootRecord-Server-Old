package com.rootrecord.minecraft.rootessentials.listener;

import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import com.rootrecord.minecraft.rootessentials.towny.WildernessAccess;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class NewPlayerGraceListener implements Listener {

    private final RootEssentialsPlugin plugin;

    public NewPlayerGraceListener(RootEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var grace = plugin.newPlayerGrace();
        if (grace != null && event.getPlayer() != null) {
            grace.clearWatch(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        var grace = plugin.newPlayerGrace();
        if (grace != null && grace.keepInventoryEnabled() && grace.inGracePeriod(player.getUniqueId())) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced() != null ? event.getBlockPlaced() : event.getBlock();
        if (player != null
                && block != null
                && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR
                && WildernessAccess.isWilderness(plugin, block.getLocation())) {
            var grace = plugin.newPlayerGrace();
            if (grace != null) {
                grace.tryChargeWildernessPlace(player, block.getType(), block.getLocation());
            }
        }
        notifyWilderness(player, block, true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (player == null || block == null) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (!WildernessAccess.isWilderness(plugin, block.getLocation())) {
            return;
        }
        var grace = plugin.newPlayerGrace();
        if (grace != null) {
            grace.tryChargeWildernessDestroy(player, block.getType(), block.getLocation());
        }
    }

    private void notifyWilderness(Player player, Block block, boolean placing) {
        if (player == null || block == null) {
            return;
        }
        Material material = block.getType();
        if (material == null || material.isAir()) {
            return;
        }
        if (!WildernessAccess.isWilderness(plugin, block.getLocation())) {
            return;
        }
        var grace = plugin.newPlayerGrace();
        if (grace == null) {
            return;
        }
        var loc = block.getLocation();
        grace.onWildernessBlockChange(
                player.getUniqueId(),
                player.getName(),
                material,
                loc.getWorld().getName(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ(),
                placing);
    }
}
