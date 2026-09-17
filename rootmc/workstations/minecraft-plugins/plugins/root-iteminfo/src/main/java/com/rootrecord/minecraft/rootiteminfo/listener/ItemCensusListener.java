package com.rootrecord.minecraft.rootiteminfo.listener;

import com.rootrecord.minecraft.rootiteminfo.RootItemInfoPlugin;
import com.rootrecord.minecraft.rootiteminfo.census.ItemCensusScanner;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicLong;

/** Soft refresh hooks — full accuracy comes from the periodic scan. */
public final class ItemCensusListener implements Listener {

    private final RootItemInfoPlugin plugin;
    private final ItemCensusScanner scanner;
    private final AtomicLong lastCloseKickMs = new AtomicLong();
    private BukkitTask chunkDebounceTask;

    public ItemCensusListener(RootItemInfoPlugin plugin, ItemCensusScanner scanner) {
        this.plugin = plugin;
        this.scanner = scanner;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> scanner.requestFullScan("join"), 200L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> scanner.requestFullScan("quit"), 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        long now = System.currentTimeMillis();
        long last = lastCloseKickMs.get();
        if (now - last < 30_000L) {
            return;
        }
        if (lastCloseKickMs.compareAndSet(last, now)) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> scanner.requestFullScan("inventory-close"), 40L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (chunkDebounceTask != null) {
            chunkDebounceTask.cancel();
        }
        chunkDebounceTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            chunkDebounceTask = null;
            scanner.requestFullScan("chunk-load-settled");
        }, 600L);
    }
}
