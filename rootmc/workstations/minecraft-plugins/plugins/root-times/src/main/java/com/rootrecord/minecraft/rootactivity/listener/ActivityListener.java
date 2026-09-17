package com.rootrecord.minecraft.rootactivity.listener;

import com.rootrecord.minecraft.rootactivity.RootActivityPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ActivityListener implements Listener {

    private final RootActivityPlugin plugin;

    public ActivityListener(RootActivityPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        plugin.service().onPlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.service().onPlayerQuit(event.getPlayer());
    }
}
