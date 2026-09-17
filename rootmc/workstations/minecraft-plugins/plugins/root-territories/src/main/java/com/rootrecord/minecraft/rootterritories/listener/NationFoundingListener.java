package com.rootrecord.minecraft.rootterritories.listener;

import com.rootrecord.minecraft.rootterritories.RootTerritoriesPlugin;
import com.rootrecord.minecraft.rootterritories.nation.NationFoundingProposal;
import com.rootrecord.minecraft.rootterritories.nation.NationFoundingService;
import com.rootrecord.minecraft.rootterritories.towny.TownyReflection;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;

import java.util.Optional;
import java.util.function.Consumer;

/** Blocks /nation new unless an approved multi-town founding proposal exists; collects split cost. */
public final class NationFoundingListener {

    private NationFoundingListener() {}

    public static void register(RootTerritoriesPlugin plugin) {
        NationFoundingService founding = plugin.nationFounding();
        if (founding == null || !founding.proposalsRequired()) {
            return;
        }
        Listener listener = new Listener() {};
        Consumer<Event> handler = event -> onPreNewNation(plugin, founding, event);
        wire(plugin, listener, handler, "com.palmergames.bukkit.towny.event.PreNewNationEvent");
        wire(plugin, listener, handler, "com.palmergames.bukkit.towny.event.nation.PreNewNationEvent");
        plugin.getLogger().info("Nation founding proposal gate registered.");
    }

    private static void onPreNewNation(RootTerritoriesPlugin plugin, NationFoundingService founding, Event event) {
        if (event instanceof Cancellable cancellable && cancellable.isCancelled()) {
            return;
        }
        String nationName = TownyReflection.stringOrNull(
                TownyReflection.invokeNoArg(event, "getNationName", "getName"));
        Object town = TownyReflection.invokeNoArg(event, "getTown");
        String capitalTown = TownyReflection.stringOrNull(TownyReflection.invokeNoArg(town, "getName"));
        if (nationName == null || capitalTown == null) {
            return;
        }
        Optional<String> validation = founding.validateCreation(nationName, capitalTown);
        if (validation.isPresent()) {
            TownyReflection.cancelEvent(event, NationFoundingService.colorize(validation.get()));
            return;
        }
        Optional<NationFoundingProposal> approved =
                founding.store().findApprovedForCreation(nationName, capitalTown);
        if (approved.isEmpty()) {
            return;
        }
        Optional<String> payError = founding.executePayments(approved.get());
        if (payError.isPresent()) {
            TownyReflection.cancelEvent(event, NationFoundingService.colorize(payError.get()));
            return;
        }
        NationFoundingProposal executed = approved.get();
        plugin.getServer().broadcastMessage(NationFoundingService.colorize(
                founding.config().msgExecuted()
                        .replace("{nation}", executed.nationName())
                        .replace("{total}", NationFoundingService.formatGold(founding.config().totalCostGold()))
                        .replace("{towns}", String.valueOf(executed.allTowns().size()))));
    }

    private static void wire(
            RootTerritoriesPlugin plugin,
            Listener listener,
            Consumer<Event> handler,
            String eventClassName) {
        try {
            Class<?> raw = TownyReflection.loadClass(eventClassName);
            if (!Event.class.isAssignableFrom(raw)) {
                return;
            }
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) raw;
            PluginManager pm = plugin.getServer().getPluginManager();
            pm.registerEvent(eventClass, listener, EventPriority.HIGH, (l, event) -> handler.accept(event), plugin);
        } catch (Throwable ex) {
            plugin.getLogger().fine("Nation founding hook skipped for " + eventClassName + ": " + ex.getMessage());
        }
    }
}
