package com.rootrecord.minecraft.rootclaims;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ground-level particle ring on the outer perimeter of each owner's claim union
 * (overlapping claims blend into one outline - no interior edges).
 */
public final class ClaimOutlineTask {

    private final RootClaimsPlugin plugin;
    private final ClaimStore store;
    private final Set<UUID> enabledPlayers = new HashSet<>();
    private BukkitTask task;

    public ClaimOutlineTask(RootClaimsPlugin plugin, ClaimStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public void start() {
        stop();
        if (!plugin.outlineEnabled()) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, plugin.outlineRefreshTicks());
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (!enabledPlayers.remove(uuid)) {
            enabledPlayers.add(uuid);
            drawFor(player);
            return true;
        }
        return false;
    }

    public void clear(UUID playerId) {
        enabledPlayers.remove(playerId);
    }

    private void tick() {
        if (!plugin.outlineEnabled() || enabledPlayers.isEmpty()) {
            return;
        }
        enabledPlayers.removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
        for (UUID uuid : enabledPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                drawFor(player);
            }
        }
    }

    private void drawFor(Player player) {
        World world = player.getWorld();
        Location playerLoc = player.getLocation();
        double maxDistance = plugin.outlineMaxDistanceBlocks();
        double maxDistanceSquared = maxDistance * maxDistance;
        List<ClaimRecord> nearby = new ArrayList<>();
        for (ClaimRecord claim : store.all()) {
            if (!claim.key().world().equals(world.getName())) {
                continue;
            }
            double dx = claim.key().x() - playerLoc.getX();
            double dz = claim.key().z() - playerLoc.getZ();
            double reach = maxDistance + claim.radiusBlocks();
            if (dx * dx + dz * dz > reach * reach) {
                continue;
            }
            nearby.add(claim);
        }
        if (nearby.isEmpty()) {
            return;
        }
        int step = Math.max(2, plugin.outlineContourStep());
        double spacing = Math.max(0.9, plugin.outlineParticleSpacing());
        Particle.DustOptions ownDust = new Particle.DustOptions(Color.LIME, plugin.outlineParticleSize());
        Particle.DustOptions friendDust = new Particle.DustOptions(Color.AQUA, plugin.outlineParticleSize());
        Particle.DustOptions otherDust = new Particle.DustOptions(Color.RED, plugin.outlineParticleSize());

        for (Map.Entry<UUID, List<ClaimRecord>> entry : ClaimUnionGeometry.groupByOwner(nearby).entrySet()) {
            List<ClaimRecord> owned = entry.getValue();
            Particle.DustOptions dust;
            if (entry.getKey().equals(player.getUniqueId())) {
                dust = ownDust;
            } else if (owned.stream().anyMatch(c ->
                    store.areaRoot(c).trusted().containsKey(player.getUniqueId()))) {
                dust = friendDust;
            } else {
                dust = otherDust;
            }
            for (double[] point : ClaimUnionGeometry.samplePerimeter(owned, step, spacing)) {
                double x = point[0];
                double z = point[1];
                double dx = x - playerLoc.getX();
                double dz = z - playerLoc.getZ();
                if (dx * dx + dz * dz > maxDistanceSquared) {
                    continue;
                }
                int groundY = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z));
                double y = groundY + 1.05;
                player.spawnParticle(
                        Particle.DUST,
                        new Location(world, x, y, z),
                        1,
                        0,
                        0,
                        0,
                        0,
                        dust);
            }
        }
    }
}
