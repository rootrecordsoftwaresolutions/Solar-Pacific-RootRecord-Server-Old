package com.rootrecord.minecraft.rootspawn;

import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

/** Disables the vanilla player waypoint strip (locator bar) added in Minecraft 1.21.6+. */
public final class LocatorBarDisabler implements Listener {

    private final RootSpawnPlugin plugin;

    public LocatorBarDisabler(RootSpawnPlugin plugin) {
        this.plugin = plugin;
    }

    public void applyToAllWorlds() {
        for (World world : Bukkit.getWorlds()) {
            apply(world);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        apply(event.getWorld());
    }

    private void apply(World world) {
        if (world == null) {
            return;
        }
        if (Boolean.FALSE.equals(world.getGameRuleValue(GameRules.LOCATOR_BAR))) {
            return;
        }
        world.setGameRule(GameRules.LOCATOR_BAR, false);
        plugin.getLogger().info("Disabled locator bar in world '" + world.getName() + "'.");
    }
}
