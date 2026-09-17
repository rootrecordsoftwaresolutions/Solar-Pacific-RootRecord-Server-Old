package com.rootrecord.minecraft.roottry;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class TryCommandListener implements Listener {

    private final RootTryPlugin plugin;

    public TryCommandListener(RootTryPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        TryService service = plugin.service();
        if (service != null) {
            service.onCommand(event.getPlayer(), event.getMessage());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        TryService service = plugin.service();
        if (service != null) {
            service.completed(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        TryService service = plugin.service();
        if (service != null) {
            service.invalidate(event.getPlayer().getUniqueId());
        }
    }
}
