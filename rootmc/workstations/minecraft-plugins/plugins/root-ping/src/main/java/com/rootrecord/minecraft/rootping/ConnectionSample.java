package com.rootrecord.minecraft.rootping;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.UUID;

import com.sun.management.OperatingSystemMXBean;

/** One connection + server-usage snapshot. */
public record ConnectionSample(
        String trigger,
        String serverId,
        String hostLabel,
        UUID uuid,
        String username,
        int pingMs,
        String ip,
        int remotePort,
        int protocolVersion,
        String clientBrand,
        String locale,
        int clientViewDistance,
        String virtualHost,
        double tps1m,
        double tps5m,
        double tps15m,
        double msptMs,
        int onlinePlayers,
        int maxPlayers,
        long heapUsedMb,
        long heapMaxMb,
        double heapPct,
        double processCpuPct,
        double diskUsedPct) {

    public static final String TRIGGER_COMMAND = "COMMAND";
    public static final String TRIGGER_INTERVAL = "INTERVAL";

    public static ConnectionSample capture(
            Player player,
            String trigger,
            String serverId,
            String hostLabel,
            String virtualHost,
            Path diskRoot) {
        InetSocketAddress addr = player.getAddress();
        String ip = "";
        int port = 0;
        if (addr != null) {
            if (addr.getAddress() != null) {
                ip = addr.getAddress().getHostAddress();
            }
            port = addr.getPort();
        }

        double[] tps = Bukkit.getTPS();
        double tps1 = tps != null && tps.length > 0 ? tps[0] : 20.0;
        double tps5 = tps != null && tps.length > 1 ? tps[1] : tps1;
        double tps15 = tps != null && tps.length > 2 ? tps[2] : tps5;

        Runtime rt = Runtime.getRuntime();
        long heapMax = rt.maxMemory();
        long heapUsed = rt.totalMemory() - rt.freeMemory();
        long heapUsedMb = Math.max(0L, heapUsed / (1024L * 1024L));
        long heapMaxMb = Math.max(0L, heapMax / (1024L * 1024L));
        double heapPct = heapMax > 0 ? (heapUsed * 100.0) / (double) heapMax : 0.0;

        return new ConnectionSample(
                trigger == null ? TRIGGER_COMMAND : trigger,
                nullToEmpty(serverId),
                nullToEmpty(hostLabel),
                player.getUniqueId(),
                player.getName(),
                player.getPing(),
                ip,
                port,
                player.getProtocolVersion(),
                nullToEmpty(player.getClientBrandName()),
                nullToEmpty(player.getLocale()),
                player.getClientViewDistance(),
                nullToEmpty(virtualHost),
                tps1,
                tps5,
                tps15,
                averageTickMs(),
                Bukkit.getOnlinePlayers().size(),
                Bukkit.getMaxPlayers(),
                heapUsedMb,
                heapMaxMb,
                heapPct,
                readProcessCpuPct(),
                readDiskUsedPct(diskRoot));
    }

    private static double averageTickMs() {
        try {
            return Bukkit.getServer().getAverageTickTime();
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    private static double readProcessCpuPct() {
        try {
            OperatingSystemMXBean os =
                    (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            double cpu = os.getProcessCpuLoad();
            if (cpu < 0) {
                cpu = os.getCpuLoad();
            }
            if (cpu < 0) {
                return 0.0;
            }
            return cpu * 100.0;
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    private static double readDiskUsedPct(Path diskRoot) {
        if (diskRoot == null) {
            return 0.0;
        }
        try {
            long total = diskRoot.toFile().getTotalSpace();
            long usable = diskRoot.toFile().getUsableSpace();
            if (total <= 0) {
                return 0.0;
            }
            return ((double) (total - usable) / (double) total) * 100.0;
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
