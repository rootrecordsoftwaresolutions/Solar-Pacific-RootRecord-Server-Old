package com.rootrecord.minecraft.rootavacore;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.command.PluginCommandRegistrar;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import com.rootrecord.minecraft.rootavacore.autonomy.AvaAutonomyService;
import com.rootrecord.minecraft.rootavacore.autonomy.AvaGiftListener;
import com.rootrecord.minecraft.rootavacore.autonomy.AvaStorageService;
import com.rootrecord.minecraft.rootavacore.economy.AvaEconomyService;
import com.rootrecord.minecraft.rootavacore.fill.AvaFillService;
import com.rootrecord.minecraft.rootavacore.hire.AvaChestService;
import com.rootrecord.minecraft.rootavacore.hire.AvaHireService;
import com.rootrecord.minecraft.rootavacore.powers.AvaGodLoadout;
import com.rootrecord.minecraft.rootavacore.powers.AvaPowersListener;
import com.rootrecord.minecraft.rootavacore.gui.AvaMenuListener;
import com.rootrecord.minecraft.rootavacore.presence.AvaPresenceService;
import com.rootrecord.minecraft.rootavacore.presence.PresenceSafetyListener;
import com.rootrecord.minecraft.rootavacore.schematic.AvaSchematicService;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Ava companion — presence, hire, autonomy (own-claim work), powers.
 */
public final class RootAvaCorePlugin extends JavaPlugin {

    private RootRecordYamlConfig yaml;
    private AvaConfig config;
    private AvaPresenceService presence;
    private AvaPowersListener powers;
    private AvaGodLoadout godLoadout;
    private AvaHireService hire;
    private AvaChestService playerChests;
    private AvaStorageService storage;
    private AvaAutonomyService autonomy;
    private AvaGiftListener gifts;
    private AvaSchematicService schematics;
    private AvaEconomyService economy;
    private AvaFillService fill;

