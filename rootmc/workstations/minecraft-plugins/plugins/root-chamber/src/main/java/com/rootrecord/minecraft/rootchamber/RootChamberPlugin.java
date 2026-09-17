package com.rootrecord.minecraft.rootchamber;

import com.rootrecord.minecraft.rootspawn.RootSpawnPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class RootChamberPlugin extends JavaPlugin {

    private RootSpawnPlugin spawn;
    private ChamberCooldownStore cooldowns;
    private ChamberMinigameManager minigame;

    @Override
    public void onEnable() {
        Plugin dep = Bukkit.getPluginManager().getPlugin("Root-Spawn");
        if (!(dep instanceof RootSpawnPlugin rootSpawn)) {
            getLogger().severe("Root-Spawn not found. Disabling Root-Chamber.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        this.spawn = rootSpawn;
        this.cooldowns = new ChamberCooldownStore(this);
        this.minigame = new ChamberMinigameManager(this, spawn, cooldowns);
        spawn.setChamberRunStateProvider(minigame::isRunning);
        Bukkit.getPluginManager().registerEvents(new ChamberMinigameListener(spawn, minigame), this);
        Bukkit.getScheduler().runTaskLater(this, () -> spawn.chamberSpawners().refreshLoadedChunks(), 20L);
        getLogger().info("Root-Chamber " + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (spawn != null) {
            spawn.setChamberRunStateProvider(null);
            spawn.chamberSpawners().stopBoost();
        }
    }
}
