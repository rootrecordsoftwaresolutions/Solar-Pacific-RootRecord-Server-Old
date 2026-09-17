package com.rootrecord.minecraft.rootactivity.command;

import com.rootrecord.minecraft.rootactivity.RootActivityPlugin;
import com.rootrecord.minecraft.rootactivity.service.ActivityService;
import com.rootrecord.minecraft.rootactivity.service.ActivityService.RealmTimezoneLine;
import com.rootrecord.minecraft.rootactivity.timezone.TimezoneDef;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public final class TimezoneCommand implements CommandExecutor {

    private final RootActivityPlugin plugin;

    public TimezoneCommand(RootActivityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.msg("players-only"));
            return true;
        }
        if (!player.hasPermission("rootactivity.use")) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (!plugin.activityConfig().enabled()) {
            player.sendMessage(plugin.msg("disabled"));
            return true;
        }
        if (!plugin.activityConfig().mysqlConfigured()) {
            player.sendMessage(plugin.colorize("&cMySQL is not configured for Root-Activity."));
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin.host(), () -> {
            try {
                ActivityService.TimezoneReport report = plugin.service().buildReport(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin.host(), () -> sendReport(player, report));
            } catch (Exception ex) {
                plugin.getLogger().warning("/timezone failed for " + player.getName() + ": " + ex.getMessage());
                Bukkit.getScheduler().runTask(plugin.host(), () ->
                        player.sendMessage(plugin.colorize("&cCould not load activity data. Try again shortly.")));
            }
        });
        return true;
    }

    private void sendReport(Player player, ActivityService.TimezoneReport report) {
        if (report.playerTimezone().isPresent()) {
            var tz = report.playerTimezone().get();
            String label = TimezoneDef.byKey(tz.timezoneKey()).map(TimezoneDef::label).orElse(tz.timezoneKey());
            player.sendMessage(plugin.msg("your-timezone", Map.of(
                    "label", label,
                    "source", plugin.service().sourceLabel(tz.source()))));
            if (report.personalPeak().isBlank()) {
                player.sendMessage(plugin.msg("your-peak-none"));
            } else {
                player.sendMessage(plugin.msg("your-peak", Map.of("peak", report.personalPeak())));
            }
        } else {
            player.sendMessage(plugin.msg("your-timezone-unset"));
            player.sendMessage(plugin.msg("clock-fallback"));
        }

        if (report.serverPeakInYourClock().isBlank()) {
            player.sendMessage(plugin.msg("server-peak-none"));
        } else {
            player.sendMessage(plugin.msg("server-peak", Map.of(
                    "peak", report.serverPeakInYourClock(),
                    "clock", report.clockLabel())));
        }

        player.sendMessage(plugin.msg("zones-header", Map.of(
                "total", String.valueOf(report.totalPlayers()),
                "clock", report.clockLabel())));
        if (report.realmLines().isEmpty()) {
            player.sendMessage(plugin.msg("realm-empty"));
        } else {
            for (RealmTimezoneLine line : report.realmLines()) {
                if (line.peakInYourClock().isBlank()) {
                    player.sendMessage(plugin.msg("zone-line-no-peak", Map.of(
                            "label", line.label(),
                            "players", String.valueOf(line.players()))));
                } else {
                    player.sendMessage(plugin.msg("zone-line", Map.of(
                            "label", line.label(),
                            "players", String.valueOf(line.players()),
                            "peak", line.peakInYourClock())));
                }
            }
        }
        player.sendMessage(plugin.msg("discord-hint"));
    }
}
