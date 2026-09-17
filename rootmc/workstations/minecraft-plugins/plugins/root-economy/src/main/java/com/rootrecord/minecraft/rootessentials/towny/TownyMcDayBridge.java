package com.rootrecord.minecraft.rootessentials.towny;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Runs Towny plot tax / upkeep once per RootMC Minecraft day.
 * Towny 0.100+ exposes {@code NewDayScheduler.newDay()}; keep {@code day_interval}
 * aligned with Root-Times {@code minecraft-day.length-minutes} (e.g. {@code 20m}).
 */
public final class TownyMcDayBridge {

    private static final String NEW_DAY_SCHEDULER = "com.palmergames.bukkit.towny.tasks.NewDayScheduler";
    private static final String TIMER_HANDLER = "com.palmergames.bukkit.towny.TownyTimerHandler";

    private TownyMcDayBridge() {}

    public static void runNewDay(Plugin plugin, Runnable onComplete) {
        if (TownyReflection.townyPlugin() == null) {
            onComplete.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean ok = invokeNewDay(plugin);
            if (!ok) {
                plugin.getLogger().warning(
                        "Towny new-day could not be invoked — set day_interval to match MC day length"
                                + " (e.g. 20m) as backup, or check Towny version.");
            }
            onComplete.run();
        });
    }

    /**
     * Prefer RootMC-driven new days: turn off Towny's own daily timer so taxes
     * don't fire on a separate {@code day_interval} clock.
     */
    public static void disableTownyDailyTimer(Plugin plugin) {
        if (TownyReflection.townyPlugin() == null) {
            return;
        }
        try {
            Class<?> type = TownyReflection.loadClass(TIMER_HANDLER);
            Method toggle = type.getMethod("toggleDailyTimer", boolean.class);
            toggle.invoke(null, false);
            plugin.getLogger().info(
                    "Towny daily timer disabled — new day driven by RootMC Minecraft-day rollover.");
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Could not disable Towny daily timer: " + ex.getMessage());
        }
    }

    private static boolean invokeNewDay(Plugin plugin) {
        try {
            Class<?> sched = TownyReflection.loadClass(NEW_DAY_SCHEDULER);
            Method isRunning = sched.getMethod("isNewDayRunning");
            if (Boolean.TRUE.equals(isRunning.invoke(null))) {
                plugin.getLogger().info("Towny new-day already running — skip duplicate invoke.");
                return true;
            }
            Method newDay = sched.getMethod("newDay");
            newDay.invoke(null);
            plugin.getLogger().info("Towny new-day invoked via NewDayScheduler#newDay");
            return true;
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().log(Level.WARNING, "Towny NewDayScheduler.newDay failed: " + ex.getMessage(), ex);
            return false;
        }
    }
}
