package com.rootrecord.minecraft.rootterritories.listener;

import com.rootrecord.minecraft.rootterritories.RootTerritoriesPlugin;
import com.rootrecord.minecraft.rootterritories.service.TerritoryService;
import com.rootrecord.minecraft.rootterritories.towny.TownyReflection;
import com.rootrecord.minecraft.rootterritories.towny.TownyTerritorySource;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;

/** Blocks town creation inside foreign influence and nation joins without touching rings. */
public final class TerritoryRulesListener {

    private TerritoryRulesListener() {}

    public static void register(RootTerritoriesPlugin plugin) {
        if (!TownyTerritorySource.isAvailable()) {
            return;
        }
        TerritoryService territories = plugin.territories();
        Listener listener = new Listener() {};
        wirePreNewTown(plugin, listener, territories);
        wireNationJoin(
                plugin,
                listener,
                territories,
                "com.palmergames.bukkit.towny.event.NationPreAddTownEvent",
                "com.palmergames.bukkit.towny.event.nation.NationPreAddTownEvent");
        wireNationJoin(
                plugin,
                listener,
                territories,
                "com.palmergames.bukkit.towny.event.NationPreInviteTownEvent",
                "com.palmergames.bukkit.towny.event.nation.NationPreInviteTownEvent");
        plugin.getLogger().info("Territory rules listener registered (town creation + nation join).");
    }

    private static void wirePreNewTown(
            RootTerritoriesPlugin plugin,
            Listener listener,
            TerritoryService territories) {
        wire(
                plugin,
                listener,
                EventPriority.HIGH,
                "com.palmergames.bukkit.towny.event.PreNewTownEvent",
                event -> {
                    if (!territories.config().enforceTownRules()) {
                        return;
                    }
                    if (event instanceof Cancellable cancellable && cancellable.isCancelled()) {
                        return;
                    }
                    Object spawn = TownyReflection.invokeNoArg(event, "getSpawnLocation", "getLocation");
                    if (!(spawn instanceof Location location)) {
                        return;
                    }
                    String townName = TownyReflection.stringOrNull(
                            TownyReflection.invokeNoArg(event, "getTownName", "getName"));
                    if (!territories.canCreateTownAt(location, townName)) {
                        TownyReflection.cancelEvent(event, colorize(territories.config().denyCreateTown()));
                    }
                });
    }

    private static void wireNationJoin(
            RootTerritoriesPlugin plugin,
            Listener listener,
            TerritoryService territories,
            String... eventClassNames) {
        for (String className : eventClassNames) {
            wire(
                    plugin,
                    listener,
                    EventPriority.HIGH,
                    className,
                    event -> {
                        if (!territories.config().enforceTownRules()) {
                            return;
                        }
                        if (event instanceof Cancellable cancellable && cancellable.isCancelled()) {
                            return;
                        }
                        Object town = TownyReflection.invokeNoArg(event, "getTown");
                        Object nation = TownyReflection.invokeNoArg(event, "getNation");
                        if (!territories.canTownJoinNation(town, nation)) {
                            TownyReflection.cancelEvent(event, colorize(territories.config().denyJoinNation()));
                        }
                    });
        }
    }

    private static void wire(
            RootTerritoriesPlugin plugin,
            Listener listener,
            EventPriority priority,
            String eventClassName,
            java.util.function.Consumer<Event> handler) {
        try {
            Class<?> raw = TownyReflection.loadClass(eventClassName);
            if (!Event.class.isAssignableFrom(raw)) {
                return;
            }
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) raw;
            PluginManager pm = plugin.getServer().getPluginManager();
            pm.registerEvent(eventClass, listener, priority, (l, event) -> handler.accept(event), plugin);
        } catch (Throwable ex) {
            plugin.getLogger().fine("Territory rules hook skipped for " + eventClassName + ": " + ex.getMessage());
        }
    }

    private static String colorize(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw == null ? "" : raw);
    }
}
