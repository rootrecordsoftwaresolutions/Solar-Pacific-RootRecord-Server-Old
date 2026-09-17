package com.rootrecord.minecraft.roottimes.command;

import com.rootrecord.minecraft.common.ChatUi;
import com.rootrecord.minecraft.common.McDayClock;
import com.rootrecord.minecraft.roottimes.RootTimesPlugin;
import com.rootrecord.minecraft.roottimes.mysql.ActivityHarvestStore;
import com.rootrecord.minecraft.roottimes.mysql.PlaytimeStore;
import com.rootrecord.minecraft.roottimes.timezone.PlayerTimezone;
import com.rootrecord.minecraft.roottimes.track.SessionTracker;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Player-facing clock / uptime / playtime snapshot. */
public final class TimeCommand implements CommandExecutor {

    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("HH:mm z", Locale.US);

    private final RootTimesPlugin plugin;

    public TimeCommand(RootTimesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("roottimes.time")) {
            sender.sendMessage("§cNo permission for /time.");
            return true;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Snapshot snap = collect(sender);
            Bukkit.getScheduler().runTask(plugin, () -> send(sender, snap));
        });
        return true;
    }

    private Snapshot collect(CommandSender sender) {
        long untilMs = McDayClock.enabled() ? McDayClock.millisUntilNextMidnight() : 0L;
        String until = untilMs > 0 ? PlaytimeStore.formatCountdown(untilMs) : "—";

        long upMs = plugin.uptimeTracker() != null ? plugin.uptimeTracker().currentUptimeMs() : 0L;
        long longestUpMs = plugin.uptimeTracker() != null ? plugin.uptimeTracker().longestUptimeMs() : upMs;

        String sessionLine = "—";
        if (plugin.sessionTracker() != null) {
            Optional<SessionTracker.NamedSession> longest = plugin.sessionTracker().longestSessionOverall();
            if (longest.isPresent()) {
                SessionTracker.NamedSession s = longest.get();
                sessionLine = s.name() + " · " + PlaytimeStore.formatCountdown(s.millis());
            }
        }

        String topLine = "—";
        if (plugin.mysql().ready()) {
            try (var c = plugin.mysql().open()) {
                List<PlaytimeStore.PlaytimeTopRow> top = PlaytimeStore.topPlaytime(c, plugin.timesConfig(), 3);
                if (!top.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < top.size(); i++) {
                        if (i > 0) {
                            sb.append(" | ");
                        }
                        PlaytimeStore.PlaytimeTopRow row = top.get(i);
                        sb.append(i + 1)
                                .append(". ")
                                .append(row.username() == null ? "?" : row.username())
                                .append(' ')
                                .append(PlaytimeStore.formatDuration(row.seconds()));
                    }
                    topLine = sb.toString();
                }
            } catch (Exception ex) {
                topLine = "unavailable";
            }
        }

        String serverClock = CLOCK.format(McDayClock.now());
        String localClock = sender instanceof Player player ? formatLocal(player) : serverClock;

        return new Snapshot(
                until,
                until,
                PlaytimeStore.formatCountdown(upMs),
                PlaytimeStore.formatCountdown(longestUpMs),
                sessionLine,
                topLine,
                serverClock,
                localClock);
    }

    private String formatLocal(Player player) {
        String tzKey = plugin.timesConfig().defaultTimezoneKey();
        if (plugin.mysql().ready()) {
            try (var c = plugin.mysql().open()) {
                tzKey = ActivityHarvestStore.getTimezoneKey(c, plugin.timesConfig(), player.getUniqueId())
                        .orElse(tzKey);
            } catch (Exception ignored) {
                // keep default
            }
        }
        PlayerTimezone tz = PlayerTimezone.byKey(tzKey).orElse(PlayerTimezone.utc());
        ZonedDateTime local = Instant.now().atZone(ZoneOffset.ofTotalSeconds(tz.offsetMinutes() * 60));
        return CLOCK.format(local);
    }

    private void send(CommandSender sender, Snapshot snap) {
        ChatUi.banner(sender, "Time");
        boolean towny = Bukkit.getPluginManager().isPluginEnabled("Towny");
        if (towny) {
            ChatUi.row(sender, "New day", snap.untilMc() + " (MC / Towny synced)");
        } else {
            ChatUi.row(sender, "New ingame day", snap.untilMc());
        }
        ChatUi.row(sender, "Uptime", snap.uptime() + " | longest " + snap.longestUptime());
        ChatUi.row(sender, "Longest session", snap.longestSession());
        ChatUi.row(sender, "Top playtime", snap.topPlaytime());
        ChatUi.row(sender, "Clock", snap.serverClock() + " | local " + snap.localClock());
    }

    private record Snapshot(
            String untilMc,
            String untilTowny,
            String uptime,
            String longestUptime,
            String longestSession,
            String topPlaytime,
            String serverClock,
            String localClock) {}
}
