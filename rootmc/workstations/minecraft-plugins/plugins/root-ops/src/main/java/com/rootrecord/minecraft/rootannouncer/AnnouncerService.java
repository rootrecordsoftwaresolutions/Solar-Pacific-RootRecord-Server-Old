package com.rootrecord.minecraft.rootannouncer;

import com.rootrecord.minecraft.common.ChatUi;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/** Rotates and broadcasts configured lines to online players. */
public final class AnnouncerService {

    /** Prefix from PublicReachoutService hourly tax/treasury line. */
    public static final String TAX_PREFIX = "::tax::";

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
        String plain = stripLegacy(formatted);
        boolean tax = plain.startsWith(TAX_PREFIX);
        if (tax) {
            plain = plain.substring(TAX_PREFIX.length()).trim();
        }
        // Strip legacy mute tips if still present in cached/cloud lines.
        int tipAt = plain.lastIndexOf(" · /announcements");
        if (tipAt < 0) {
            tipAt = plain.lastIndexOf(" · /announcer");
        }
        if (tipAt > 0) {
            plain = plain.substring(0, tipAt).trim();
        }
        String tag = tax ? "Tax" : "News";
        String body = plain.isBlank() ? "—" : plain;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.toggles().isEnabled(player.getUniqueId())) {
                ChatUi.entry(player, tag, body);
            }
        }
    }

    private static String stripLegacy(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace('\u00a7', '&').replaceAll("(?i)&[0-9a-fk-or]", "").trim();
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
