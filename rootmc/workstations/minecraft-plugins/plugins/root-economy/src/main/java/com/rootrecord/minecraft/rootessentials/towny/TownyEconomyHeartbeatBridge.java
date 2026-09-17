package com.rootrecord.minecraft.rootessentials.towny;

import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import com.rootrecord.minecraft.rootessentials.service.EconomyHeartbeatService;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Sends personal economy heartbeats when Towny runs its new-day task. */
public final class TownyEconomyHeartbeatBridge {

    private TownyEconomyHeartbeatBridge() {}

    public static void register(RootEconomyPlugin plugin, EconomyHeartbeatService heartbeat) {
        if (TownyReflection.townyPlugin() == null || heartbeat == null) {
            return;
        }
        Class<? extends Event> eventClass = TownyReflection.loadEventClass(
                "com.palmergames.bukkit.towny.event.NewDayEvent",
                "com.palmergames.bukkit.towny.event.time.NewDayEvent");
        if (eventClass == null) {
            plugin.getLogger().warning("Towny economy heartbeat: NewDayEvent class not found.");
            return;
        }
        Listener listener = new Listener() {};
        plugin.getServer().getPluginManager().registerEvent(
                eventClass,
                listener,
                EventPriority.MONITOR,
                (l, event) -> heartbeat.onNewDay(Bukkit.getOnlinePlayers()),
                plugin);
        plugin.getLogger().info("Towny economy heartbeat registered on NewDayEvent.");
    }
}
