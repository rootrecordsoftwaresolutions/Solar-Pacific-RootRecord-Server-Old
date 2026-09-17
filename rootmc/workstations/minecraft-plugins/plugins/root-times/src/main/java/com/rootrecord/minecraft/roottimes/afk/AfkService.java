package com.rootrecord.minecraft.roottimes.afk;

import com.rootrecord.minecraft.roottimes.RootTimesPlugin;
import com.rootrecord.minecraft.roottimes.mysql.AfkSessionStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("deprecation")
public final class AfkService implements Listener {

    private final RootTimesPlugin plugin;
    private final Map<UUID, Boolean> afk = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastActiveMs = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> afkStarted = new ConcurrentHashMap<>();
    private final Map<UUID, Long> afkSecondsToday = new ConcurrentHashMap<>();
    private BukkitTask idleTask;

    public AfkService(RootTimesPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.timesConfig().afkEnabled()) {
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        idleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::scanIdle, 20L * 5L, 20L * 5L);
        for (Player player : Bukkit.getOnlinePlayers()) {
            touch(player.getUniqueId());
        }
    }

    public void stop() {
        if (idleTask != null) {
            idleTask.cancel();
            idleTask = null;
        }
        HandlerList.unregisterAll(this);
        for (UUID uuid : afk.keySet().toArray(UUID[]::new)) {
            if (Boolean.TRUE.equals(afk.get(uuid))) {
                clearAfk(uuid, false);
            }
        }
        afk.clear();
        lastActiveMs.clear();
        afkStarted.clear();
    }

    public boolean isAfk(UUID uuid) {
        return Boolean.TRUE.equals(afk.get(uuid));
    }

    public long afkSecondsToday(UUID uuid) {
        return afkSecondsToday.getOrDefault(uuid, 0L);
    }

    public boolean toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (isAfk(uuid)) {
            clearAfk(uuid, true);
            return false;
        }
        markAfk(player, true);
        return true;
    }

    public void touch(UUID uuid) {
        lastActiveMs.put(uuid, System.currentTimeMillis());
        if (isAfk(uuid)) {
            clearAfk(uuid, true);
        }
    }

    private void scanIdle() {
        if (!plugin.timesConfig().afkEnabled()) {
            return;
        }
        long afterMs = plugin.timesConfig().afkAfterSeconds() * 1000L;
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (player.hasPermission("roottimes.bypass")) {
                continue;
            }
            lastActiveMs.putIfAbsent(uuid, now);
            if (isAfk(uuid)) {
                continue;
            }
            long last = lastActiveMs.getOrDefault(uuid, now);
            if (now - last >= afterMs) {
                markAfk(player, true);
            }
        }
    }

    private void markAfk(Player player, boolean broadcast) {
        UUID uuid = player.getUniqueId();
        afk.put(uuid, true);
        afkStarted.put(uuid, Instant.now());
        if (broadcast && plugin.timesConfig().afkBroadcast()) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes(
                    '&', "&7" + player.getName() + " is now AFK."));
        }
    }

    private void clearAfk(UUID uuid, boolean broadcast) {
        Instant started = afkStarted.remove(uuid);
        afk.put(uuid, false);
        lastActiveMs.put(uuid, System.currentTimeMillis());
        if (started != null) {
            Instant end = Instant.now();
            long secs = Math.max(0L, end.getEpochSecond() - started.getEpochSecond());
            afkSecondsToday.merge(uuid, secs, Long::sum);
            persistSession(uuid, started, end);
        }
        if (broadcast && plugin.timesConfig().afkBroadcast()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes(
                        '&', "&7" + player.getName() + " is no longer AFK."));
            }
        }
    }

    private void persistSession(UUID uuid, Instant start, Instant end) {
        if (!plugin.mysql().ready()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (var c = plugin.mysql().open()) {
                AfkSessionStore.recordSession(c, plugin.timesConfig(), uuid, start, end);
            } catch (Exception ex) {
                plugin.getLogger().warning("AFK session save failed: " + ex.getMessage());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        touch(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> touch(event.getPlayer().getUniqueId()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        touch(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (isAfk(uuid)) {
            clearAfk(uuid, false);
        }
        lastActiveMs.remove(uuid);
        afk.remove(uuid);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (isAfk(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
