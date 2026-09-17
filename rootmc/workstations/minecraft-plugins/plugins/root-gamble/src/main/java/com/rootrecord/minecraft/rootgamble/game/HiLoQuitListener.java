package com.rootrecord.minecraft.rootgamble.game;

import com.rootrecord.minecraft.rootgamble.RootGamblePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class HiLoQuitListener implements Listener {

    private final RootGamblePlugin plugin;

    public HiLoQuitListener(RootGamblePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.hilo().clearSession(event.getPlayer().getUniqueId());
    }
}
