package com.rootrecord.minecraft.rootessentials.listener;

import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Attribute water/lava bucket empties to a player, then charge wilderness/territory
 * fees (into debt) for each block the fluid later occupies.
 */
public final class FluidSpreadFeeListener implements Listener {

    private static final long TRACK_TTL_MS = 10L * 60_000L;

    private final RootEssentialsPlugin plugin;
    private final Map<String, Tracked> sources = new ConcurrentHashMap<>();

    public FluidSpreadFeeListener(RootEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (player == null
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Material bucket = event.getBucket();
        if (bucket != Material.WATER_BUCKET && bucket != Material.LAVA_BUCKET) {
            return;
        }
        Block dest = event.getBlockClicked().getRelative(event.getBlockFace());
        if (dest == null) {
            return;
        }
        remember(dest, player.getUniqueId());
        var grace = plugin.newPlayerGrace();
        if (grace != null) {
            Material fluid = bucket == Material.LAVA_BUCKET ? Material.LAVA : Material.WATER;
            grace.tryChargeWildernessPlace(player, fluid, dest.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent event) {
        Block from = event.getBlock();
        Block to = event.getToBlock();
        if (from == null || to == null || !isFluid(from.getType())) {
            return;
        }
        if (isFluid(to.getType())) {
            return;
        }
        Tracked tracked = sources.get(key(from));
        if (tracked == null || System.currentTimeMillis() - tracked.atMs > TRACK_TTL_MS) {
            if (tracked != null) {
                sources.remove(key(from));
            }
            return;
        }
        remember(to, tracked.playerId);
        Player player = plugin.getServer().getPlayer(tracked.playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        var grace = plugin.newPlayerGrace();
        if (grace != null) {
            grace.tryChargeWildernessPlace(player, from.getType(), to.getLocation());
        }
    }

    private void remember(Block block, UUID playerId) {
        if (block == null || playerId == null || block.getWorld() == null) {
            return;
        }
        sources.put(key(block), new Tracked(playerId, System.currentTimeMillis()));
        if (sources.size() > 8_000) {
            long now = System.currentTimeMillis();
            sources.entrySet().removeIf(e -> now - e.getValue().atMs > TRACK_TTL_MS);
        }
    }

    private static String key(Block block) {
        return block.getWorld().getName()
                + ":" + block.getX()
                + ":" + block.getY()
                + ":" + block.getZ();
    }

    private static boolean isFluid(Material type) {
        return type == Material.WATER
                || type == Material.LAVA
                || type == Material.BUBBLE_COLUMN;
    }

    private record Tracked(UUID playerId, long atMs) {}
}
