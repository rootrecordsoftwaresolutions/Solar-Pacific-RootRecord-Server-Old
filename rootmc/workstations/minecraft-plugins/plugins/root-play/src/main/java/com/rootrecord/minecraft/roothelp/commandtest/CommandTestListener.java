package com.rootrecord.minecraft.roothelp.commandtest;

import com.rootrecord.minecraft.roothelp.RootHelpPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public final class CommandTestListener implements Listener {

    private final RootHelpPlugin plugin;
    private final CommandTestService service;

    public CommandTestListener(RootHelpPlugin plugin, CommandTestService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        service.ensureEnrolledAsync(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!service.enabled()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin.host(), () ->
                service.tryCompleteCommand(event.getPlayer(), event.getMessage()));
    }
}
