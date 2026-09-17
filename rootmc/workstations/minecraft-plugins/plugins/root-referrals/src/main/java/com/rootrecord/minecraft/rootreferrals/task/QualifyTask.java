package com.rootrecord.minecraft.rootreferrals.task;

import com.rootrecord.minecraft.rootreferrals.service.QualifyService;
import com.rootrecord.minecraft.rootreferrals.store.ReferralStore;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Async scan of pending referral edges every 5 seconds. */
public final class QualifyTask {

    private final JavaPlugin plugin;
    private final QualifyService qualifyService;
    private final ReferralStore store;
    private int taskId = -1;

    public QualifyTask(JavaPlugin plugin, QualifyService qualifyService, ReferralStore store) {
        this.plugin = plugin;
        this.qualifyService = qualifyService;
        this.store = store;
    }

    public void start() {
        stop();
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!store.ready()) {
                return;
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, qualifyService::processPending);
        }, 100L, 100L).getTaskId();
    }

    public void stop() {
        if (taskId >= 0) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }
}
