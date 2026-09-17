package com.rootrecord.minecraft.rootperms.listener;

import com.rootrecord.minecraft.rootperms.service.PermsServiceImpl;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PermsSessionListener implements Listener {

    private final JavaPlugin plugin;
    private final PermsServiceImpl perms;

    public PermsSessionListener(JavaPlugin plugin, PermsServiceImpl perms) {
        this.plugin = plugin;
        this.perms = perms;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            perms.ensureUser(event.getPlayer().getUniqueId(), event.getPlayer().getName());
            plugin.getServer().getScheduler().runTask(plugin, () -> perms.refresh(event.getPlayer()));
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        perms.clearAttachment(event.getPlayer());
    }
}
