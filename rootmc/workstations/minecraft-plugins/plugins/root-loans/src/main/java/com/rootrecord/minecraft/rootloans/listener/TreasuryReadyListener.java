package com.rootrecord.minecraft.rootloans.listener;

import com.rootrecord.minecraft.rootloans.RootLoansPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;

/** Finish loan startup when Root-Essentials treasury becomes available. */
public final class TreasuryReadyListener implements Listener {

    private final RootLoansPlugin plugin;

    public TreasuryReadyListener(RootLoansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (!"Root-Essentials".equals(event.getPlugin().getName())) {
            return;
        }
        plugin.retryFinishEnable();
    }
}
