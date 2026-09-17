package com.rootrecord.minecraft.rootessentials.listener;

import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Sends live gold / CPU / solar XP status on join. */
public final class SolarHostStatusListener implements Listener {

    private final RootEconomyPlugin plugin;

    public SolarHostStatusListener(RootEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var solar = plugin.solarMining();
        if (solar == null) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                solar.sendStatus(event.getPlayer());
            }
        }, 40L);
    }
}
