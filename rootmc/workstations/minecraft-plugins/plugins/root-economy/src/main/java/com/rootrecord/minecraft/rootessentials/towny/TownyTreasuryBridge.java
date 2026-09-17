package com.rootrecord.minecraft.rootessentials.towny;

import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;

/** Tags Towny closed-economy Vault deposits for treasury ledger details. */
public final class TownyTreasuryBridge {

    private TownyTreasuryBridge() {}

    public static void register(RootEconomyPlugin plugin) {
        if (TownyReflection.townyPlugin() == null) {
            return;
        }
        Listener listener = new Listener() {};
        wireEvent(
                plugin,
                listener,
                "towny:new-town",
                "com.palmergames.bukkit.towny.event.PreNewTownEvent",
                "com.palmergames.bukkit.towny.event.town.PreNewTownEvent");
        wireEvent(
                plugin,
                listener,
                "towny:new-nation",
                "com.palmergames.bukkit.towny.event.PreNewNationEvent",
                "com.palmergames.bukkit.towny.event.nation.PreNewNationEvent");
        wireSingle(plugin, listener, "com.palmergames.bukkit.towny.event.TownPreClaimEvent", null);
        wireSingle(
                plugin,
                listener,
                "com.palmergames.bukkit.towny.event.plot.changeowner.PlotPreClaimEvent",
                "towny:claim");
        wireSingle(plugin, listener, "com.palmergames.bukkit.towny.event.TownClaimEvent", "towny:claim");
        plugin.getLogger().info(
                "Towny treasury ledger channels registered (v"
                        + plugin.getDescription().getVersion()
                        + " â€” town bank mirror fix active).");
    }

    private static void wireEvent(
            RootEconomyPlugin plugin,
            Listener listener,
            String channel,
            String... classNames) {
        Class<? extends Event> eventClass = TownyReflection.loadEventClass(classNames);
        if (eventClass == null) {
            plugin.getLogger().warning("Towny treasury hook: no event class for channel " + channel);
            return;
        }
        registerChannel(plugin, listener, eventClass, channel);
    }

    private static void wireSingle(
            RootEconomyPlugin plugin,
            Listener listener,
            String eventClassName,
            String channel) {
        Class<? extends Event> eventClass = TownyReflection.loadEventClass(eventClassName);
        if (eventClass == null) {
            return;
        }
        if (channel == null && eventClassName.contains("TownPreClaimEvent")) {
            registerPreClaimChannel(plugin, listener, eventClass);
            return;
        }
        registerChannel(plugin, listener, eventClass, channel);
    }

    private static void registerPreClaimChannel(
            RootEconomyPlugin plugin,
            Listener listener,
            Class<? extends Event> eventClass) {
        PluginManager pm = plugin.getServer().getPluginManager();
        pm.registerEvent(
                eventClass,
                listener,
                EventPriority.LOWEST,
                (l, event) -> {
                    if (event instanceof Cancellable cancellable && cancellable.isCancelled()) {
                        return;
                    }
                    try {
                        boolean isOutpost = (boolean) eventClass.getMethod("isOutpost").invoke(event);
                        TownyTreasuryChannels.set(isOutpost ? "towny:outpost" : "towny:claim");
                    } catch (ReflectiveOperationException ex) {
                        TownyTreasuryChannels.set("towny:claim");
                    }
                },
                plugin);
        pm.registerEvent(
                eventClass,
                listener,
                EventPriority.MONITOR,
                (l, event) -> plugin.getServer().getScheduler().runTask(plugin, TownyTreasuryChannels::clear),
                plugin);
    }

    private static void registerChannel(
            RootEconomyPlugin plugin,
            Listener listener,
            Class<? extends Event> eventClass,
            String channel) {
        PluginManager pm = plugin.getServer().getPluginManager();
        pm.registerEvent(
                eventClass,
                listener,
                EventPriority.LOWEST,
                (l, event) -> {
                    if (event instanceof Cancellable cancellable && cancellable.isCancelled()) {
                        return;
                    }
                    TownyTreasuryChannels.set(channel);
                },
                plugin);
        pm.registerEvent(
                eventClass,
                listener,
                EventPriority.MONITOR,
                (l, event) -> plugin.getServer().getScheduler().runTask(plugin, TownyTreasuryChannels::clear),
                plugin);
    }
}
