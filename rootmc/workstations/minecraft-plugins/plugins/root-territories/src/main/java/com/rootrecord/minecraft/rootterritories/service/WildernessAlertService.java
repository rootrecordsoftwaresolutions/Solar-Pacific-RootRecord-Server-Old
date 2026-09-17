package com.rootrecord.minecraft.rootterritories.service;

import com.rootrecord.minecraft.rootterritories.config.TerritoriesConfig;
import com.rootrecord.minecraft.rootterritories.model.NationTerritory;
import com.rootrecord.minecraft.rootterritories.model.TownTerritory;
import com.rootrecord.minecraft.rootterritories.model.WildernessAt;
import com.rootrecord.minecraft.rootterritories.towny.TownyResidents;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WildernessAlertService {

    private final TerritoryService territories;
    private final ConcurrentHashMap<String, Long> cooldowns = new ConcurrentHashMap<>();

    public WildernessAlertService(TerritoryService territories) {
        this.territories = territories;
    }

    public void handleBlockChange(Player actor, Location location) {
        TerritoriesConfig cfg = territories.config();
        if (!cfg.enabled() || !cfg.wildernessAlertsEnabled() || actor == null || location == null) {
            return;
        }
        if (location.getWorld() == null) {
            return;
        }
        if (!isSurfaceLevel(location, cfg)) {
            return;
        }

        double x = location.getBlockX() + 0.5;
        double z = location.getBlockZ() + 0.5;
        WildernessAt hits = territories.wildernessAt(location.getWorld().getName(), x, z);
        if (hits.isEmpty()) {
            return;
        }

        String actorName = actor.getName();
        UUID actorId = actor.getUniqueId();
        long cooldownMs = cfg.wildernessAlertCooldownMs();

        for (String townName : hits.townNames()) {
            if (TownyResidents.isResidentOfTown(actor, townName)) {
                continue;
            }
            String townNation = TownyResidents.townNationName(townName);
            if (townNation != null && TownyResidents.isResidentOfNation(actor, townNation)) {
                continue;
            }
            if (TownyResidents.isFriendOfAnyTownResident(actor, townName)) {
                continue;
            }
            if (!tryCooldown("town:" + townName.toLowerCase() + ":" + actorId, cooldownMs)) {
                continue;
            }
            String message = colorize(cfg.wildernessAlertTown().replace("{player}", actorName));
            for (Player resident : TownyResidents.onlineTownResidents(townName)) {
                if (!resident.equals(actor)) {
                    resident.sendMessage(message);
                }
            }
        }

        for (String nationName : hits.nationNames()) {
            if (TownyResidents.isResidentOfNation(actor, nationName)) {
                continue;
            }
            if (TownyResidents.isFriendOfAnyNationResident(actor, nationName)) {
                continue;
            }
            if (!tryCooldown("nation:" + nationName.toLowerCase() + ":" + actorId, cooldownMs)) {
                continue;
            }
            String message = colorize(cfg.wildernessAlertNation().replace("{player}", actorName));
            for (Player resident : TownyResidents.onlineNationResidents(nationName)) {
                if (!resident.equals(actor)) {
                    resident.sendMessage(message);
                }
            }
        }
    }

    private boolean tryCooldown(String key, long cooldownMs) {
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(key);
        if (last != null && now - last < cooldownMs) {
            return false;
        }
        cooldowns.put(key, now);
        return true;
    }

    private static boolean isSurfaceLevel(Location location, TerritoriesConfig cfg) {
        int sea = location.getWorld().getSeaLevel();
        return location.getBlockY() > sea + cfg.wildernessMinYAboveSea();
    }

    private static String colorize(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw == null ? "" : raw);
    }
}
