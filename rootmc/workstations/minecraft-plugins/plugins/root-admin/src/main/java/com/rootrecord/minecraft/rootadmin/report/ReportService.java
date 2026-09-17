package com.rootrecord.minecraft.rootadmin.report;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.rootadmin.RootAdminPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ReportService {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final RootAdminPlugin plugin;
    private final PlayerActivityLog activityLog;

    public ReportService(RootAdminPlugin plugin, PlayerActivityLog activityLog) {
        this.plugin = plugin;
        this.activityLog = activityLog;
    }

    public String submit(Player reporter, OfflinePlayer target, String reason) {
        String targetName = target.getName() != null ? target.getName() : "unknown";
        UUID targetId = target.getUniqueId();
        int lookbackMin = plugin.lookbackMinutes();
        int lookbackSec = lookbackMin * 60;

        StringBuilder body = new StringBuilder();
        body.append("RootMC player report\n");
        body.append("Generated: ").append(TS.format(Instant.now())).append(" UTC\n");
        body.append("Reporter: ").append(reporter.getName()).append(" (").append(reporter.getUniqueId()).append(")\n");
        body.append("Reported: ").append(targetName).append(" (").append(targetId).append(")\n");
        body.append("Online now: ").append(target.isOnline()).append("\n");
        body.append("Reason: ").append(reason == null || reason.isBlank() ? "(none given)" : reason).append("\n");
        body.append("Lookback: ").append(lookbackMin).append(" minutes (reported player only)\n");
        body.append("\n=== Chat (reported player) ===\n");
        appendActivity(body, activityLog.snapshot(targetId), PlayerActivityLog.Kind.CHAT);
        body.append("\n=== Commands (reported player) ===\n");
        appendActivity(body, activityLog.snapshot(targetId), PlayerActivityLog.Kind.COMMAND);
        body.append("\n=== CoreProtect (reported player) ===\n");
        for (String line : CoreProtectHelper.recentActions(targetName, lookbackSec)) {
            body.append(line).append('\n');
        }

        String reportId = UUID.randomUUID().toString().substring(0, 8);
        if (plugin.saveReportFiles()) {
            saveFile(reportId, body.toString());
        }
        if (plugin.notifyStaff()) {
            notifyStaff(reporter, targetName, reason, reportId);
        }
        postDiscordAsync(reportId, body.toString(), reporter, target, reason);
        return reportId;
    }

    private void postDiscordAsync(
            String reportId,
            String fullBody,
            Player reporter,
            OfflinePlayer target,
            String reason) {
        if (!plugin.discordReportsEnabled() || !plugin.reportCloud().hasCredentials()) {
            return;
        }
        String targetName = target.getName() != null ? target.getName() : "unknown";
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.reportCloud().submitReport(
                        reportId,
                        reporter.getUniqueId().toString(),
                        reporter.getName(),
                        target.getUniqueId().toString(),
                        targetName,
                        target.isOnline(),
                        reason == null ? "" : reason,
                        fullBody);
            } catch (Exception ex) {
                plugin.getLogger().warning("Discord report post failed: " + ex.getMessage());
            }
        });
    }

    private static void appendActivity(StringBuilder body, List<PlayerActivityLog.Entry> entries, PlayerActivityLog.Kind kind) {
        boolean any = false;
        for (PlayerActivityLog.Entry entry : entries) {
            if (entry.kind() != kind) continue;
            any = true;
            body.append('[').append(TS.format(entry.at())).append(" UTC] ").append(entry.text()).append('\n');
        }
        if (!any) body.append("(none in window)\n");
    }

    private void saveFile(String reportId, String content) {
        File dir = new File(RootRecordFolders.dir(plugin), "reports");
        dir.mkdirs();
        File file = new File(dir, "report-" + reportId + ".txt");
        try {
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save report file: " + ex.getMessage());
        }
    }

    private void notifyStaff(Player reporter, String targetName, String reason, String reportId) {
        String summary = plugin.colorize(plugin.msg("report-notify")
                .replace("{reporter}", reporter.getName())
                .replace("{player}", targetName)
                .replace("{reason}", reason == null || reason.isBlank() ? "(none)" : reason)
                .replace("{id}", reportId));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("rootadmin.reports.notify")) {
                online.sendMessage(summary);
            }
        }
        plugin.getLogger().info("Report " + reportId + ": " + reporter.getName() + " reported " + targetName
                + " — " + (reason == null ? "" : reason));
    }
}
