package com.rootrecord.minecraft.rootactivity.service;

import com.rootrecord.minecraft.rootactivity.config.ActivityConfig;
import com.rootrecord.minecraft.rootactivity.data.ActivityStore;
import com.rootrecord.minecraft.rootactivity.data.ActivityStore.PlayerTimezone;
import com.rootrecord.minecraft.rootactivity.data.ActivityStore.TimezoneSource;
import com.rootrecord.minecraft.rootactivity.ip.IpTimezoneResolver;
import com.rootrecord.minecraft.rootactivity.timezone.TimezoneDef;
import com.rootrecord.minecraft.rootactivity.tracker.ActivityTracker;
import com.rootrecord.minecraft.rootactivity.util.PeakHoursFormatter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ActivityService {

    public record TimezoneReport(
            Optional<PlayerTimezone> playerTimezone,
            String clockLabel,
            String personalPeak,
            String serverPeakInYourClock,
            List<RealmTimezoneLine> realmLines,
            int totalPlayers) {}

    public record RealmTimezoneLine(String label, int players, String peakInYourClock) {}

    private final JavaPlugin plugin;
    private final ActivityConfig config;
    private final ActivityStore store;
    private final IpTimezoneResolver ipResolver;
    private final ActivityTracker tracker;

    public ActivityService(
            JavaPlugin plugin,
            ActivityConfig config,
            ActivityStore store,
            IpTimezoneResolver ipResolver,
            ActivityTracker tracker) {
        this.plugin = plugin;
        this.config = config;
        this.store = store;
        this.ipResolver = ipResolver;
        this.tracker = tracker;
    }

    public void onPlayerJoin(Player player) {
        if (!config.enabled() || !config.mysqlConfigured()) {
            return;
        }
        boolean timesOwnsHourly = Bukkit.getPluginManager().isPluginEnabled("Root-Times");
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Optional<PlayerTimezone> tz = store.getTimezone(uuid);
                if (tz.isPresent()) {
                    if (!timesOwnsHourly) {
                        Bukkit.getScheduler()
                                .runTask(plugin, () -> tracker.beginSession(player, tz.get().timezoneKey()));
                    }
                    return;
                }
                String ip = extractIp(player);
                if (!IpTimezoneResolver.isResolvableIp(ip)) {
                    return;
                }
                Optional<String> key = ipResolver.resolveTimezoneKey(ip);
                if (key.isEmpty()) {
                    return;
                }
                if (store.upsertTimezone(uuid, key.get(), TimezoneSource.IP, ip) && !timesOwnsHourly) {
                    Bukkit.getScheduler().runTask(plugin, () -> tracker.beginSession(player, key.get()));
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Timezone join setup failed for " + player.getName() + ": " + ex.getMessage());
            }
        });
    }

    public void onPlayerQuit(Player player) {
        if (Bukkit.getPluginManager().isPluginEnabled("Root-Times")) {
            return;
        }
        tracker.endSession(player.getUniqueId());
    }

    public void reloadTracker() {
        tracker.stop();
        if (Bukkit.getPluginManager().isPluginEnabled("Root-Times")) {
            plugin.getLogger().info("Root-Times present — Root-Activity hourly writers idle.");
            return;
        }
        if (config.enabled() && config.mysqlConfigured()) {
            tracker.start(config.flushIntervalSeconds());
            for (Player player : Bukkit.getOnlinePlayers()) {
                onPlayerJoin(player);
            }
        }
    }

    public TimezoneReport buildReport(UUID uuid) throws Exception {
        Optional<PlayerTimezone> playerTz = store.getTimezone(uuid);
        int viewerOffsetMinutes = 0;
        String clockLabel = "UTC clock";
        if (playerTz.isPresent()) {
            Optional<TimezoneDef> def = TimezoneDef.byKey(playerTz.get().timezoneKey());
            if (def.isPresent()) {
                viewerOffsetMinutes = def.get().offsetMinutes();
                clockLabel = "your clock (" + def.get().label() + ")";
            }
        }

        String personalPeak = "";
        if (playerTz.isPresent()) {
            int[] buckets = store.loadHourlyBuckets(uuid);
            personalPeak = PeakHoursFormatter.format(buckets, config.peakHourCount());
        }

        Map<String, Integer> counts = store.countPlayersByTimezone();
        Map<String, int[]> realm = store.realmBucketsByTimezone();
        Set<String> keys = new HashSet<>();
        keys.addAll(counts.keySet());
        keys.addAll(realm.keySet());

        int[] globalInViewerClock = TimezoneDef.emptyHourBuckets();
        List<RealmTimezoneLine> lines = new ArrayList<>();
        int totalPlayers = 0;

        for (String key : keys) {
            int players = counts.getOrDefault(key, 0);
            totalPlayers += players;
            int[] zoneBuckets = realm.getOrDefault(key, TimezoneDef.emptyHourBuckets());
            int zoneOffset = TimezoneDef.byKey(key).map(TimezoneDef::offsetMinutes).orElse(0);
            int[] inViewerClock =
                    PeakHoursFormatter.shiftToOffset(zoneBuckets, zoneOffset, viewerOffsetMinutes);
            PeakHoursFormatter.addBuckets(globalInViewerClock, inViewerClock);

            if (players <= 0) {
                continue;
            }
            String peak = PeakHoursFormatter.format(inViewerClock, config.peakHourCount());
            String label = TimezoneDef.byKey(key).map(TimezoneDef::label).orElse(key);
            lines.add(new RealmTimezoneLine(label, players, peak));
        }

        lines.sort(Comparator.comparingInt(RealmTimezoneLine::players)
                .reversed()
                .thenComparing(RealmTimezoneLine::label));

        String serverPeak = PeakHoursFormatter.format(globalInViewerClock, config.peakHourCount());
        return new TimezoneReport(playerTz, clockLabel, personalPeak, serverPeak, lines, totalPlayers);
    }

    public String sourceLabel(TimezoneSource source) {
        return source == TimezoneSource.DISCORD ? "Discord" : "IP estimate";
    }

    private static String extractIp(Player player) {
        if (player.getAddress() == null || player.getAddress().getAddress() == null) {
            return "";
        }
        return player.getAddress().getAddress().getHostAddress();
    }
}
