package com.rootrecord.minecraft.rootchamber;

import com.rootrecord.minecraft.common.RootMcTreasuryResolver;
import com.rootrecord.minecraft.common.RootMcTreasuryService;
import com.rootrecord.minecraft.rootspawn.LavaSpot;
import com.rootrecord.minecraft.rootspawn.RootSpawnPlugin;
import com.rootrecord.minecraft.rootspawn.SpawnConfig;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 5-minute chamber survival minigame  -  timer, lava, cooldown, treasury payout. */
final class ChamberMinigameManager {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final RootChamberPlugin plugin;
    private final RootSpawnPlugin spawn;
    private final ChamberCooldownStore cooldowns;
    private final ChamberRunRecorder recorder;
    private final Map<UUID, ActiveRun> active = new ConcurrentHashMap<>();

    ChamberMinigameManager(RootChamberPlugin plugin, RootSpawnPlugin spawn, ChamberCooldownStore cooldowns) {
        this.plugin = plugin;
        this.spawn = spawn;
        this.cooldowns = cooldowns;
        this.recorder = new ChamberRunRecorder(plugin);
    }

    boolean isRunning(UUID playerId) {
        return active.containsKey(playerId);
    }

    boolean canStartAttempt(Player player) {
        return bypassCooldown(player) || !cooldowns.isOnCooldown(
                player.getUniqueId(), spawn.config().chamberCooldownMs());
    }

    void onEnterWell(Player player) {
        if (!spawn.isInsideWellFootprint(player.getLocation())) {
            return;
        }
        spawn.chamberMsg(player, spawn.config().wellWarning());
    }

    void onEnterChamber(Player player) {
        if (!spawn.isInsideChamber(player.getLocation())) {
            return;
        }
        if (isRunning(player.getUniqueId())) {
            return;
        }
        if (!canStartAttempt(player)) {
            long remain = cooldowns.cooldownRemainingMs(player.getUniqueId());
            String wait = spawn.config().chamberCooldownDeny()
                    .replace("{time}", formatDuration(remain));
            spawn.chamberMsg(player, wait);
            return;
        }
        startRun(player);
    }

    void onLeaveChamber(Player player) {
        ActiveRun run = active.get(player.getUniqueId());
        if (run == null) {
            return;
        }
        finishRun(player.getUniqueId(), ChamberRunRecorder.Outcome.DISQUALIFIED, false);
        if (!bypassCooldown(player)) {
            cooldowns.setCooldown(player.getUniqueId(), spawn.config().chamberCooldownMs());
        }
        spawn.chamberMsg(player, spawn.config().chamberDisqualified());
    }

