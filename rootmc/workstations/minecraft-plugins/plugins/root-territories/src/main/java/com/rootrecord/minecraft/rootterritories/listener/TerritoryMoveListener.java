package com.rootrecord.minecraft.rootterritories.listener;

import com.rootrecord.minecraft.rootterritories.config.TerritoriesConfig;
import com.rootrecord.minecraft.rootterritories.model.NationTerritory;
import com.rootrecord.minecraft.rootterritories.model.TownTerritory;
import com.rootrecord.minecraft.rootterritories.prefs.TerritoryNotificationStore;
import com.rootrecord.minecraft.rootterritories.service.TerritoryService;
import com.rootrecord.minecraft.rootterritories.towny.TownyResidents;
import com.rootrecord.minecraft.rootterritories.util.TerritoryMessages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TerritoryMoveListener implements Listener {

    private final TerritoryService territories;
    private final TerritoryNotificationStore notifications;
    private final ConcurrentHashMap<String, Long> messageCooldowns = new ConcurrentHashMap<>();

    public TerritoryMoveListener(TerritoryService territories, TerritoryNotificationStore notifications) {
        this.territories = territories;
        this.notifications = notifications;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        territories.syncPlayerZonesSilently(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        handlePosition(event.getPlayer(), event.getTo().getWorld().getName(), event.getTo().getX(), event.getTo().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        handlePosition(event.getPlayer(), event.getTo().getWorld().getName(), event.getTo().getX(), event.getTo().getZ());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        territories.clearPlayer(event.getPlayer().getUniqueId());
        clearCooldowns(event.getPlayer().getUniqueId());
    }

    private void handlePosition(Player player, String world, double x, double z) {
        if (!territories.config().enabled()) {
            return;
        }
        TerritoriesConfig cfg = territories.config();
        double px = Math.floor(x) + 0.5;
        double pz = Math.floor(z) + 0.5;

        UUID playerId = player.getUniqueId();
        Set<String> now = territories.zonesAt(world, px, pz, 0);
        Set<String> before = territories.activeZoneKeys(playerId);

        if (before.equals(now)) {
            return;
        }

        Map<String, NationTerritory> nationIndex = territories.nationIndex();
        Map<String, TownTerritory> townIndex = territories.townIndex();

        Set<String> entered = new HashSet<>(now);
        entered.removeAll(before);
        Set<String> exited = new HashSet<>(before);
        exited.removeAll(now);

        for (String key : entered) {
            if (!shouldAnnounceBorder(player, key, nationIndex, townIndex)) {
                continue;
            }
            if (!canAnnounce(playerId, key, "enter", cfg)) {
                continue;
            }
            if (key.startsWith("nation:")) {
                NationTerritory nation = nationIndex.get(key);
                if (nation != null) {
                    TerritoryMessages.sendBorderAlert(player, formatEnterNation(cfg, nation));
                }
            } else if (key.startsWith("town:")) {
                TownTerritory town = townIndex.get(key);
                if (town != null) {
                    TerritoryMessages.sendBorderAlert(player, formatEnterTown(cfg, town));
                }
            }
        }
        for (String key : exited) {
            if (!shouldAnnounceBorder(player, key, nationIndex, townIndex)) {
                continue;
            }
            if (!canAnnounce(playerId, key, "exit", cfg)) {
                continue;
            }
            if (key.startsWith("nation:")) {
                NationTerritory nation = nationIndex.get(key);
                if (nation != null) {
                    TerritoryMessages.sendBorderAlert(player, formatExitNation(cfg, nation));
                }
            } else if (key.startsWith("town:")) {
                TownTerritory town = townIndex.get(key);
                if (town != null) {
                    TerritoryMessages.sendBorderAlert(player, formatExitTown(cfg, town));
                }
            }
        }
        territories.updatePlayerZones(playerId, now);
    }

    private boolean shouldAnnounceBorder(
            Player player,
            String zoneKey,
            Map<String, NationTerritory> nationIndex,
            Map<String, TownTerritory> townIndex) {
        if (notifications.isEnabled(player.getUniqueId())) {
            return true;
        }
        String playerNation = TownyResidents.playerNationName(player);
        if (playerNation == null || playerNation.isBlank()) {
            return false;
        }
        if (zoneKey.startsWith("nation:")) {
            NationTerritory nation = nationIndex.get(zoneKey);
            if (nation == null || playerNation.equalsIgnoreCase(nation.nationName())) {
                return false;
            }
            return TownyResidents.areNationsAllied(playerNation, nation.nationName());
        }
        if (zoneKey.startsWith("town:")) {
            TownTerritory town = townIndex.get(zoneKey);
            if (town == null) {
                return false;
            }
            String townNation = TownyResidents.townNationName(town.townName());
            if (townNation == null || playerNation.equalsIgnoreCase(townNation)) {
                return false;
            }
            return TownyResidents.areNationsAllied(playerNation, townNation);
        }
        return false;
    }

    private boolean canAnnounce(UUID playerId, String zoneKey, String type, TerritoriesConfig cfg) {
        long cooldownMs = cfg.messageCooldownSeconds() * 1000L;
        if (cooldownMs <= 0) {
            return true;
        }
        String key = playerId + ":" + zoneKey + ":" + type;
        long now = System.currentTimeMillis();
        Long last = messageCooldowns.get(key);
        if (last != null && now - last < cooldownMs) {
            return false;
        }
        messageCooldowns.put(key, now);
        return true;
    }

    private void clearCooldowns(UUID playerId) {
        String prefix = playerId + ":";
        messageCooldowns.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static String formatEnterNation(TerritoriesConfig cfg, NationTerritory nation) {
        return cfg.enterNation().replace("{nation}", nation.nationName());
    }

    private static String formatExitNation(TerritoriesConfig cfg, NationTerritory nation) {
        return cfg.exitNation().replace("{nation}", nation.nationName());
    }

    private static String formatEnterTown(TerritoriesConfig cfg, TownTerritory town) {
        return cfg.enterTown().replace("{town}", town.townName());
    }

    private static String formatExitTown(TerritoriesConfig cfg, TownTerritory town) {
        return cfg.exitTown().replace("{town}", town.townName());
    }
}
