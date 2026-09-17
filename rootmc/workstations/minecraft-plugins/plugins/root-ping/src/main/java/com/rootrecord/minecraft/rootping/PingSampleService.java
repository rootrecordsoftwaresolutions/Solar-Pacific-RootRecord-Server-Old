package com.rootrecord.minecraft.rootping;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Capture + persist samples; schedule per-player interval tasks. */
public final class PingSampleService {

    private final RootPingPlugin plugin;
    private final Map<UUID, BukkitTask> intervalTasks = new ConcurrentHashMap<>();
    private final Map<UUID, String> virtualHosts = new ConcurrentHashMap<>();
    private Path diskRoot;

    public PingSampleService(RootPingPlugin plugin) {
        this.plugin = plugin;
        refreshDiskRoot();
    }

    public void refreshDiskRoot() {
        Path root = plugin.getServer().getWorldContainer().toPath().getParent();
        this.diskRoot = root != null ? root : plugin.getServer().getWorldContainer().toPath();
    }

    public void rememberVirtualHost(UUID uuid, String hostname) {
        if (uuid == null) {
            return;
        }
        if (hostname == null || hostname.isBlank()) {
            virtualHosts.remove(uuid);
            return;
        }
        virtualHosts.put(uuid, hostname.trim());
    }

    public void forget(UUID uuid) {
        if (uuid == null) {
            return;
        }
        virtualHosts.remove(uuid);
        cancelInterval(uuid);
    }

    public void startInterval(Player player) {
        if (player == null || !plugin.config().enabled()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        cancelInterval(uuid);
        long periodTicks = plugin.config().intervalMinutes() * 60L * 20L;
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(
                plugin,
                () -> {
                    Player online = Bukkit.getPlayer(uuid);
                    if (online == null || !online.isOnline()) {
                        cancelInterval(uuid);
                        return;
                    }
                    sampleAndStore(online, ConnectionSample.TRIGGER_INTERVAL);
                },
                periodTicks,
                periodTicks);
        intervalTasks.put(uuid, task);
    }

    public void cancelInterval(UUID uuid) {
        BukkitTask task = intervalTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    public void cancelAll() {
        for (UUID uuid : intervalTasks.keySet()) {
            cancelInterval(uuid);
        }
        intervalTasks.clear();
    }

    public ConnectionSample sampleAndStore(Player player, String trigger) {
        ConnectionSample sample;
        try {
            sample = ConnectionSample.capture(
                    player,
                    trigger,
                    plugin.resolveServerId(),
                    plugin.resolveHostLabel(),
                    virtualHosts.getOrDefault(player.getUniqueId(), ""),
                    diskRoot);
        } catch (Throwable t) {
            plugin.getLogger().warning("Ping sample capture failed: " + t.getMessage());
            return null;
        }
        final ConnectionSample toStore = sample;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.store().insert(toStore));
        return sample;
    }

    public void sendRichPing(Player player, ConnectionSample sample) {
        PingConfig cfg = plugin.config();
        String pingLine = cfg.pingLine()
                .replace("{ping}", String.valueOf(sample.pingMs()));
        String serverLine = cfg.serverLine()
                .replace("{tps}", format1(sample.tps1m()))
                .replace("{mspt}", format1(sample.msptMs()))
                .replace("{online}", String.valueOf(sample.onlinePlayers()))
                .replace("{max}", String.valueOf(sample.maxPlayers()))
                .replace("{heap}", String.valueOf(Math.round(sample.heapPct())));
        player.sendMessage(colorize(cfg.prefix() + pingLine));
        player.sendMessage(colorize(cfg.prefix() + serverLine));
    }

    private static String format1(double v) {
        return String.format(java.util.Locale.US, "%.1f", v);
    }

    @SuppressWarnings("deprecation")
    private static String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }
}
