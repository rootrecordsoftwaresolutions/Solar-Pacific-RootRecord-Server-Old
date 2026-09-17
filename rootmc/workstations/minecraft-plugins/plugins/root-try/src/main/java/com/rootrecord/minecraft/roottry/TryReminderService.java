package com.rootrecord.minecraft.roottry;

import com.rootrecord.minecraft.common.ChatUi;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** Announcer-style whisper of one incomplete try; only completion clears that try. */
public final class TryReminderService {

    private final RootTryPlugin plugin;
    private BukkitTask task;

    public TryReminderService(RootTryPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        TryService service = plugin.service();
        if (service == null || !service.config().reminderEnabled() || !service.config().enabled()) {
            return;
        }
        long ticks = Math.max(20L * 30L, service.config().reminderIntervalSeconds() * 20L);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, ticks, ticks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        TryService service = plugin.service();
        if (service == null || !service.config().enabled()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("roottry.use")) {
                continue;
            }
            TryEntry next = service.nextIncomplete(player);
            if (next == null) {
                continue;
            }
            String tag = shortTag(next.id());
            ChatUi.entryStatus(player, tag, next.title(), false, service.incompleteStatus(next));
            if (next.hint() != null && !next.hint().isBlank()) {
                ChatUi.tip(player, next.hint());
            }
        }
    }

    private static String shortTag(String id) {
        if (id == null || id.isBlank()) {
            return "Try";
        }
        int dot = id.lastIndexOf('.');
        String raw = dot >= 0 && dot + 1 < id.length() ? id.substring(dot + 1) : id;
        if (raw.isEmpty()) {
            return "Try";
        }
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }
}
