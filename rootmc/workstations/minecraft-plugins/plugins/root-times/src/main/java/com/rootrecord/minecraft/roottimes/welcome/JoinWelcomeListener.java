package com.rootrecord.minecraft.roottimes.welcome;

import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.common.FancyHeadlines;
import com.rootrecord.minecraft.common.McDayClock;
import com.rootrecord.minecraft.common.RootMcServerDisplay;
import com.rootrecord.minecraft.roottimes.RootTimesPlugin;
import com.rootrecord.minecraft.roottimes.clock.ClockService;
import com.rootrecord.minecraft.roottimes.mysql.ActivityHarvestStore;
import com.rootrecord.minecraft.roottimes.mysql.PlaytimeStore;
import com.rootrecord.minecraft.roottimes.timezone.PlayerTimezone;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

@SuppressWarnings("deprecation")
public final class JoinWelcomeListener implements Listener {

    private final RootTimesPlugin plugin;

    public JoinWelcomeListener(RootTimesPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.timesConfig().welcomeEnabled()) {
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void stop() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.timesConfig().welcomeEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        int delay = plugin.timesConfig().welcomeDelayTicks();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> sendWelcome(player));
        }, delay);
    }

    private void sendWelcome(Player player) {
        UUID uuid = player.getUniqueId();
        long totalSeconds = 0L;
        long voteCount = -1L;
        String tzKey = plugin.timesConfig().defaultTimezoneKey();
        if (plugin.mysql().ready()) {
            try (var c = plugin.mysql().open()) {
                totalSeconds = PlaytimeStore.totalSeconds(c, plugin.timesConfig(), uuid).orElse(0L);
                tzKey = ActivityHarvestStore.getTimezoneKey(c, plugin.timesConfig(), uuid).orElse(tzKey);
                voteCount = countVotes(c, uuid);
            } catch (Exception ex) {
                plugin.getLogger().warning("Welcome playtime lookup failed: " + ex.getMessage());
            }
        }
        PlayerTimezone tz = PlayerTimezone.byKey(tzKey).orElse(PlayerTimezone.utc());
        ZonedDateTime local = Instant.now().atZone(ZoneOffset.ofTotalSeconds(tz.offsetMinutes() * 60));
        String clock = format12h(local);
        String playtime = PlaytimeStore.formatDuration(totalSeconds);
        long scheduleDay = McDayClock.enabled() ? McDayClock.currentDayId() : 0L;

        String tzLabel = tz.label();
        String playtimeFmt = playtime;
        String clockFmt = clock;
        long scheduleDayFinal = scheduleDay;
        long votesFinal = voteCount;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            World world = plugin.worldTimeSync() != null
                    ? plugin.worldTimeSync().dayWorld()
                    : null;
            long fullTime = world != null ? world.getFullTime() : 0L;
            long todTicks = Math.floorMod(fullTime, McDayClock.TICKS_PER_DAY);
            long worldDay = Math.floorDiv(fullTime, McDayClock.TICKS_PER_DAY);
            String phase = ClockService.phaseLabel(todTicks);
            // Day 0 is valid after a new-world re-anchor — do not fall back to world fullTime.
            long displayDay = McDayClock.enabled() ? scheduleDayFinal : worldDay;

            FancyHeadlines.sendBanner(player, RootMcServerDisplay.serverName(plugin));
            ChatUi.entry(player, "Playtime", playtimeFmt);
            if (votesFinal >= 0L) {
                ChatUi.entry(player, "Votes", votesFinal + (votesFinal == 1L ? " vote" : " votes"));
            }
            ChatUi.entry(player, "Local", clockFmt + " " + tzLabel);
            ChatUi.entry(player, "Day", "#" + displayDay + " · " + phase);
            ChatUi.tip(player, "/playtime  ·  /cmds  ·  /vote  ·  /try");
        });
    }

    private long countVotes(java.sql.Connection c, UUID uuid) {
        if (uuid == null) {
            return 0L;
        }
        String table = plugin.timesConfig().tablePrefix() + "rewards_votes";
        try (var ps = c.prepareStatement(
                "SELECT COUNT(*) FROM `"
                        + table
                        + "` WHERE LOWER(REPLACE(uuid,'-','')) = LOWER(REPLACE(?, '-', ''))")) {
            ps.setString(1, uuid.toString());
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (Exception ex) {
            plugin.getLogger().fine("Welcome vote count skipped: " + ex.getMessage());
            return -1L;
        }
    }

    private static String format12h(ZonedDateTime local) {
        int hour24 = local.getHour();
        int minute = local.getMinute();
        String ampm = hour24 >= 12 ? "PM" : "AM";
        int hour12 = hour24 % 12;
        if (hour12 == 0) {
            hour12 = 12;
        }
        return hour12 + ":" + String.format("%02d", minute) + " " + ampm;
    }
}
