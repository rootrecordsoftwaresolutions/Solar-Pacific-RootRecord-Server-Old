package com.rootrecord.minecraft.rootbonds.gui;

import com.rootrecord.minecraft.rootbonds.RootBondsPlugin;
import com.rootrecord.minecraft.rootbonds.service.GovernmentBondNotifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Delivers while-away government bond deposit summaries shortly after a leader logs in. */
public final class GovBondNotifyListener implements Listener {

    private static final long DELIVER_DELAY_TICKS = 60L;

    private final RootBondsPlugin plugin;

    public GovBondNotifyListener(RootBondsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> GovernmentBondNotifier.deliverPending(plugin, player),
                DELIVER_DELAY_TICKS);
    }
}
