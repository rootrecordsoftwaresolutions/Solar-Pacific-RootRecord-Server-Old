package com.rootrecord.minecraft.rootranks.listener;

import com.rootrecord.minecraft.rootranks.RootRanksPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;

/** Registers the rootranks PAPI expansion when PlaceholderAPI enables after us. */
public final class PlaceholderApiHookListener implements Listener {

    private final RootRanksPlugin plugin;

    public PlaceholderApiHookListener(RootRanksPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (!"PlaceholderAPI".equals(event.getPlugin().getName())) {
            return;
        }
        plugin.registerPlaceholderExpansionIfPresent();
    }
}
