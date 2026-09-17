package com.rootrecord.minecraft.rootessentials.listener;

import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;

/** Registers the rootessentials PAPI expansion when PlaceholderAPI enables after us. */
public final class PlaceholderApiHookListener implements Listener {

    private final RootEssentialsPlugin plugin;

    public PlaceholderApiHookListener(RootEssentialsPlugin plugin) {
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
