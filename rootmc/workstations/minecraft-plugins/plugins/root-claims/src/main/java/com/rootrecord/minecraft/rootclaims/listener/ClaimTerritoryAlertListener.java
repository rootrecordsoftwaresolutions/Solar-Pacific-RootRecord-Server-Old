package com.rootrecord.minecraft.rootclaims.listener;

import com.rootrecord.minecraft.rootclaims.ClaimRecord;
import com.rootrecord.minecraft.rootclaims.ClaimService;
import com.rootrecord.minecraft.rootclaims.ClaimStore;
import com.rootrecord.minecraft.rootclaims.RootClaimsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.concurrent.ConcurrentHashMap;

/** Satellite alerts when strangers destroy/place in a claim's wilderness territory band. */
public final class ClaimTerritoryAlertListener implements Listener {

    private final RootClaimsPlugin plugin;
    private final ClaimStore store;
    private final ClaimService claims;
    private final ConcurrentHashMap<String, Long> cooldowns = new ConcurrentHashMap<>();

    public ClaimTerritoryAlertListener(RootClaimsPlugin plugin, ClaimStore store, ClaimService claims) {
        this.plugin = plugin;
        this.store = store;
        this.claims = claims;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        alert(event.getPlayer(), event.getBlock().getWorld().getName(),
                event.getBlock().getX(), event.getBlock().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        alert(event.getPlayer(), event.getBlock().getWorld().getName(),
                event.getBlock().getX(), event.getBlock().getZ());
    }

    private void alert(Player actor, String world, int x, int z) {
        if (!plugin.territoryAlertsEnabled() || actor == null || world == null) {
            return;
        }
        ClaimRecord territory = findTerritory(world, x, z);
        if (territory == null || claims.isAllied(actor, territory) || claims.isAdmin(actor)) {
            return;
        }
        long cooldownMs = plugin.territoryAlertCooldownMs();
        String key = territory.key().storageId() + ":" + actor.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(key);
        if (last != null && now - last < cooldownMs) {
            return;
        }
        cooldowns.put(key, now);

        ClaimRecord root = store.areaRoot(territory);
        String message = plugin.msg("territory-satellite-alert").replace("{player}", actor.getName());
        Player owner = plugin.getServer().getPlayer(root.ownerId());
        if (owner != null && owner.isOnline() && !owner.getUniqueId().equals(actor.getUniqueId())) {
            owner.sendMessage(message);
        }
        for (var entry : root.trusted().entrySet()) {
            Player friend = plugin.getServer().getPlayer(entry.getKey());
            if (friend != null
                    && friend.isOnline()
                    && !friend.getUniqueId().equals(actor.getUniqueId())) {
                friend.sendMessage(message);
            }
        }
    }

    private ClaimRecord findTerritory(String world, int x, int z) {
        int buffer = plugin.territoryBufferBlocks();
        if (buffer <= 0) {
            return null;
        }
        ClaimRecord best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ClaimRecord claim : store.all()) {
            if (!claim.containsTerritory(world, x, z, buffer)) {
                continue;
            }
            double distance = claim.horizontalDistance(x, z);
            if (distance < bestDistance) {
                best = claim;
                bestDistance = distance;
            }
        }
        return best;
    }
}
