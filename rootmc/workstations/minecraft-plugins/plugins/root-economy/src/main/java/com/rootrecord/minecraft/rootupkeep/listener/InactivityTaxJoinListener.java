package com.rootrecord.minecraft.rootupkeep.listener;

import com.rootrecord.minecraft.rootupkeep.RootUpkeepPlugin;
import com.rootrecord.minecraft.rootupkeep.service.InactivityTaxAwayNotifier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Delivers accumulated inactivity-tax losses after a long absence. */
public final class InactivityTaxJoinListener implements Listener {

    private static final long DELIVER_DELAY_TICKS = 60L;

    private final RootUpkeepPlugin plugin;

    public InactivityTaxJoinListener(RootUpkeepPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        long previousLastPlayed = event.getPlayer().getLastPlayed();
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin.host(),
                () -> {
                    org.bukkit.entity.Player player = plugin.getServer().getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        InactivityTaxAwayNotifier.deliverIfAway(plugin, player, previousLastPlayed);
                    }
                },
                DELIVER_DELAY_TICKS);
    }
}
