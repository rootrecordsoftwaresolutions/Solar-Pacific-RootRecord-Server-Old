package com.rootrecord.minecraft.rootping;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PingListener implements Listener {

    private final RootPingPlugin plugin;

    public PingListener(RootPingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getPlayer() == null) {
            return;
        }
        String host = event.getHostname();
        if (host != null && host.contains(":")) {
            host = host.substring(0, host.indexOf(':'));
        }
        plugin.service().rememberVirtualHost(event.getPlayer().getUniqueId(), host);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.config().enabled()) {
            return;
        }
        plugin.service().startInterval(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.service().forget(event.getPlayer().getUniqueId());
    }
}
