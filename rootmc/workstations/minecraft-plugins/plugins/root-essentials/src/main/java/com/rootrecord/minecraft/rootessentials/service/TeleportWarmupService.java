package com.rootrecord.minecraft.rootessentials.service;

import com.rootrecord.minecraft.rootessentials.RootEssentialsPlugin;
import com.rootrecord.minecraft.rootessentials.util.Permissions;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class TeleportWarmupService {

    private final RootEssentialsPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Long> combatUntilMs = new ConcurrentHashMap<>();

    private int warmupSeconds = 3;
    private int combatTagSeconds = 30;

    public TeleportWarmupService(RootEssentialsPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload(int warmupSeconds, int combatTagSeconds) {
        this.warmupSeconds = Math.max(0, warmupSeconds);
        this.combatTagSeconds = Math.max(1, combatTagSeconds);
    }

    public boolean request(Player player, Location destination, Runnable onSuccess) {
        if (destination == null) {
            return false;
        }
        Location cloned = destination.clone();
        return request(player, () -> cloned, onSuccess);
    }

    public boolean request(Player player, Supplier<Location> destination, Runnable onSuccess) {
        if (player == null || destination == null) {
            return false;
        }
        if (bypass(player)) {
            Location target = destination.get();
            if (target == null || target.getWorld() == null) {
                return false;
            }
            executeNow(player, target, onSuccess);
            return true;
        }
        if (isCombatTagged(player)) {
            player.sendMessage(plugin.msg("teleport-combat"));
            return false;
        }
        cancel(player);

        if (warmupSeconds <= 0) {
            Location target = destination.get();
            if (target == null || target.getWorld() == null) {
                return false;
            }
            executeNow(player, target, onSuccess);
            return true;
        }

        Location anchor = player.getLocation().clone();
        long delayTicks = warmupSeconds * 20L;
        player.sendMessage(plugin.msg("teleport-warmup").replace("{seconds}", String.valueOf(warmupSeconds)));

        BukkitTask teleport = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pending.remove(player.getUniqueId());
            if (!player.isOnline()) {
                return;
            }
            if (moved(player, anchor)) {
                player.sendMessage(plugin.msg("teleport-cancelled-move"));
                return;
            }
            if (isCombatTagged(player)) {
                player.sendMessage(plugin.msg("teleport-combat"));
                return;
            }
            Location target = destination.get();
            if (target == null || target.getWorld() == null) {
                player.sendMessage(plugin.msg("teleport-failed"));
                return;
            }
            executeNow(player, target, onSuccess);
        }, delayTicks);

        pending.put(player.getUniqueId(), new Pending(anchor, teleport));
        return true;
    }

    public void cancel(Player player) {
        if (player == null) {
            return;
        }
        Pending wait = pending.remove(player.getUniqueId());
        if (wait != null) {
            wait.teleport().cancel();
        }
    }

    public void onQuit(UUID uuid) {
        pending.remove(uuid);
        combatUntilMs.remove(uuid);
    }

    public void onMove(Player player, Location from, Location to) {
        if (player == null || from == null || to == null) {
            return;
        }
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        if (!pending.containsKey(player.getUniqueId())) {
            return;
        }
        cancel(player);
        player.sendMessage(plugin.msg("teleport-cancelled-move"));
    }

    public void tagCombat(Player player) {
        if (player == null) {
            return;
        }
        combatUntilMs.put(player.getUniqueId(), System.currentTimeMillis() + combatTagSeconds * 1000L);
        cancel(player);
    }

    public boolean isCombatTagged(Player player) {
        if (player == null) {
            return false;
        }
        Long until = combatUntilMs.get(player.getUniqueId());
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            combatUntilMs.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private void executeNow(Player player, Location target, Runnable onSuccess) {
        player.teleport(target);
        if (onSuccess != null) {
            onSuccess.run();
        }
    }

    private static boolean moved(Player player, Location anchor) {
        Location now = player.getLocation();
        return now.getBlockX() != anchor.getBlockX()
                || now.getBlockY() != anchor.getBlockY()
                || now.getBlockZ() != anchor.getBlockZ();
    }

    private static boolean bypass(Player player) {
        return Permissions.has(player, "teleport.bypass");
    }

    private record Pending(Location anchor, BukkitTask teleport) {}
}
