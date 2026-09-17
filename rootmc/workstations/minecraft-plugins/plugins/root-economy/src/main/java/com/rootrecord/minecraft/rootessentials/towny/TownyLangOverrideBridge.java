package com.rootrecord.minecraft.rootessentials.towny;

import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/** Suppresses Towny global new-day chat; RootMC economy heartbeat replaces it. */
public final class TownyLangOverrideBridge {

    private static final String[] SUPPRESS_KEYS = {"msg_new_day", "msg_new_day_tax"};

    private TownyLangOverrideBridge() {}

    public static void register(RootEconomyPlugin plugin) {
        if (TownyReflection.townyPlugin() == null) {
            return;
        }
        registerTranslationLoadHook(plugin);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> applySuppressions(plugin), 1L);
    }

    private static void registerTranslationLoadHook(RootEconomyPlugin plugin) {
        Class<? extends Event> eventClass = TownyReflection.loadEventClass(
                "com.palmergames.bukkit.towny.event.TranslationLoadEvent");
        if (eventClass == null) {
            return;
        }
        Listener listener = new Listener() {};
        plugin.getServer().getPluginManager().registerEvent(
                eventClass,
                listener,
                EventPriority.NORMAL,
                (l, event) -> addSuppressionsToEvent(event),
                plugin);
    }

    private static void addSuppressionsToEvent(Event event) {
        try {
            Method addTranslation = event.getClass().getMethod(
                    "addTranslation", String.class, String.class, String.class);
            for (String locale : loadedLocales()) {
                for (String key : SUPPRESS_KEYS) {
                    addTranslation.invoke(event, locale, key, "");
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Towny reload hook unavailable â€” startup apply still runs.
        }
    }

    private static void applySuppressions(RootEconomyPlugin plugin) {
        try {
            Class<?> translation = TownyReflection.loadClass(
                    "com.palmergames.bukkit.towny.object.Translation");
            Method addTranslations = translation.getMethod("addTranslations", Map.class);
            Map<String, Map<String, String>> batch = new HashMap<>();
            for (String locale : loadedLocales()) {
                Map<String, String> overrides = new HashMap<>();
                for (String key : SUPPRESS_KEYS) {
                    overrides.put(key, "");
                }
                batch.put(locale, overrides);
            }
            if (batch.isEmpty()) {
                Map<String, String> fallback = new HashMap<>();
                for (String key : SUPPRESS_KEYS) {
                    fallback.put(key, "");
                }
                batch.put("en_US", fallback);
            }
            addTranslations.invoke(null, batch);
            plugin.getLogger().info("Towny new-day chat suppressed (economy heartbeat replaces it).");
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning(
                    "Towny new-day suppress failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Iterable<String> loadedLocales() {
        try {
            Class<?> translation = TownyReflection.loadClass(
                    "com.palmergames.bukkit.towny.object.Translation");
            Field translations = translation.getDeclaredField("translations");
            translations.setAccessible(true);
            Map<String, Map<String, String>> map =
                    (Map<String, Map<String, String>>) translations.get(null);
            if (map == null || map.isEmpty()) {
                return java.util.List.of("en_US");
            }
            return map.keySet();
        } catch (ReflectiveOperationException ex) {
            return java.util.List.of("en_US");
        }
    }
}
