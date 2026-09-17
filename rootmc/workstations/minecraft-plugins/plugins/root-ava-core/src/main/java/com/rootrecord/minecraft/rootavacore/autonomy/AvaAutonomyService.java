package com.rootrecord.minecraft.rootavacore.autonomy;

import com.rootrecord.minecraft.rootavacore.AvaConfig;
import com.rootrecord.minecraft.rootavacore.RootAvaCorePlugin;
import com.rootrecord.minecraft.rootavacore.presence.AvaPresenceService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Home Ava own-claim work. Hired crews run the same cycle via {@link AvaClaimCrew} independently.
 */
public final class AvaAutonomyService {

    public enum Phase {
        IDLE_HIRED,
        GATHER,
        ENSURE_CHESTS,
        DEPOSIT,
        BUILD,
        FARM,
        VACUUM,
        PAUSED
    }

    private final RootAvaCorePlugin plugin;
    private final AvaStorageService storage;
    private final AvaClaimCrew homeCrew;
    private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.GATHER);
    private BukkitTask task;
    private volatile List<String> planPriorities = List.of();
    private volatile String planTheme = "";
    private volatile String lastNote = "boot";

    public AvaAutonomyService(RootAvaCorePlugin plugin, AvaStorageService storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.homeCrew = new AvaClaimCrew(
                plugin,
                storage,
                AvaPresenceService.HOME_CREW,
                plugin.config().powers().avaUuid());
    }

    public AvaStorageService storage() {
        return storage;
    }

    public AvaHomesteadBuilder builder() {
        return homeCrew.builder();
    }

    public AvaClaimCrew homeCrew() {
        return homeCrew;
    }

    public void setPlanPriorities(List<String> priorities, String theme) {
        if (priorities == null || priorities.isEmpty()) {
            planPriorities = List.of();
        } else {
            planPriorities = List.copyOf(priorities);
        }
        planTheme = theme == null ? "" : theme.trim();
        lastNote = "plan ignored · default cycle";
    }

    public List<String> planPriorities() {
        return planPriorities;
    }

    public String planTheme() {
        return planTheme;
    }

    public String statusLine() {
        return mapPhase(homeCrew.phase()).name().toLowerCase(Locale.ROOT)
                + " · " + homeCrew.statusLine()
                + " · cycle gather→chests→deposit→build→farm→vacuum";
    }

    public void start() {
        stop();
        long period = Math.max(60L, plugin.config().autonomy().tickSeconds() * 20L);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void setPaused(boolean on) {
        homeCrew.setPaused(on);
        phase.set(on ? Phase.PAUSED : Phase.GATHER);
        lastNote = on ? "paused" : "resumed";
    }

    public boolean paused() {
        return homeCrew.paused();
    }

    public Phase phase() {
        return phase.get();
    }

    private void tick() {
        AvaConfig.AutonomyConfig cfg = plugin.config().autonomy();
        if (!cfg.enabled() || !plugin.config().enabled()) {
            lastNote = "disabled";
            return;
        }
        if (!plugin.powers().playEnabled()) {
            lastNote = "play_off";
            phase.set(Phase.PAUSED);
            return;
        }
        if (homeCrew.paused()) {
            phase.set(Phase.PAUSED);
            lastNote = "paused";
            return;
        }
        if (cfg.pauseWhenHired() && plugin.hire().isBusy()) {
            phase.set(Phase.IDLE_HIRED);
            lastNote = "hired — own claim waits";
            return;
        }

        homeCrew.tickOwned();
        phase.set(mapPhase(homeCrew.phase()));
        lastNote = homeCrew.lastNote();
    }

    public String acceptGift(Player from, ItemStack stack) {
        return homeCrew.acceptGift(from, stack);
    }

    private static Phase mapPhase(AvaClaimCrew.Phase p) {
        if (p == null) return Phase.GATHER;
        return switch (p) {
            case ENSURE_CHESTS -> Phase.ENSURE_CHESTS;
            case DEPOSIT -> Phase.DEPOSIT;
            case BUILD -> Phase.BUILD;
            case FARM -> Phase.FARM;
            case VACUUM -> Phase.VACUUM;
            case PAUSED -> Phase.PAUSED;
            default -> Phase.GATHER;
        };
    }
}