    void onPlayerQuit(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!isRunning(playerId)) {
            return;
        }
        finishRun(playerId, ChamberRunRecorder.Outcome.DISCONNECT, false);
        if (!bypassCooldown(player)) {
            cooldowns.setCooldown(playerId, spawn.config().chamberCooldownMs());
        }
    }

    void onPlayerDeath(Player player) {
        if (!isRunning(player.getUniqueId())) {
            return;
        }
        finishRun(player.getUniqueId(), ChamberRunRecorder.Outcome.DEATH, false);
        if (!bypassCooldown(player)) {
            cooldowns.setCooldown(player.getUniqueId(), spawn.config().chamberCooldownMs());
        }
        spawn.chamberMsg(player, spawn.config().chamberDeath());
    }

    private void startRun(Player player) {
        SpawnConfig cfg = spawn.config();
        int seconds = cfg.chamberDurationSeconds();
        long startedAtMs = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();

        List<LavaSpot> lavaSpots = spawn.lavaSpotStore().loadSpots();
        ChamberLavaRunner lava = new ChamberLavaRunner(plugin, spawn);

        BossBar bar = BossBar.bossBar(
                LEGACY.deserialize(spawn.colorize(formatLavaUi(cfg.chamberBossTitle(), seconds * 1000L, lava))),
                1.0f,
                BossBar.Color.RED,
                BossBar.Overlay.PROGRESS);
        player.showBossBar(bar);

        long endsAt = startedAtMs + seconds * 1000L;
        BukkitTask finish = Bukkit.getScheduler().runTaskLater(
                plugin, () -> completeWin(playerId), seconds * 20L);

        BukkitTask ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !isRunning(playerId)) {
                return;
            }
            ActiveRun run = active.get(playerId);
            if (run == null) {
                return;
            }
            long leftMs = run.endsAtMs - System.currentTimeMillis();
            if (leftMs <= 0) {
                return;
            }
            float progress = Math.max(0f, Math.min(1f, leftMs / (seconds * 1000f)));
            bar.progress(progress);
            bar.name(LEGACY.deserialize(spawn.colorize(
                    formatLavaUi(cfg.chamberBossTitle(), leftMs, run.lava))));
            spawn.actionBar(player, formatLavaUi(cfg.chamberActionBar(), leftMs, run.lava));
        }, 0L, 20L);

        active.put(playerId, new ActiveRun(startedAtMs, endsAt, ticker, finish, bar, lava));
        lava.start(playerId, seconds, lavaSpots);
        spawn.chamberSpawners().boostForRun(player);

        spawn.chamberMsg(player, cfg.chamberStarted());
        if (lavaSpots.isEmpty()) {
            spawn.chamberMsg(player, cfg.chamberLavaNoSpots());
        } else {
            spawn.chamberMsg(player, cfg.chamberLavaArmed()
                    .replace("{lava_total}", Integer.toString(lavaSpots.size())));
        }
        spawn.showChamberTitle(
                player,
                cfg.chamberStartTitle(),
                cfg.chamberStartSubtitle(),
                10, 70, 20);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.2f);
        plugin.getLogger().info("Chamber run started for " + player.getName()
                + "  -  " + lavaSpots.size() + " lava spot(s) armed");
    }

    private void completeWin(UUID playerId) {
        ActiveRun run = active.get(playerId);
        if (run == null) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline() || !spawn.isInsideChamber(player.getLocation())) {
            finishRun(playerId, ChamberRunRecorder.Outcome.DISQUALIFIED, false);
            return;
        }
        SpawnConfig cfg = spawn.config();
        double prize = cfg.chamberPrizeGold();
        boolean paid = payPrize(player, prize);
        finishRun(playerId, ChamberRunRecorder.Outcome.WIN, true, prize, paid);
        if (!bypassCooldown(player)) {
            cooldowns.setCooldown(playerId, cfg.chamberCooldownMs());
        }
        String win = cfg.chamberWin()
                .replace("{gold}", formatGold(prize))
                .replace("{paid}", paid ? "yes" : "pending");
        spawn.chamberMsg(player, win);
        spawn.showChamberTitle(
                player,
                cfg.chamberWinTitle(),
                cfg.chamberWinSubtitle().replace("{gold}", formatGold(prize)),
                10, 80, 20);
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }

    private void finishRun(UUID playerId, ChamberRunRecorder.Outcome outcome, boolean ignored) {
        finishRun(playerId, outcome, false, 0.0, false);
    }

    private void finishRun(
            UUID playerId,
            ChamberRunRecorder.Outcome outcome,
            boolean recordPrize,
            double prizeGold,
            boolean prizePaid) {
        ActiveRun run = active.remove(playerId);
        if (run == null) {
            return;
        }
        if (run.ticker != null) {
            run.ticker.cancel();
        }
        if (run.finish != null) {
            run.finish.cancel();
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline() && run.bar != null) {
            player.hideBossBar(run.bar);
        }
        if (run.lava != null) {
            run.lava.restoreAll();
        }
        spawn.chamberSpawners().stopBoost();
        long durationMs = Math.max(0L, System.currentTimeMillis() - run.startedAtMs);
        ChamberLavaRunner lava = run.lava;
        recorder.record(new ChamberRunRecorder.RunRecord(
                playerId,
                player != null ? player.getName() : "unknown",
                outcome,
                durationMs,
                lava != null ? lava.totalSpots() : 0,
                lava != null ? lava.ceilingsOpened() : 0,
                lava != null ? lava.floorsOpened() : 0,
                recordPrize ? prizeGold : 0.0,
                recordPrize && prizePaid));
    }

    private boolean payPrize(Player player, double amount) {
        RootMcTreasuryService treasury = RootMcTreasuryResolver.resolve(plugin);
        if (treasury != null) {
            return treasury.grantToPlayer(
                    player.getUniqueId(),
                    player.getName(),
                    amount,
                    treasury.treasuryUuid(),
                    treasury.treasuryUsername(),
                    "chamber-survival");
        }
        plugin.getLogger().warning("Chamber prize unpaid  -  treasury unavailable for " + player.getName());
        return false;
    }

    private String formatLavaUi(String template, long leftMs, ChamberLavaRunner lava) {
        if (template == null) {
            return "";
        }
        int total = lava != null ? lava.totalSpots() : 0;
        int opened = lava != null ? lava.ceilingsOpened() : 0;
        int remaining = lava != null ? lava.spotsRemaining() : 0;
        return template
                .replace("{time}", formatDuration(leftMs))
                .replace("{lava_total}", Integer.toString(total))
                .replace("{lava_open}", Integer.toString(opened))
                .replace("{lava_remaining}", Integer.toString(remaining));
    }

    private static boolean bypassCooldown(Player player) {
        return player.isOp() || player.hasPermission("rootspawn.chamber.bypass");
    }

    private static String formatDuration(long ms) {
        long totalSec = Math.max(0L, ms / 1000L);
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return min + ":" + (sec < 10 ? "0" : "") + sec;
    }

    private static String formatGold(double gold) {
        if (gold == Math.rint(gold)) {
            return String.valueOf((long) gold);
        }
        return String.format("%.3f", gold);
    }

    private static final class ActiveRun {
        final long startedAtMs;
        final long endsAtMs;
        final BukkitTask ticker;
        final BukkitTask finish;
        final BossBar bar;
        final ChamberLavaRunner lava;

        ActiveRun(
                long startedAtMs,
                long endsAtMs,
                BukkitTask ticker,
                BukkitTask finish,
                BossBar bar,
                ChamberLavaRunner lava) {
            this.startedAtMs = startedAtMs;
            this.endsAtMs = endsAtMs;
            this.ticker = ticker;
            this.finish = finish;
            this.bar = bar;
            this.lava = lava;
        }
    }
}
