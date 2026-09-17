package com.rootrecord.minecraft.roothelp.commandtest;

import com.rootrecord.minecraft.roothelp.RootHelpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

public final class CommandTestReminderTask {

    private final RootHelpPlugin plugin;
    private final CommandTestService service;
    private BukkitTask task;

    public CommandTestReminderTask(RootHelpPlugin plugin, CommandTestService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public void start() {
        stop();
        if (!service.enabled()) {
            return;
        }
        long ticks = service.config().reminderIntervalSeconds() * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (var player : Bukkit.getOnlinePlayers()) {
                service.sendReminder(player);
            }
        }, ticks, ticks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