    @Override
    public void onEnable() {
        RootRecordFolders.ensureDir(this);
        yaml = new RootRecordYamlConfig(this, RootRecordFolders.ROOT_AVA_CORE_CONFIG, "root-ava-core.yml");
        yaml.load();
        migrateHireFleet116();
        migrateHireTesting1g();
        migrateHireSlowDig();
        migrateHireContractScale();
        migrateHireClaimClear133();
        migrateHireDig2x137();
        config = new AvaConfig(yaml.config());

        presence = new AvaPresenceService(this);
        powers = new AvaPowersListener(this);
        godLoadout = new AvaGodLoadout(this);
        hire = new AvaHireService(this);
        playerChests = new AvaChestService(this);
        storage = new AvaStorageService(this);
        autonomy = new AvaAutonomyService(this, storage);
        gifts = new AvaGiftListener(this);
        schematics = new AvaSchematicService(this);
        economy = new AvaEconomyService(this);
        fill = new AvaFillService(this);

        getServer().getPluginManager().registerEvents(new PresenceSafetyListener(this, presence), this);
        getServer().getPluginManager().registerEvents(new AvaMenuListener(this), this);
        getServer().getPluginManager().registerEvents(powers, this);
        getServer().getPluginManager().registerEvents(gifts, this);
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent event) {
                if (powers.isAva(event.getPlayer()) && powers.playEnabled()) {
                    getServer().getScheduler().runTaskLater(
                            RootAvaCorePlugin.this, () -> godLoadout.apply(event.getPlayer()), 20L);
                }
                if (hire != null) {
                    getServer().getScheduler().runTaskLater(
                            RootAvaCorePlugin.this, () -> hire.onJoin(event.getPlayer()), 40L);
                }
            }
        }, this);

        bindAvaCommand();
        getServer().getScheduler().runTaskLater(this, () -> presence.startIfEnabled(), 40L);
        godLoadout.start();
        hire.start();
        autonomy.start();
        schematics.start();
        economy.start();
        fill.start();

        getLogger().info("Ava companion loaded (Root-Ava-Core v" + getDescription().getVersion() + ").");
    }

    @Override
    public void onDisable() {
        if (fill != null) {
            fill.stop();
        }
        if (economy != null) {
            economy.stop();
        }
        if (schematics != null) {
            schematics.stop();
        }
        if (autonomy != null) {
            autonomy.stop();
        }
        if (hire != null) {
            hire.stop();
        }
        if (godLoadout != null) {
            godLoadout.stop();
        }
        if (presence != null) {
            presence.stop();
        }
    }

    public AvaSchematicService schematics() {
        return schematics;
    }

    public void reloadAll() {
        yaml.load();
        migrateHireFleet116();
        migrateHireTesting1g();
        migrateHireSlowDig();
        migrateHireContractScale();
        migrateHireClaimClear133();
        migrateHireDig2x137();
        config = new AvaConfig(yaml.config());
        if (presence != null) {
            presence.reload();
        }
        if (schematics != null) {
            schematics.start();
        }
        if (economy != null) {
            economy.start();
        }
        getLogger().info("Root-Ava-Core reloaded.");
    }

    public AvaEconomyService economy() {
        return economy;
    }

    /** One-shot: home Ava keeps building + room for 6 hire jobs even if live yml still has 1.8.115 values. */
    private void migrateHireFleet116() {
        var cfg = yaml.config();
        if (cfg.getBoolean("hire.fleet-116", false)) return;
        cfg.set("autonomy.pause-when-hired", false);
        if (cfg.getInt("hire.concurrent-jobs", 2) < 6) {
            cfg.set("hire.concurrent-jobs", 6);
        }
        if (!cfg.isSet("hire.hour-minutes")) cfg.set("hire.hour-minutes", 60);
        if (!cfg.isSet("hire.hour-gold")) cfg.set("hire.hour-gold", 1.0);
        cfg.set("hire.fleet-116", true);
        yaml.save();
        getLogger().info("Hire fleet 1.8.116: pause-when-hired false, concurrent-jobs 6, hour hire ready.");
    }

    /** Testing: 1G for hour/sidekick/mine/teardown. Chunk + minedown keep full dig rates. */
    private void migrateHireTesting1g() {
        var cfg = yaml.config();
        boolean alreadyCheap =
                cfg.getDouble("hire.hour-gold", 300) <= 1
                        && cfg.getDouble("hire.mine-base-gold", 50) <= 1
                        && cfg.getDouble("hire.teardown-base-gold", 40) <= 1
                        && cfg.getDouble("hire.sidekick-gold-per-minute", 25) <= 0;
        if (alreadyCheap) {
            if (!cfg.getBoolean("hire.testing-1g", false)) {
                cfg.set("hire.testing-1g", true);
                yaml.save();
            }
            return;
        }
        cfg.set("hire.hour-gold", 1.0);
        cfg.set("hire.sidekick-gold", 1.0);
        cfg.set("hire.sidekick-gold-per-minute", 0.0);
        cfg.set("hire.mine-base-gold", 1.0);
        cfg.set("hire.mine-gold-per-block", 0.0);
        cfg.set("hire.teardown-base-gold", 1.0);
        cfg.set("hire.teardown-gold-per-block", 0.0);
        if (!cfg.isSet("hire.chunk-base-gold")) cfg.set("hire.chunk-base-gold", 50.0);
        if (!cfg.isSet("hire.chunk-gold-per-block")) cfg.set("hire.chunk-gold-per-block", 0.15);
        if (!cfg.isSet("hire.chunk-max-volume")) cfg.set("hire.chunk-max-volume", 50000);
        if (!cfg.isSet("hire.minedown-base-gold")) cfg.set("hire.minedown-base-gold", 50.0);
        if (!cfg.isSet("hire.minedown-gold-per-block")) cfg.set("hire.minedown-gold-per-block", 0.15);
        if (!cfg.isSet("hire.minedown-max-volume")) cfg.set("hire.minedown-max-volume", 8000);
        if (!cfg.isSet("hire.minedown-size")) cfg.set("hire.minedown-size", 3);
        cfg.set("hire.testing-1g", true);
        yaml.save();
        getLogger().info("Hire testing-1g: hour/sidekick/mine/teardown = 1G; chunk+minedown keep dig rates.");
    }

    /** Dig jobs: slow random pace, max 120 blocks/min. Old 24/tick every 2 ticks was a death loop. */
    private void migrateHireSlowDig() {
        var cfg = yaml.config();
        if (cfg.getBoolean("hire.slow-dig-129", false)
                && cfg.getInt("hire.max-blocks-per-minute", 0) >= 1
                && cfg.getInt("hire.tick-period", 2) >= 20) {
            return;
        }
        cfg.set("hire.blocks-per-tick", 2);
        cfg.set("hire.tick-period", 20);
        cfg.set("hire.max-blocks-per-minute", 120);
        cfg.set("hire.slow-dig-129", true);
        yaml.save();
        getLogger().info("Hire dig slowed: max 120 blocks/min, random 0–2/sec.");
    }

    /** Chunk/minedown: 1000G + 0.01/block, doubles each successful use. */
    private void migrateHireContractScale() {
        var cfg = yaml.config();
        if (cfg.getBoolean("hire.claim-clear-133", false)) {
            return;
        }
        if (cfg.getBoolean("hire.contract-scale-131", false)
                && cfg.getDouble("hire.chunk-base-gold", 0) >= 1000
                && cfg.getDouble("hire.minedown-base-gold", 0) >= 1000) {
            return;
        }
        cfg.set("hire.chunk-base-gold", 1000.0);
        cfg.set("hire.chunk-gold-per-block", 0.01);
        cfg.set("hire.minedown-base-gold", 1000.0);
        cfg.set("hire.minedown-gold-per-block", 0.01);
        cfg.set("hire.contract-scale-131", true);
        yaml.save();
        getLogger().info("Hire contracts: chunk/minedown start 1000G + 0.01/block, double each use.");
    }

    /** Claim-circle clear: no base, 0.01/block +0.01 per 100k, dig 2× (0–4/sec). */
    private void migrateHireClaimClear133() {
        var cfg = yaml.config();
        if (cfg.getBoolean("hire.claim-clear-133", false)
                && cfg.getDouble("hire.chunk-base-gold", 1) <= 0
                && cfg.getInt("hire.max-blocks-per-minute", 0) >= 240) {
            return;
        }
        cfg.set("hire.chunk-base-gold", 0.0);
        cfg.set("hire.chunk-gold-per-block", 0.01);
        cfg.set("hire.chunk-max-volume", 500000);
        cfg.set("hire.minedown-base-gold", 0.0);
        cfg.set("hire.minedown-gold-per-block", 0.01);
        cfg.set("hire.blocks-per-tick", 4);
        cfg.set("hire.tick-period", 20);
        cfg.set("hire.max-blocks-per-minute", 240);
        cfg.set("hire.claim-clear-133", true);
        yaml.save();
        getLogger().info("Hire claim-clear: no base, 0.01 G/block +0.01 per 100k, dig max 240/min.");
    }

    /** Hire dig 2× from 1.8.136: random 0–8/sec, max 480/min. Pace is in the plugin, not RCON. */
    private void migrateHireDig2x137() {
        var cfg = yaml.config();
        if (cfg.getBoolean("hire.dig-2x-137", false)
                && cfg.getInt("hire.max-blocks-per-minute", 0) >= 480
                && cfg.getInt("hire.blocks-per-tick", 0) >= 8) {
            return;
        }
        cfg.set("hire.blocks-per-tick", 8);
        cfg.set("hire.tick-period", 20);
        cfg.set("hire.max-blocks-per-minute", 480);
        cfg.set("hire.dig-2x-137", true);
        yaml.save();
        getLogger().info("Hire dig 2×: max 480 blocks/min, random 0–8/sec.");
    }

    public AvaConfig config() {
        return config;
    }

    public AvaPresenceService presence() {
        return presence;
    }

    public AvaPowersListener powers() {
        return powers;
    }

    public AvaGodLoadout godLoadout() {
        return godLoadout;
    }

    public AvaHireService hire() {
        return hire;
    }

    public AvaFillService fill() {
        return fill;
    }

    public AvaChestService playerChests() {
        return playerChests;
    }

    public AvaAutonomyService autonomy() {
        return autonomy;
    }

    public AvaGiftListener gifts() {
        return gifts;
    }

    public AvaStorageService storage() {
        return storage;
    }

    private void bindAvaCommand() {
        PluginCommand cmd = getCommand("ava");
        if (cmd == null) {
            cmd = PluginCommandRegistrar.register(
                    this,
                    "ava",
                    "Ava companion",
                    "/ava [help|hire|chest|speed|work|gift|eco|presence|play|stop]",
                    List.of());
        }
        if (cmd == null) {
            getLogger().severe("Could not bind /ava");
            return;
        }
        AvaCommand handler = new AvaCommand(this);
        cmd.setExecutor(handler);
        cmd.setTabCompleter(handler);
    }

    @SuppressWarnings("deprecation")
    public String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }
}
