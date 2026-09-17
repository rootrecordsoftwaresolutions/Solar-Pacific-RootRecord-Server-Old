package com.rootrecord.minecraft.rootadmin.probe;

import com.rootrecord.minecraft.rootadmin.RootAdminPlugin;
import com.rootrecord.minecraft.rootadmin.cloud.ReportCloudClient;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class AdminCommandProbeService {

    private static final class State {
        int attempts;
        long lastAttemptMs;
        final List<String> recent = new ArrayList<>();
    }

    private final RootAdminPlugin plugin;
    private final ReportCloudClient cloud;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    private volatile boolean enabled;
    private volatile int threshold;
    private volatile int windowMinutes;
    private volatile boolean notifyStaff;
    private volatile boolean discordPost;

    public AdminCommandProbeService(RootAdminPlugin plugin, ReportCloudClient cloud) {
        this.plugin = plugin;
        this.cloud = cloud;
        reload();
    }

    public void reload() {
        enabled = plugin.probeEnabled();
        threshold = plugin.probeThreshold();
        windowMinutes = plugin.probeWindowMinutes();
        notifyStaff = plugin.probeNotifyStaff();
        discordPost = plugin.probeDiscordPost();
    }

    public void onQuit(UUID uuid) {
        states.remove(uuid);
    }

    public void recordAttempt(Player player, String commandLine, AdminCommandProbeCatalog.ProbeMatch match) {
        if (!enabled || player == null || match == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        State state = states.computeIfAbsent(uuid, ignored -> new State());
        synchronized (state) {
            if (state.lastAttemptMs > 0
                    && now - state.lastAttemptMs > windowMinutes * 60_000L) {
                state.attempts = 0;
                state.recent.clear();
            }
            state.lastAttemptMs = now;
            state.attempts++;
            state.recent.add(match.label() + " (" + match.reason() + ")");
            while (state.recent.size() > 10) {
                state.recent.remove(0);
            }
            if (state.attempts < threshold) {
                return;
            }
            int count = state.attempts;
            List<String> commands = List.copyOf(state.recent);
            state.attempts = 0;
            state.recent.clear();
            flagPlayer(player, commandLine, match, count, commands);
        }
    }

    private void flagPlayer(
            Player player,
            String lastCommand,
            AdminCommandProbeCatalog.ProbeMatch match,
            int attemptCount,
            List<String> recentCommands) {
        plugin.getLogger().warning("Admin command probe flag: " + player.getName()
                + " — " + attemptCount + " unauthorized attempts (last: " + lastCommand + ")");

        if (notifyStaff) {
            String summary = plugin.colorize(plugin.msg("admin-probe-notify")
                    .replace("{player}", player.getName())
                    .replace("{count}", String.valueOf(attemptCount))
                    .replace("{command}", match.label())
                    .replace("{reason}", match.reason()));
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.hasPermission("rootadmin.reports.notify")) {
                    online.sendMessage(summary);
                }
            }
        }

        if (!discordPost || !cloud.hasCredentials()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                cloud.submitAdminProbeFlag(
                        player.getUniqueId().toString(),
                        player.getName(),
                        player.getWorld().getName(),
                        attemptCount,
                        lastCommand,
                        match.label(),
                        match.reason(),
                        recentCommands,
                        Instant.now().toString());
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Admin probe Discord flag failed for " + player.getName(), ex);
            }
        });
    }
}
