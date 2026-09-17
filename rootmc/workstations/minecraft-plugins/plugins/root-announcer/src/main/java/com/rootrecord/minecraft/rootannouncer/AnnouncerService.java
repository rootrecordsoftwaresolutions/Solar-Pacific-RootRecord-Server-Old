package com.rootrecord.minecraft.rootannouncer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/** Rotates and broadcasts configured lines to online players. */
public final class AnnouncerService {

    private final RootAnnouncerPlugin plugin;
    private final AtomicInteger sequence = new AtomicInteger();

    public AnnouncerService(RootAnnouncerPlugin plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        AnnouncerConfig config = plugin.config();
        List<String> lines = plugin.effectiveLines();
        if (!config.enabled() || lines.isEmpty()) {
            return;
        }
        if (Bukkit.getOnlinePlayers().size() < config.requireMinPlayers()) {
            return;
        }
        broadcastLine(pickLine(lines, config));
    }

    public boolean broadcastNow(int index) {
        AnnouncerConfig config = plugin.config();
        List<String> lines = plugin.effectiveLines();
        if (lines.isEmpty()) {
            return false;
        }
        int idx = index;
        if (idx < 0 || idx >= lines.size()) {
            idx = pickIndex(lines, config);
        }
        broadcastLine(lines.get(idx));
        return true;
    }

    private void broadcastLine(String line) {
        String formatted = plugin.formatLine(line);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.toggles().isEnabled(player.getUniqueId())) {
                player.sendMessage(formatted);
            }
        }
    }

    private String pickLine(List<String> lines, AnnouncerConfig config) {
        return lines.get(pickIndex(lines, config));
    }

    private int pickIndex(List<String> lines, AnnouncerConfig config) {
        if (config.randomOrder()) {
            return ThreadLocalRandom.current().nextInt(lines.size());
        }
        int idx = Math.floorMod(sequence.getAndIncrement(), lines.size());
        return idx;
    }
}
