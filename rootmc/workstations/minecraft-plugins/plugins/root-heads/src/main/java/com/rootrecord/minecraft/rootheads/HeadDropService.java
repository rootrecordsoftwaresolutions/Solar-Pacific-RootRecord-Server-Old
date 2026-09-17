package com.rootrecord.minecraft.rootheads;

import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class HeadDropService {

    private final RootHeadsPlugin plugin;
    /** killerUuid|mobId → last drop millis */
    private final Map<String, Long> mobCooldowns = new ConcurrentHashMap<>();
    /** killerUuid|victimUuid → last drop millis */
    private final Map<String, Long> pairCooldowns = new ConcurrentHashMap<>();

    public HeadDropService(RootHeadsPlugin plugin) {
        this.plugin = plugin;
    }

    public void clearCooldowns() {
        mobCooldowns.clear();
        pairCooldowns.clear();
    }

    public boolean tryMobDrop(Player killer, HeadsConfig.MobHead mob, int lootingLevel) {
        HeadsConfig cfg = plugin.config();
        if (!cfg.enabled() || mob == null) {
            return false;
        }
        if (!killer.hasPermission("rootheads.drops")) {
            return false;
        }
        long now = System.currentTimeMillis();
        String key = killer.getUniqueId() + "|" + mob.id().toLowerCase(Locale.ROOT);
        long cd = cfg.sameMobCooldownMs();
        if (cd > 0) {
            Long last = mobCooldowns.get(key);
            if (last != null && now - last < cd) {
                return false;
            }
        }
        double chance = Math.min(1.0, mob.chance() + Math.max(0, lootingLevel) * mob.lootingBonus());
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return false;
        }
        if (cd > 0) {
            mobCooldowns.put(key, now);
        }
        return true;
    }

    public boolean tryPlayerDrop(Player killer, Player victim, int lootingLevel) {
        HeadsConfig.PlayerHeads ph = plugin.config().playerHeads();
        if (!plugin.config().enabled() || !ph.enabled()) {
            return false;
        }
        if (!killer.hasPermission("rootheads.drops")) {
            return false;
        }
        if (killer.getUniqueId().equals(victim.getUniqueId())) {
            return false;
        }
        long now = System.currentTimeMillis();
        String key = pairKey(killer.getUniqueId(), victim.getUniqueId());
        long cd = ph.samePairCooldownMs();
        if (cd > 0) {
            Long last = pairCooldowns.get(key);
            if (last != null && now - last < cd) {
                return false;
            }
        }
        double chance = Math.min(1.0, ph.chance() + Math.max(0, lootingLevel) * ph.lootingBonus());
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return false;
        }
        if (cd > 0) {
            pairCooldowns.put(key, now);
        }
        return true;
    }

    private static String pairKey(UUID killer, UUID victim) {
        return killer + "|" + victim;
    }
}
