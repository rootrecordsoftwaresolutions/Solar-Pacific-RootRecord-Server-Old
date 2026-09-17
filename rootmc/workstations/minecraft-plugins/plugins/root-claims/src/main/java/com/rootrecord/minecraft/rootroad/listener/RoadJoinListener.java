package com.rootrecord.minecraft.rootroad.listener;

import com.rootrecord.minecraft.rootroad.RoadGuideService;
import com.rootrecord.minecraft.rootroad.RootRoadPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class RoadJoinListener implements Listener {

    private final RootRoadPlugin plugin;
    private final RoadGuideService guide;

    public RoadJoinListener(RootRoadPlugin plugin, RoadGuideService guide) {
        this.plugin = plugin;
        this.guide = guide;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.enabledFlag()) {
            return;
        }
        guide.onJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!plugin.enabledFlag()) {
            return;
        }
        guide.onQuit(event.getPlayer());
    }
}
