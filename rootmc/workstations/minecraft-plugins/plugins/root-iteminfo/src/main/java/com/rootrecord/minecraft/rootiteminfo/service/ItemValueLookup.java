package com.rootrecord.minecraft.rootiteminfo.service;

import com.rootrecord.minecraft.rootiteminfo.RootItemInfoPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.OptionalDouble;

/** Average unit value from RootMC price registry, then Root-Essentials worth. */
public final class ItemValueLookup {

    private final RootItemInfoPlugin plugin;

    public ItemValueLookup(RootItemInfoPlugin plugin) {
        this.plugin = plugin;
    }

    public OptionalDouble averageUnitG(Material material) {
        if (material == null || material.isAir()) {
            return OptionalDouble.empty();
        }
        String key = RootItemInfoPlugin.materialKey(material);
        Double fromRootMc = tryRootMcAverage(key);
        if (fromRootMc != null && fromRootMc > 0) {
            return OptionalDouble.of(fromRootMc);
        }
        Double fromEssentials = tryEssentialsWorth(material);
        if (fromEssentials != null && fromEssentials > 0) {
            return OptionalDouble.of(fromEssentials);
        }
        return OptionalDouble.empty();
    }

    private Double tryRootMcAverage(String itemKey) {
        Plugin rootMc = Bukkit.getPluginManager().getPlugin("RootMC");
        if (rootMc == null || !rootMc.isEnabled()) {
            return null;
        }
        try {
            Method method = rootMc.getClass().getMethod("averagePrice", String.class);
            Object result = method.invoke(rootMc, itemKey);
            if (result instanceof Number number) {
                double v = number.doubleValue();
                return v > 0 ? v : null;
            }
        } catch (ReflectiveOperationException ignored) {
            // fall through
        }
        return null;
    }

    private Double tryEssentialsWorth(Material material) {
        Plugin essentials = Bukkit.getPluginManager().getPlugin("Root-Essentials");
        if (essentials == null || !essentials.isEnabled()) {
            return null;
        }
        try {
            Method method = essentials.getClass().getMethod("itemPrice", Material.class);
            Object result = method.invoke(essentials, material);
            if (result instanceof Number number) {
                return number.doubleValue();
            }
        } catch (ReflectiveOperationException ignored) {
            // no worth bridge
        }
        return null;
    }

    public Material resolveMaterial(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String raw = query.trim().toLowerCase(Locale.ROOT);
        if (raw.startsWith("minecraft:")) {
            raw = raw.substring("minecraft:".length());
        }
        if ("hand".equals(raw) || "held".equals(raw)) {
            return null;
        }
        if ("gold".equals(raw)) {
            return Material.GOLD_INGOT;
        }
        Material exact = Material.matchMaterial(raw);
        if (exact != null) {
            return exact;
        }
        Material underscored = Material.matchMaterial(raw.replace(' ', '_'));
        return underscored;
    }
}
