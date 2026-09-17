package com.rootrecord.minecraft.roottimes.track;

import com.rootrecord.minecraft.roottimes.RootTimesPlugin;
import com.rootrecord.minecraft.roottimes.ip.IpTimezoneResolver;
import com.rootrecord.minecraft.roottimes.mysql.ActivityHarvestStore;
import com.rootrecord.minecraft.roottimes.mysql.PlaytimeStore;
import com.rootrecord.minecraft.roottimes.timezone.PlayerTimezone;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Playtime + hourly activity harvest; excludes AFK seconds when configured. */
public final class SessionTracker implements Listener {

    private record Segment(long joinStartMs, long flushStartMs, String timezoneKey, int offsetMinutes) {}

    private final RootTimesPlugin plugin;
    private final Map<UUID, Segment> segments = new ConcurrentHashMap<>();
    private BukkitTask flushTask;
    private IpTimezoneResolver ipResolver;
    private volatile NamedSession bootLongest;

    public SessionTracker(RootTimesPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        var cfg = plugin.timesConfig();
        ipResolver = new IpTimezoneResolver(
                cfg.ipLookupEnabled(), cfg.ipLookupUrlTemplate(), cfg.ipCacheHours());
        Bukkit.getPluginManager().registerEvents(this, plugin);
        long ticks = Math.max(20L, cfg.flushIntervalSeconds() * 20L);
        flushTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushAll, ticks, ticks);
        for (Player player : Bukkit.getOnlinePlayers()) {
            begin(player);
        }
    }

    public void stop() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        flushAll();
        segments.clear();
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ensureLogin(player);
            Bukkit.getScheduler().runTask(plugin, () -> begin(player));
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        noteSessionEnd(player.getUniqueId(), player.getName());
        flushPlayer(player.getUniqueId(), true);
        segments.remove(player.getUniqueId());
    }

    /** Longest currently online session (wall clock since join). */
    public Optional<NamedSession> longestCurrentSession() {
        NamedSession best = null;
        long now = System.currentTimeMillis();
        for (var entry : segments.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            long ms = Math.max(0L, now - entry.getValue().joinStartMs());
            if (best == null || ms > best.millis()) {
                best = new NamedSession(player.getName(), ms);
            }
        }
        return Optional.ofNullable(best);
    }

    /** Best of current online + finished sessions this boot. */
    public Optional<NamedSession> longestSessionOverall() {
        Optional<NamedSession> current = longestCurrentSession();
        NamedSession finished = bootLongest;
        if (current.isEmpty()) {
            return Optional.ofNullable(finished);
        }
        if (finished == null || current.get().millis() >= finished.millis()) {
            return current;
        }
        return Optional.of(finished);
    }

    public record NamedSession(String name, long millis) {}

    private void noteSessionEnd(UUID uuid, String name) {
        Segment segment = segments.get(uuid);
        if (segment == null) {
            return;
        }
        long ms = Math.max(0L, System.currentTimeMillis() - segment.joinStartMs());
        NamedSession current = bootLongest;
        if (current == null || ms > current.millis()) {
            bootLongest = new NamedSession(name == null || name.isBlank() ? "?" : name, ms);
        }
    }

    private void begin(Player player) {
        UUID uuid = player.getUniqueId();
        String tzKey = resolveTimezoneKey(uuid);
        PlayerTimezone tz = PlayerTimezone.byKey(tzKey).orElse(PlayerTimezone.utc());
        long now = System.currentTimeMillis();
        segments.put(uuid, new Segment(now, now, tz.key(), tz.offsetMinutes()));
    }

    private void ensureLogin(Player player) {
        if (!plugin.mysql().ready() || !plugin.timesConfig().harvestPlaytime()) {
            return;
        }
        try (var c = plugin.mysql().open()) {
            PlaytimeStore.recordLogin(c, plugin.timesConfig(), player.getUniqueId(), player.getName());
            String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : null;
            Optional<String> existing =
                    ActivityHarvestStore.getTimezoneKey(c, plugin.timesConfig(), player.getUniqueId());
            if (existing.isEmpty()) {
                String tzKey = plugin.timesConfig().defaultTimezoneKey();
                String source = "times";
                if (ipResolver != null && IpTimezoneResolver.isResolvableIp(ip)) {
                    Optional<String> fromIp = ipResolver.resolveTimezoneKey(ip);
                    if (fromIp.isPresent()) {
                        tzKey = fromIp.get();
                        source = "ip";
                    }
                }
                ActivityHarvestStore.ensureTimezone(
                        c, plugin.timesConfig(), player.getUniqueId(), tzKey, source, ip);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Playtime login record failed: " + ex.getMessage());
        }
    }

    private String resolveTimezoneKey(UUID uuid) {
        if (!plugin.mysql().ready()) {
            return plugin.timesConfig().defaultTimezoneKey();
        }
        try (var c = plugin.mysql().open()) {
            return ActivityHarvestStore.getTimezoneKey(c, plugin.timesConfig(), uuid)
                    .orElse(plugin.timesConfig().defaultTimezoneKey());
        } catch (Exception ex) {
            return plugin.timesConfig().defaultTimezoneKey();
        }
    }

    private void flushAll() {
        for (UUID uuid : segments.keySet().toArray(UUID[]::new)) {
            flushPlayer(uuid, false);
        }
    }

    private void flushPlayer(UUID uuid, boolean ending) {
        Segment segment = segments.get(uuid);
        if (segment == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long elapsedSec = Math.max(0L, (now - segment.flushStartMs()) / 1000L);
        if (!ending) {
            segments.put(
                    uuid,
                    new Segment(segment.joinStartMs(), now, segment.timezoneKey(), segment.offsetMinutes()));
        }
        if (elapsedSec <= 0) {
            return;
        }
        boolean afk = plugin.afkService() != null && plugin.afkService().isAfk(uuid);
        long playSec = elapsedSec;
        long activitySec = elapsedSec;
        if (afk) {
            if (plugin.timesConfig().afkExcludePlaytime()) {
                playSec = 0;
            }
            if (plugin.timesConfig().afkExcludeActivity()) {
                activitySec = 0;
            }
        }
        if (!plugin.mysql().ready()) {
            return;
        }
        long playWrite = playSec;
        long activityWrite = activitySec;
        String tzKey = segment.timezoneKey();
        int offset = segment.offsetMinutes();
        Runnable write = () -> {
            try (var c = plugin.mysql().open()) {
                if (plugin.timesConfig().harvestPlaytime() && playWrite > 0) {
                    PlaytimeStore.addSession(c, plugin.timesConfig(), uuid, playWrite);
                }
                if (plugin.timesConfig().harvestActivity() && activityWrite > 0) {
                    int hour = localHour(offset, Instant.ofEpochMilli(segment.flushStartMs()));
                    ActivityHarvestStore.addPlaySeconds(c, plugin.timesConfig(), uuid, hour, activityWrite);
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Session flush failed: " + ex.getMessage());
            }
        };
        // Never schedule Bukkit tasks while disabled (onDisable flush).
        if (Bukkit.isPrimaryThread() && plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
        } else {
            write.run();
        }
    }

    private static int localHour(int offsetMinutes, Instant instant) {
        long localEpoch = instant.getEpochSecond() + offsetMinutes * 60L;
        return (int) Math.floorMod(localEpoch / 3600L, 24L);
    }
}
