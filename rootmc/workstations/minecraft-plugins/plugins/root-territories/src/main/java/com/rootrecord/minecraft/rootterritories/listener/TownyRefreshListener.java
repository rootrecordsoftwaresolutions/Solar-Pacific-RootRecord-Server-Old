package com.rootrecord.minecraft.rootterritories.listener;

import com.rootrecord.minecraft.rootterritories.RootTerritoriesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/** Debounced Towny claim/unclaim refresh. */
public final class TownyRefreshListener {

    private TownyRefreshListener() {}

    public static void register(RootTerritoriesPlugin plugin) {
        Plugin towny = Bukkit.getPluginManager().getPlugin("Towny");
        if (towny == null || !towny.isEnabled()) {
            return;
        }
        Listener listener = new Listener() {};
        ClassLoader loader = towny.getClass().getClassLoader();
        wire(plugin, listener, loader, "com.palmergames.bukkit.towny.event.NewTownEvent");
        wire(plugin, listener, loader, "com.palmergames.bukkit.towny.event.TownClaimEvent");
        wire(plugin, listener, loader, "com.palmergames.bukkit.towny.event.TownUnclaimEvent");
        wire(plugin, listener, loader, "com.palmergames.bukkit.towny.event.TownPreClaimEvent");
        wire(plugin, listener, loader, "com.palmergames.bukkit.towny.event.TownPreUnclaimCmdEvent");
        wire(plugin, listener, loader, "com.palmergames.bukkit.towny.event.nation.NewNationEvent");
        wire(plugin, listener, loader, "com.palmergames.bukkit.towny.event.nation.DeleteNationEvent");
        wire(plugin, listener, loader, "com.palmergames.bukkit.towny.event.DeleteTownEvent");
        wire(plugin, listener, loader, "com.palmergames.bukkit.towny.event.nation.NationTownAddEvent");
        wire(plugin, listener, loader, "com.palmergames.bukkit.towny.event.nation.NationTownRemoveEvent");
    }

    private static void wire(
            RootTerritoriesPlugin plugin,
            Listener listener,
            ClassLoader loader,
            String eventClassName) {
        try {
            Class<?> raw = Class.forName(eventClassName, true, loader);
            if (!Event.class.isAssignableFrom(raw)) {
                return;
            }
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) raw;
            PluginManager pm = plugin.getServer().getPluginManager();
            pm.registerEvent(
                    eventClass,
                    listener,
                    EventPriority.MONITOR,
                    (l, event) -> plugin.scheduleRebuild(false),
                    plugin);
        } catch (Throwable ex) {
            plugin.getLogger().fine("Towny refresh hook skipped for " + eventClassName + ": " + ex.getMessage());
        }
    }
}
