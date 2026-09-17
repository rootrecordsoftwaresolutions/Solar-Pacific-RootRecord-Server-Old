package com.rootrecord.minecraft.rootactivity.tracker;

import com.rootrecord.minecraft.rootactivity.data.ActivityStore;
import com.rootrecord.minecraft.rootactivity.timezone.TimezoneDef;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ActivityTracker {

    private record Session(UUID uuid, String timezoneKey, int offsetMinutes, Instant segmentStart) {}

    private final JavaPlugin plugin;
    private final ActivityStore store;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private int taskId = -1;

    public ActivityTracker(JavaPlugin plugin, ActivityStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    public void start(int flushIntervalSeconds) {
        stop();
        long periodTicks = Math.max(20L, flushIntervalSeconds * 20L);
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> flushAll(), periodTicks, periodTicks).getTaskId();
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        flushAll(true);
        sessions.clear();
    }

    public void beginSession(Player player, String timezoneKey) {
        Optional<TimezoneDef> def = TimezoneDef.byKey(timezoneKey);
        if (def.isEmpty()) {
            sessions.remove(player.getUniqueId());
            return;
        }
        flushPlayer(player.getUniqueId());
        sessions.put(
                player.getUniqueId(),
                new Session(player.getUniqueId(), def.get().key(), def.get().offsetMinutes(), Instant.now()));
    }

    public void endSession(UUID uuid) {
        flushPlayer(uuid);
        sessions.remove(uuid);
    }

    public void refreshTimezone(UUID uuid, String timezoneKey) {
        Session current = sessions.get(uuid);
        if (current == null) {
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            beginSession(player, timezoneKey);
        }
    }

    private void flushAll() {
        flushAll(false);
    }

    private void flushAll(boolean synchronous) {
        for (UUID uuid : sessions.keySet().toArray(UUID[]::new)) {
            flushPlayer(uuid, synchronous);
        }
    }

    private void flushPlayer(UUID uuid) {
        flushPlayer(uuid, false);
    }

    private void flushPlayer(UUID uuid, boolean synchronous) {
        Session session = sessions.get(uuid);
        if (session == null) {
            return;
        }
        Instant now = Instant.now();
        long seconds = Math.max(0L, now.getEpochSecond() - session.segmentStart.getEpochSecond());
        if (seconds <= 0) {
            return;
        }
        int localHour = localHour(session.offsetMinutes(), session.segmentStart);
        Runnable write = () -> {
            try {
                store.addPlaySeconds(uuid, localHour, seconds);
            } catch (Exception ex) {
                plugin.getLogger().warning("Activity flush failed for " + uuid + ": " + ex.getMessage());
            }
        };
        if (synchronous) {
            write.run();
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
        }
        sessions.put(uuid, new Session(uuid, session.timezoneKey(), session.offsetMinutes(), now));
    }

    static int localHour(int offsetMinutes, Instant instant) {
        ZoneOffset offset = ZoneOffset.ofTotalSeconds(offsetMinutes * 60);
        return instant.atOffset(offset).getHour();
    }
}
