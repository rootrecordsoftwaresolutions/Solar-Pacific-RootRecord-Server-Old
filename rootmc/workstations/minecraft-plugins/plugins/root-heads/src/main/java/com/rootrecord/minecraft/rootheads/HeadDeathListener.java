package com.rootrecord.minecraft.rootheads;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

public final class HeadDeathListener implements Listener {

    private static final String META_SPAWN_REASON = "rootheads_spawn_reason";

    private final RootHeadsPlugin plugin;

    public HeadDeathListener(RootHeadsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        event.getEntity().setMetadata(
                META_SPAWN_REASON,
                new FixedMetadataValue(plugin, event.getSpawnReason().name()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        HeadsConfig cfg = plugin.config();
        if (!cfg.enabled()) {
            return;
        }

        Entity entity = event.getEntity();
        Player killer = event.getEntity().getKiller();
        if (cfg.requireDirectPlayerKill() && killer == null) {
            return;
        }
        if (killer == null) {
            return;
        }

        if (entity instanceof Player victim) {
            handlePlayerKill(killer, victim);
            return;
        }

        if (deniedSpawn(entity, cfg)) {
            return;
        }

        HeadsConfig.MobHead mob = cfg.byEntity(entity.getType());
        if (mob == null) {
            return;
        }
        int looting = lootingLevel(killer);
        if (!plugin.drops().tryMobDrop(killer, mob, looting)) {
            return;
        }
        ItemStack head = plugin.items().createMobHead(mob, plugin.getPluginMeta().getVersion());
        entity.getWorld().dropItemNaturally(entity.getLocation(), head);
        killer.sendMessage(plugin.msg("drop").replace("{head}", strip(mob.displayName())));
    }

    private void handlePlayerKill(Player killer, Player victim) {
        HeadsConfig.PlayerHeads ph = plugin.config().playerHeads();
        if (!ph.enabled()) {
            return;
        }
        if (ph.requirePvp()) {
            // getKiller() already implies player combat contribution.
        }
        int looting = lootingLevel(killer);
        if (!plugin.drops().tryPlayerDrop(killer, victim, looting)) {
            return;
        }
        ItemStack head = plugin.items().createPlayerHead(victim, ph, plugin.getPluginMeta().getVersion());
        victim.getWorld().dropItemNaturally(victim.getLocation(), head);
        killer.sendMessage(plugin.msg("player-drop").replace("{player}", victim.getName()));
    }

    private boolean deniedSpawn(Entity entity, HeadsConfig cfg) {
        if (!cfg.denySpawner() && !cfg.denySpawnerEgg()) {
            return false;
        }
        if (!entity.hasMetadata(META_SPAWN_REASON)) {
            return false;
        }
        String reason = entity.getMetadata(META_SPAWN_REASON).getFirst().asString();
        if (cfg.denySpawner() && "SPAWNER".equals(reason)) {
            return true;
        }
        return cfg.denySpawnerEgg() && "SPAWNER_EGG".equals(reason);
    }

    private static int lootingLevel(Player killer) {
        ItemStack hand = killer.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            return 0;
        }
        return hand.getEnchantmentLevel(Enchantment.LOOTING);
    }

    private static String strip(String colored) {
        if (colored == null) {
            return "";
        }
        return colored.replaceAll("(?i)&[0-9a-fk-or]", "");
    }
}
