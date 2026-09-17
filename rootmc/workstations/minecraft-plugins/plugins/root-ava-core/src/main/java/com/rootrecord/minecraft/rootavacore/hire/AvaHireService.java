package com.rootrecord.minecraft.rootavacore.hire;

import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.rootavacore.AvaConfig;
import com.rootrecord.minecraft.rootavacore.RootAvaCorePlugin;
import com.rootrecord.minecraft.rootavacore.autonomy.AvaClaimCrew;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Player hire jobs: sidekick follow, claim mine/excavate, surface teardown.
 * Dig work is server-side (claim-scoped) — Mannequin never breaks blocks.
 */
public final class AvaHireService {

    /** Dig jobs must start at this Y or above; below is free-reign mining (no claims). */
    public static final int MINING_FLOOR_Y = 0;
    /** One dig tick per second; random 0–8 breaks → max 480 blocks/min per job. */
    public static final int DIG_TICK_PERIOD_TICKS = 20;
    public static final int DIG_MAX_PER_SECOND = 8;
    /** Random 0–{@link #DIG_MAX_PER_SECOND} inclusive → average half of max. */
    public static final double DIG_AVG_PER_SECOND = DIG_MAX_PER_SECOND / 2.0d;
    /** Empty rescans on a finished layer before descending (catches dirt→grass). */
    public static final int LAYER_EMPTY_PASSES = 2;
    /** Dig this many Y-rows together (top of the pair first). */
    public static final int DIG_LAYER_ROWS = 2;
    /** Keep rescanning this many blocks above digY for refills (water, dirt→grass, …). */
    public static final int DIG_ABOVE_RESCAN_BLOCKS = 20;
    /** Keep trying player-floor on this Y before deferring and going down. */
    public static final int LAYER_PLAYER_STALL_TICKS = 15;
    /** Ore drop preview layer (deepslate diamond belt). */
    public static final int ORE_DROP_Y = -59;
    /** Stand within this many blocks of the claim ring (center or rim). */
    public static final int RING_STAND_BLOCKS = 8;
    public static final int VOLUME_RATE_STEP_BLOCKS = 100_000;
    public static final double VOLUME_RATE_STEP_GOLD = 0.01d;

    public enum JobType {
        SIDEKICK,
        MINE,
        TEARDOWN,
        HOUR,
        CHUNK,
        MINEDOWN
    }

    public static final class Job {
        public final UUID id;
        public final UUID hirer;
        public final String hirerName;
        public final JobType type;
        public final double charged;
        public final long startedAt;
        public long endsAt;
        public final Deque<long[]> blocks = new ArrayDeque<>(); // packed x,y,z per world
        public final String world;
        public int broken;
        public boolean done;
        public Location workAnchor;
        public UUID claimOwnerId;
        public int standY = Integer.MIN_VALUE;
        public int digY = Integer.MIN_VALUE;
        public int oresAtNeg59;
        public transient AvaClaimCrew crew;
        public transient List<int[]> columnCache;
        public transient int layerEmptyStreak;
        public transient int playerStallTicks;
        public transient boolean layerHadWork;
        public final transient List<long[]> deferredFloor = new ArrayList<>();

        Job(UUID hirer, String hirerName, JobType type, double charged, String world) {
            this(UUID.randomUUID(), hirer, hirerName, type, charged, System.currentTimeMillis(), world);
        }

        Job(UUID id, UUID hirer, String hirerName, JobType type, double charged, long startedAt, String world) {
            this.id = id != null ? id : UUID.randomUUID();
            this.hirer = hirer;
            this.hirerName = hirerName;
            this.type = type;
            this.charged = charged;
            this.startedAt = startedAt > 0 ? startedAt : System.currentTimeMillis();
            this.world = world;
        }
    }

    private static final long CONTRACT_QUOTE_MS = 90_000L;

    public static final class PendingContract {
        public final JobType type;
        public final List<long[]> blocks;
        public final double cost;
        public final int standY;
        public final String world;
        public final long expiresAt;
        public final double perBlock;
        public final int overlapSkipped;
        public final int oresAtNeg59;
        public final int buildsSkipped;

        PendingContract(
                JobType type,
                List<long[]> blocks,
                double cost,
                int standY,
                String world,
                long expiresAt,
                double perBlock,
                int overlapSkipped,
                int oresAtNeg59,
                int buildsSkipped) {
            this.type = type;
            this.blocks = blocks;
            this.cost = cost;
            this.standY = standY;
            this.world = world;
            this.expiresAt = expiresAt;
            this.perBlock = Math.max(0, perBlock);
            this.overlapSkipped = Math.max(0, overlapSkipped);
            this.oresAtNeg59 = Math.max(0, oresAtNeg59);
            this.buildsSkipped = Math.max(0, buildsSkipped);
        }
    }

    private final RootAvaCorePlugin plugin;
    private final Map<UUID, Job> byHirer = new ConcurrentHashMap<>();
    private final Map<UUID, PendingContract> pendingContracts = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> chunkUses = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> minedownUses = new ConcurrentHashMap<>();
    private final List<Job> active = new ArrayList<>();
    private BukkitTask workTask;
    private BukkitTask sidekickTask;
    private BukkitTask hourTask;
    private BukkitTask saveTask;
    private BukkitTask loadTask;
    private volatile boolean dirty;
    private volatile boolean persistReady;

    public AvaHireService(RootAvaCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        cancelTickers();
        loadRates();
        workTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickWork, 40L, DIG_TICK_PERIOD_TICKS);
        sidekickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSidekicks, 20L, 20L);
        long hourPeriod = Math.max(60L, plugin.config().autonomy().tickSeconds() * 20L);
        hourTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickHourJobs, 80L, hourPeriod);
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flushIfDirty, 200L, 200L);
        persistReady = false;
        loadTask = Bukkit.getScheduler().runTaskLater(plugin, this::ensureLoaded, 60L);
    }

    public void stop() {
        saveRates();
        if (persistReady || !active.isEmpty()) {
            saveJobs();
        }
        cancelTickers();
        for (Job job : active) {
            if (job.crew != null) job.crew.stop();
            plugin.presence().despawnHire(job.hirer);
        }
        active.clear();
        byHirer.clear();
        pendingContracts.clear();
        persistReady = false;
    }

    private void cancelTickers() {
        if (workTask != null) {
            workTask.cancel();
            workTask = null;
        }
        if (sidekickTask != null) {
            sidekickTask.cancel();
            sidekickTask = null;
        }
        if (hourTask != null) {
            hourTask.cancel();
            hourTask = null;
        }
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (loadTask != null) {
            loadTask.cancel();
            loadTask = null;
        }
    }

    public Job jobFor(UUID hirer) {
        return byHirer.get(hirer);
    }

    public List<Job> activeJobs() {
        return List.copyOf(active);
    }

    /** True when any hire job is in progress (sidekick or dig). */
    public boolean isBusy() {
        for (Job job : active) {
            if (job != null && !job.done) return true;
        }
        return false;
    }

    public int activeCount() {
        int n = 0;
        for (Job job : active) {
            if (job != null && !job.done) n++;
        }
        return n;
    }

    public String hireHelp() {
        return "&7Stand in &fyour&7 claim (Y 0+). Claim clear: full circle (center or rim, incl. expansions)."
                + "\n&f/ava hire hour&7 · &f/ava hire sidekick&7 · &f/ava hire mine&7 · &f/ava hire teardown"
                + "\n&f/ava hire claim&7 or &f/ava hire minedown&7 → then click &a[Confirm]"
                + " &8(0.01 G/block · +0.01 per 100k · natural blocks only)"
                + "\n&f/ava stop&7 · &f/ava hire status"
                + "\n&8Claim clear cannot be stopped · no refunds. Builds are left standing.";
    }

    public String nextContractHint(UUID playerId, JobType type) {
        if (!isContractType(type)) return "";
        return "0.01 G/block · +0.01 per 100k · no base";
    }

    /** Stop every hire job and pending quote. No refunds. */
    public int cancelAll() {
        int n = pendingContracts.size();
        pendingContracts.clear();
        List<Job> jobs = new ArrayList<>(active);
        for (Job job : jobs) {
            if (job == null || job.done) continue;
            job.done = true;
            byHirer.remove(job.hirer);
            active.remove(job);
            if (job.crew != null) job.crew.stop();
            plugin.presence().despawnHire(job.hirer);
            n++;
        }
        markDirty();
        saveJobs();
        return n;
    }

    public String hireSidekick(Player player, int minutes) {
        if (!plugin.config().hire().enabled()) return "hire_disabled";
        if (!(player instanceof Player)) return "players_only";
        AvaConfig.HireConfig h = plugin.config().hire();
        int mins = Math.max(1, Math.min(h.sidekickMaxMinutes(), minutes));
        if (byHirer.containsKey(player.getUniqueId())) return "already_hired";
        if (active.size() >= h.concurrentJobs()) return "busy";
        pendingContracts.remove(player.getUniqueId());
        String land = com.rootrecord.minecraft.rootavacore.AvaLandAccess.requireOwnedLand(player);
        if (land != null) return land;
        double cost = h.sidekickGold() > 0
                ? round2(h.sidekickGold())
                : round2(h.sidekickPerMinute() * mins);
        String pay = charge(player, cost);
        if (pay != null) return pay;

        Job job = new Job(player.getUniqueId(), player.getName(), JobType.SIDEKICK, cost, player.getWorld().getName());
        job.endsAt = System.currentTimeMillis() + mins * 60_000L;
        active.add(job);
        byHirer.put(player.getUniqueId(), job);
        markDirty();

        spawnHireBody(player, player.getLocation(), true);
        plugin.getLogger().info("Hire sidekick · " + player.getName() + " · " + mins + "m · " + cost + "G");
        return null;
    }

    public String hireHour(Player player) {
        if (!plugin.config().hire().enabled()) return "hire_disabled";
        AvaConfig.HireConfig h = plugin.config().hire();
        if (byHirer.containsKey(player.getUniqueId())) return "already_hired";
        if (active.size() >= h.concurrentJobs()) return "busy";
        pendingContracts.remove(player.getUniqueId());
        String land = com.rootrecord.minecraft.rootavacore.AvaLandAccess.requireOwnedLand(player);
        if (land != null) return land;
        String chest = requireHireChest(player);
        if (chest != null) return chest;
        Object claim = claimAt(player.getLocation());
        if (claim == null) {
            claim = com.rootrecord.minecraft.rootavacore.AvaLandAccess.territoryAt(player);
        }
        if (claim == null) return "not_in_claim";
        if (!ownsLand(claim, player.getUniqueId())) return "not_your_claim";

        double cost = round2(h.hourGold());
        String pay = charge(player, cost);
        if (pay != null) return pay;

        UUID ownerId = AvaClaimCrew.claimOwnerId(claim);
        if (ownerId == null) ownerId = player.getUniqueId();
        Location anchor = AvaClaimCrew.claimAnchor(claim);
        if (anchor == null) anchor = player.getLocation().clone();

        Job job = new Job(player.getUniqueId(), player.getName(), JobType.HOUR, cost, player.getWorld().getName());
        job.endsAt = System.currentTimeMillis() + h.hourMinutes() * 60_000L;
        job.workAnchor = anchor.clone();
        job.claimOwnerId = ownerId;
        job.crew = new AvaClaimCrew(plugin, plugin.storage(), player.getUniqueId(), ownerId);
        active.add(job);
        byHirer.put(player.getUniqueId(), job);
        markDirty();

        spawnHireBody(player, player.getLocation(), false);
        if (plugin.config().presence().enabled()) {
            plugin.presence().parkAndWork(player.getUniqueId(), anchor);
        }
        plugin.getLogger().info(
                "Hire hour · " + player.getName() + " · " + h.hourMinutes() + "m · " + cost + "G");
        return "hour:" + h.hourMinutes() + ":" + fmt(cost);
    }

    public String hireMine(Player player, int radius) {
        return startDigJob(player, radius, JobType.MINE);
    }

    public String hireTeardown(Player player, int radius) {
        return startDigJob(player, radius, JobType.TEARDOWN);
    }

    public String hireChunk(Player player) {
        return quoteContract(player, JobType.CHUNK);
    }

    public String hireMinedown(Player player) {
        return quoteContract(player, JobType.MINEDOWN);
    }

    public double walletGold(Player player) {
        RootMcEconomyService eco = RootMcEconomyResolver.resolve(plugin);
        if (eco == null || player == null) {
            return 0;
        }
        return round2(eco.balance(player.getUniqueId()));
    }

    public PendingContract pendingContract(UUID playerId) {
        return pendingContracts.get(playerId);
    }

    public Map<UUID, PendingContract> pendingQuotes() {
        return Map.copyOf(pendingContracts);
    }

    public String confirmContract(Player player) {
        PendingContract quote = pendingContracts.get(player.getUniqueId());
        if (quote == null) return "no_quote";
        if (System.currentTimeMillis() > quote.expiresAt) {
            pendingContracts.remove(player.getUniqueId());
            return "quote_expired";
        }
        if (!plugin.config().hire().enabled()) return "hire_disabled";
        if (byHirer.containsKey(player.getUniqueId())) return "already_hired";
        if (active.size() >= plugin.config().hire().concurrentJobs()) return "busy";
        String landErr = com.rootrecord.minecraft.rootavacore.AvaLandAccess.requireOwnedLand(player);
        if (landErr != null) return landErr;
        String chest = requireHireChest(player);
        if (chest != null) return chest;
        Object land = workLandAt(player);
        if (land == null) return "not_in_claim";
        if (!ownsLand(land, player.getUniqueId())) return "not_your_claim";

        String pay = charge(player, quote.cost);
        if (pay != null) return pay;

        pendingContracts.remove(player.getUniqueId());
        Job job = new Job(player.getUniqueId(), player.getName(), quote.type, quote.cost, quote.world);
        job.blocks.addAll(quote.blocks);
        job.standY = quote.standY;
        job.oresAtNeg59 = quote.oresAtNeg59;
        job.workAnchor = player.getLocation().clone();
        UUID ownerId = AvaClaimCrew.claimOwnerId(land);
        job.claimOwnerId = ownerId != null ? ownerId : player.getUniqueId();
        active.add(job);
        byHirer.put(player.getUniqueId(), job);
        markDirty();
        spawnHireAtWork(player, job);
        if (plugin.godLoadout() != null) {
            plugin.godLoadout().applyNow();
        }
        plugin.getLogger().info(
                "Hire contract " + jobLabel(quote.type) + " · " + player.getName()
                        + " · " + quote.blocks.size() + " blocks · " + quote.cost + "G · Y" + quote.standY + " down");
        return "contract:" + jobLabel(quote.type) + ":" + quote.blocks.size() + ":" + quote.cost;
    }

    public String cancel(Player player) {
        return player == null ? "none" : cancelHirer(player.getUniqueId());
    }

    public String cancelHirer(UUID hirer) {
        if (hirer == null) return "none";
        PendingContract quote = pendingContracts.remove(hirer);
        Job job = byHirer.get(hirer);
        if (job == null) {
            return quote != null ? "quote_cancelled" : "none";
        }
        byHirer.remove(hirer);
        active.remove(job);
        job.done = true;
        if (job.crew != null) job.crew.stop();
        plugin.presence().despawnHire(hirer);
        markDirty();
        saveJobs();
        if (isContractType(job.type)) {
            return "cancelled_contract";
        }
        if (isDigType(job.type) && !job.blocks.isEmpty()) {
            AvaConfig.HireConfig h = plugin.config().hire();
            double per = digPerBlock(h, job.type);
            double refund = round2(job.blocks.size() * per * 0.5);
            if (refund > 0) {
                RootMcEconomyService eco = RootMcEconomyResolver.resolve(plugin);
                if (eco != null) {
                    eco.deposit(hirer, refund);
                }
            }
            return "cancelled_refund:" + refund;
        }
        return "cancelled";
    }

    public String cancelJob(UUID jobId) {
        if (jobId == null) return "none";
        for (Job job : List.copyOf(active)) {
            if (job != null && jobId.equals(job.id)) {
                return cancelHirer(job.hirer);
            }
        }
        return "none";
    }

    public String cancelQuote(UUID playerId) {
        if (playerId == null) return "none";
        PendingContract quote = pendingContracts.remove(playerId);
        return quote != null ? "quote_cancelled" : "none";
    }

    private String quoteContract(Player player, JobType type) {
        if (!plugin.config().hire().enabled()) return "hire_disabled";
        AvaConfig.HireConfig h = plugin.config().hire();
        if (byHirer.containsKey(player.getUniqueId())) return "already_hired";
        if (active.size() >= h.concurrentJobs()) return "busy";
        String landErr = com.rootrecord.minecraft.rootavacore.AvaLandAccess.requireOwnedLand(player);
        if (landErr != null) return landErr;
        String chest = requireHireChest(player);
        if (chest != null) return chest;
        String floor = requireSurfaceStart(player);
        if (floor != null) return floor;
        Object land = type == JobType.CHUNK ? claimAt(player.getLocation()) : workLandAt(player);
        if (land == null) return "not_in_claim";
        if (!ownsLand(land, player.getUniqueId())) return "not_your_claim";
        if (type == JobType.CHUNK) {
            String ring = requireNearClaimRing(player, land);
            if (ring != null) return ring;
        } else {
            String footprint = requireFullPlotInWorkLand(player, type);
            if (footprint != null) return footprint;
        }

        CollectResult collected = collectContractBlocks(player, land, type);
        List<Block> targets = collected.blocks();
        if (targets.isEmpty()) {
            if (collected.overlapSkipped() > 0) return "overlap_only";
            if (collected.buildsSkipped() > 0) return "builds_only";
            return "nothing_to_clear";
        }
        int maxVol = digMaxVolume(h, type);
        if (targets.size() > maxVol) {
            return "too_large:" + targets.size() + ":" + maxVol;
        }
        double perBlock = volumePerBlockRate(targets.size());
        double cost = round2(targets.size() * perBlock);
        List<long[]> coords = new ArrayList<>(targets.size());
        for (Block b : targets) {
            coords.add(new long[] {b.getX(), b.getY(), b.getZ()});
        }
        int standY = player.getLocation().getBlockY();
        String world = player.getWorld().getName();
        pendingContracts.put(
                player.getUniqueId(),
                new PendingContract(
                        type,
                        coords,
                        cost,
                        standY,
                        world,
                        System.currentTimeMillis() + CONTRACT_QUOTE_MS,
                        perBlock,
                        collected.overlapSkipped(),
                        collected.oresAtNeg59(),
                        collected.buildsSkipped()));
        return "quote:"
                + jobLabel(type)
                + ":"
                + coords.size()
                + ":"
                + cost
                + ":"
                + standY
                + ":"
                + walletFmt(player)
                + ":"
                + perBlock
                + ":"
                + collected.overlapSkipped()
                + ":"
                + collected.oresAtNeg59()
                + ":"
                + collected.buildsSkipped();
    }

    private String startDigJob(Player player, int radius, JobType type) {
        if (!plugin.config().hire().enabled()) return "hire_disabled";
        AvaConfig.HireConfig h = plugin.config().hire();
        if (byHirer.containsKey(player.getUniqueId())) return "already_hired";
        if (active.size() >= h.concurrentJobs()) return "busy";
        pendingContracts.remove(player.getUniqueId());

        int r = Math.max(1, Math.min(h.maxRadius(), radius <= 0 ? h.defaultRadius() : radius));
        String landErr = com.rootrecord.minecraft.rootavacore.AvaLandAccess.requireOwnedLand(player);
        if (landErr != null) return landErr;
        if (type == JobType.MINE) {
            String chest = requireHireChest(player);
            if (chest != null) return chest;
        }
        String floor = requireSurfaceStart(player);
        if (floor != null) return floor;
        Object land = workLandAt(player);
        if (land == null) return "not_in_claim";
        if (!ownsLand(land, player.getUniqueId())) return "not_your_claim";
        String footprint = requireFullPlotInWorkLand(player, type);
        if (footprint != null) return footprint;

        List<Block> targets = collectBlocks(player, r, type);
        if (targets.isEmpty()) return "nothing_to_clear";
        int maxVol = digMaxVolume(h, type);
        if (targets.size() > maxVol) {
            return "too_large:" + targets.size() + ":" + maxVol;
        }

        double per = digPerBlock(h, type);
        double base = digBase(h, type);
        double cost = round2(base + targets.size() * per);
        String pay = charge(player, cost);
        if (pay != null) return pay;

        Job job = new Job(player.getUniqueId(), player.getName(), type, cost, player.getWorld().getName());
        for (Block b : targets) {
            job.blocks.add(new long[] {b.getX(), b.getY(), b.getZ()});
        }
        job.standY = player.getLocation().getBlockY();
        job.oresAtNeg59 = countOresAtY(targets, ORE_DROP_Y);
        job.workAnchor = player.getLocation().clone();
        UUID ownerId = AvaClaimCrew.claimOwnerId(land);
        job.claimOwnerId = ownerId != null ? ownerId : player.getUniqueId();
        active.add(job);
        byHirer.put(player.getUniqueId(), job);
        markDirty();

        spawnHireAtWork(player, job);
        if (plugin.godLoadout() != null) {
            plugin.godLoadout().applyNow();
        }
        plugin.getLogger().info(
                "Hire " + type.name().toLowerCase(Locale.ROOT) + " · " + player.getName()
                        + " · " + targets.size() + " blocks · " + cost + "G");
        return "queued:" + targets.size() + ":" + cost;
    }

    private String charge(Player player, double cost) {
        if (cost <= 0) return null;
        RootMcEconomyService eco = RootMcEconomyResolver.resolve(plugin);
        if (eco == null) return "no_economy";
        if (!eco.has(player.getUniqueId(), cost)) {
            return "broke:" + fmt(eco.balance(player.getUniqueId())) + ":" + fmt(cost);
        }
        if (!eco.withdraw(player.getUniqueId(), cost)) {
            return "withdraw_failed";
        }
        try {
            eco.sinkServiceFee(player.getUniqueId(), player.getName(), cost, "hire");
        } catch (Throwable t) {
            UUID ava = plugin.config().powers().avaUuid();
            if (ava != null) {
                try {
                    eco.deposit(ava, cost);
                } catch (Throwable ignored) {
                    plugin.getLogger().log(Level.WARNING, "Hire sink to reserve failed: " + t.getMessage());
                }
            } else {
                plugin.getLogger().log(Level.WARNING, "Hire sink to reserve failed: " + t.getMessage());
            }
        }
        return null;
    }

    private void tickSidekicks() {
        long now = System.currentTimeMillis();
        Iterator<Job> it = active.iterator();
        while (it.hasNext()) {
            Job job = it.next();
            if (job.type != JobType.SIDEKICK) continue;
            Player p = Bukkit.getPlayer(job.hirer);
            if (p == null || !p.isOnline()) {
                // Pause timer while the hirer is offline.
                if (job.endsAt > 0) {
                    job.endsAt += 1000L;
                }
                continue;
            }
            if (job.done || now >= job.endsAt) {
                job.done = true;
                byHirer.remove(job.hirer);
                it.remove();
                p.sendMessage(plugin.colorize(plugin.config().prefix() + "&eSidekick hire ended."));
                plugin.presence().despawnHire(job.hirer);
                markDirty();
            } else if (plugin.config().presence().enabled()) {
                plugin.presence().follow(job.hirer, p);
            }
        }
    }

    private void tickHourJobs() {
        long now = System.currentTimeMillis();
        Iterator<Job> it = active.iterator();
        while (it.hasNext()) {
            Job job = it.next();
            if (job.type != JobType.HOUR || job.done) continue;
            if (now >= job.endsAt) {
                job.done = true;
                if (job.crew != null) job.crew.stop();
                byHirer.remove(job.hirer);
                it.remove();
                plugin.presence().despawnHire(job.hirer);
                Player p = Bukkit.getPlayer(job.hirer);
                if (p != null) {
                    p.sendMessage(plugin.colorize(plugin.config().prefix() + "&eHour hire ended."));
                }
                markDirty();
                continue;
            }
            Object claim = job.workAnchor != null ? claimAt(job.workAnchor) : null;
            if (claim == null || !ownsLand(claim, job.hirer)) {
                job.done = true;
                if (job.crew != null) job.crew.stop();
                byHirer.remove(job.hirer);
                it.remove();
                plugin.presence().despawnHire(job.hirer);
                Player p = Bukkit.getPlayer(job.hirer);
                if (p != null) {
                    p.sendMessage(plugin.colorize(plugin.config().prefix() + "&eHour hire ended — claim gone."));
                }
                markDirty();
                continue;
            }
            Location anchor = job.workAnchor != null ? job.workAnchor : AvaClaimCrew.claimAnchor(claim);
            if (job.crew != null) {
                job.crew.tickClaim(claim, anchor);
            }
        }
    }

    private void tickWork() {
        Iterator<Job> it = active.iterator();
        while (it.hasNext()) {
            Job job = it.next();
            if (job.type == JobType.SIDEKICK || job.type == JobType.HOUR || job.done) continue;
            World world = Bukkit.getWorld(job.world);
            if (world == null) {
                continue;
            }
            Player hirer = Bukkit.getPlayer(job.hirer);
            int minY = world.getMinHeight();
            Location[] focus = {null};
            int n = flushDeferredFloor(job, world, DIG_MAX_PER_SECOND, focus);
            if (!advanceDigLayer(job, world, minY)) {
                if (job.deferredFloor.isEmpty()) {
                    finishDig(job, it, null);
                    if (hirer != null) {
                        hirer.sendMessage(plugin.colorize(
                                plugin.config().prefix()
                                        + "&a"
                                        + jobLabel(job.type)
                                        + " done &8· &f"
                                        + job.broken
                                        + " blocks"));
                    }
                }
                continue;
            }
            int speed = plugin.playerChests() != null ? plugin.playerChests().getSpeedLevel(job.hirer) : 0;
            int budget = ThreadLocalRandom.current().nextInt(0, DIG_MAX_PER_SECOND + 1) + speed;
            int scanned = 0;
            List<long[]> skippedFloor = new ArrayList<>();
            while (n < budget && scanned < 128) {
                long[] c = pollQueuedInWindow(job.blocks, job.digY);
                if (c == null) break;
                scanned++;
                Block block = world.getBlockAt((int) c[0], (int) c[1], (int) c[2]);
                if (!mayBreak(block, job)) continue;
                if (nearOnlinePlayer(world, (int) c[0], (int) c[1], (int) c[2])) {
                    skippedFloor.add(c);
                    continue;
                }
                breakDigBlock(block, job);
                job.broken++;
                job.layerHadWork = true;
                focus[0] = block.getLocation();
                n++;
            }
            for (int i = skippedFloor.size() - 1; i >= 0; i--) {
                job.blocks.addFirst(skippedFloor.get(i));
            }
            showHireWorking(job, world, focus[0]);
            int left = job.blocks.size() + job.deferredFloor.size();
            if (left <= 0 && job.digY < minY) {
                finishDig(job, it, null);
                if (hirer != null) {
                    hirer.sendMessage(plugin.colorize(
                            plugin.config().prefix()
                                    + "&a"
                                    + jobLabel(job.type)
                                    + " done &8· &f"
                                    + job.broken
                                    + " blocks"));
                }
            } else if (job.broken > 0 && job.broken % 60 == 0 && n > 0) {
                markDirty();
                if (hirer != null) {
                    hirer.sendMessage(plugin.colorize(
                            plugin.config().prefix()
                                    + "&7"
                                    + jobLabel(job.type)
                                    + " · &f"
                                    + job.broken
                                    + " &7cleared · &f"
                                    + left
                                    + " &7left · &e"
                                    + etaPhrase(left)));
                }
            }
        }
    }

    /** @return false when the dig is fully below world min and nothing left to retry. */
    private boolean advanceDigLayer(Job job, World world, int minY) {
        if (job.digY == Integer.MIN_VALUE) {
            int queued = maxQueuedY(job.blocks);
            job.digY = queued != Integer.MIN_VALUE
                    ? queued
                    : (job.standY != Integer.MIN_VALUE ? job.standY : minY);
            job.layerHadWork = hasQueuedInWindow(job.blocks, job.digY);
            job.layerEmptyStreak = 0;
            job.playerStallTicks = 0;
        }
        int aboveQueued = maxQueuedY(job.blocks);
        if (aboveQueued != Integer.MIN_VALUE && aboveQueued > job.digY) {
            job.digY = aboveQueued;
            job.layerHadWork = true;
            job.layerEmptyStreak = 0;
            job.playerStallTicks = 0;
        }
        int capY = job.standY != Integer.MIN_VALUE ? job.standY : world.getMaxHeight() - 1;
        if (job.digY < capY) {
            int top = Math.min(capY, job.digY + DIG_ABOVE_RESCAN_BLOCKS);
            int bestAbove = Integer.MIN_VALUE;
            for (int y = job.digY + 1; y <= top; y++) {
                if (rescanLayer(job, world, y) > 0) {
                    bestAbove = y;
                }
            }
            if (bestAbove != Integer.MIN_VALUE) {
                job.digY = bestAbove;
                job.layerHadWork = true;
                job.layerEmptyStreak = 0;
                job.playerStallTicks = 0;
            }
        }
        int skippedAir = 0;
        while (job.digY >= minY && !hasQueuedInWindow(job.blocks, job.digY)) {
            int found = rescanWindow(job, world, job.digY, minY);
            if (found > 0) {
                job.layerHadWork = true;
                job.layerEmptyStreak = 0;
                if (onlyPlayerNearInWindow(job, world, job.digY)) {
                    job.playerStallTicks++;
                    if (job.playerStallTicks >= LAYER_PLAYER_STALL_TICKS) {
                        deferQueuedInWindow(job, job.digY);
                        job.playerStallTicks = 0;
                        job.layerHadWork = false;
                        job.digY -= DIG_LAYER_ROWS;
                        continue;
                    }
                } else {
                    job.playerStallTicks = 0;
                }
                break;
            }
            if (job.layerHadWork && job.layerEmptyStreak < LAYER_EMPTY_PASSES) {
                job.layerEmptyStreak++;
                break;
            }
            job.layerHadWork = false;
            job.layerEmptyStreak = 0;
            job.playerStallTicks = 0;
            job.digY -= DIG_LAYER_ROWS;
            skippedAir++;
            if (skippedAir >= 64) break;
        }
        return job.digY >= minY || hasQueuedInWindow(job.blocks, job.digY) || !job.deferredFloor.isEmpty();
    }

    private int flushDeferredFloor(Job job, World world, int budget, Location[] focusOut) {
        if (job.deferredFloor.isEmpty() || budget <= 0) return 0;
        int n = 0;
        List<long[]> keep = new ArrayList<>();
        for (long[] c : job.deferredFloor) {
            if (c == null) continue;
            Block block = world.getBlockAt((int) c[0], (int) c[1], (int) c[2]);
            if (!mayBreak(block, job)) continue;
            if (nearOnlinePlayer(world, (int) c[0], (int) c[1], (int) c[2]) || n >= budget) {
                keep.add(c);
                continue;
            }
            breakDigBlock(block, job);
            job.broken++;
            if (focusOut != null && focusOut.length > 0) {
                focusOut[0] = block.getLocation();
            }
            n++;
        }
        job.deferredFloor.clear();
        job.deferredFloor.addAll(keep);
        return n;
    }

    private int rescanLayer(Job job, World world, int y) {
        List<int[]> cols = workColumns(job, world);
        if (cols.isEmpty()) return 0;
        Set<String> queued = queuedKeysAtY(job.blocks, y);
        for (long[] c : job.deferredFloor) {
            if (c != null && (int) c[1] == y) {
                queued.add((int) c[0] + "," + (int) c[2]);
            }
        }
        int added = 0;
        List<long[]> found = new ArrayList<>();
        for (int[] col : cols) {
            if (queued.contains(col[0] + "," + col[1])) continue;
            Block block = world.getBlockAt(col[0], y, col[1]);
            if (!mayBreak(block, job)) continue;
            found.add(new long[] {col[0], y, col[1]});
            added++;
        }
        for (int i = found.size() - 1; i >= 0; i--) {
            job.blocks.addFirst(found.get(i));
        }
        return added;
    }

    private List<int[]> workColumns(Job job, World world) {
        if (job.columnCache != null) return job.columnCache;
        Location anchor = job.workAnchor;
        if (anchor == null || world == null) {
            job.columnCache = List.of();
            return job.columnCache;
        }
        List<int[]> cols = new ArrayList<>();
        if (job.type == JobType.CHUNK) {
            Object claim = claimAt(anchor);
            if (claim == null) claim = workLandAtColumn(anchor);
            if (claim != null) {
                int cx = claimCenterX(claim);
                int cz = claimCenterZ(claim);
                int radius = Math.max(1, claimRadius(claim));
                int r2 = radius * radius;
                for (int x = cx - radius; x <= cx + radius; x++) {
                    for (int z = cz - radius; z <= cz + radius; z++) {
                        int dx = x - cx;
                        int dz = z - cz;
                        if (dx * dx + dz * dz > r2) continue;
                        Location col = new Location(world, x + 0.5, Math.max(1, MINING_FLOOR_Y), z + 0.5);
                        if (!claimContains(claim, col)) continue;
                        if (columnCoveredByForeignClaim(col, claim)) continue;
                        cols.add(new int[] {x, z});
                    }
                }
            }
        } else {
            AvaConfig.HireConfig h = plugin.config().hire();
            int cx = anchor.getBlockX();
            int cz = anchor.getBlockZ();
            int minX;
            int maxX;
            int minZ;
            int maxZ;
            boolean cylinder = job.type == JobType.MINE || job.type == JobType.TEARDOWN;
            int radius = job.type == JobType.MINEDOWN ? 0 : Math.max(1, h.defaultRadius());
            if (job.type == JobType.MINEDOWN) {
                int half = Math.max(0, (h.minedownSize() - 1) / 2);
                minX = cx - half;
                maxX = cx + half;
                minZ = cz - half;
                maxZ = cz + half;
            } else {
                minX = cx - radius;
                maxX = cx + radius;
                minZ = cz - radius;
                maxZ = cz + radius;
            }
            int r2 = radius * radius;
            UUID owner = job.hirer;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (cylinder && (x - cx) * (x - cx) + (z - cz) * (z - cz) > r2) continue;
                    Location col = new Location(world, x + 0.5, Math.max(1, MINING_FLOOR_Y), z + 0.5);
                    if (!ownsWorkColumn(col, owner)) continue;
                    cols.add(new int[] {x, z});
                }
            }
        }
        job.columnCache = cols;
        return cols;
    }

    private int rescanWindow(Job job, World world, int digY, int minY) {
        int found = 0;
        for (int y = digY; y > digY - DIG_LAYER_ROWS; y--) {
            if (y < minY) break;
            found += rescanLayer(job, world, y);
        }
        return found;
    }

    private static boolean hasQueuedInWindow(Deque<long[]> blocks, int digY) {
        for (int y = digY; y > digY - DIG_LAYER_ROWS; y--) {
            if (hasQueuedY(blocks, y)) return true;
        }
        return false;
    }

    private static long[] pollQueuedInWindow(Deque<long[]> blocks, int digY) {
        for (int y = digY; y > digY - DIG_LAYER_ROWS; y--) {
            long[] c = pollQueuedAtY(blocks, y);
            if (c != null) return c;
        }
        return null;
    }

    private boolean onlyPlayerNearInWindow(Job job, World world, int digY) {
        boolean any = false;
        for (int y = digY; y > digY - DIG_LAYER_ROWS; y--) {
            for (long[] c : job.blocks) {
                if (c == null || (int) c[1] != y) continue;
                any = true;
                Block block = world.getBlockAt((int) c[0], (int) c[1], (int) c[2]);
                if (!mayBreak(block, job)) continue;
                if (!nearOnlinePlayer(world, (int) c[0], (int) c[1], (int) c[2])) {
                    return false;
                }
            }
        }
        return any;
    }

    private static void deferQueuedInWindow(Job job, int digY) {
        for (int y = digY; y > digY - DIG_LAYER_ROWS; y--) {
            deferQueuedAtY(job, y);
        }
    }

    private static boolean hasQueuedY(Deque<long[]> blocks, int y) {
        if (blocks == null || blocks.isEmpty()) return false;
        for (long[] c : blocks) {
            if (c != null && (int) c[1] == y) return true;
        }
        return false;
    }

    private static int maxQueuedY(Deque<long[]> blocks) {
        int max = Integer.MIN_VALUE;
        if (blocks == null) return max;
        for (long[] c : blocks) {
            if (c == null) continue;
            int y = (int) c[1];
            if (y > max) max = y;
        }
        return max;
    }

    private static Set<String> queuedKeysAtY(Deque<long[]> blocks, int y) {
        Set<String> keys = new HashSet<>();
        if (blocks == null) return keys;
        for (long[] c : blocks) {
            if (c != null && (int) c[1] == y) {
                keys.add((int) c[0] + "," + (int) c[2]);
            }
        }
        return keys;
    }

    private static long[] pollQueuedAtY(Deque<long[]> blocks, int y) {
        if (blocks == null || blocks.isEmpty()) return null;
        List<long[]> held = new ArrayList<>();
        long[] found = null;
        while (!blocks.isEmpty()) {
            long[] c = blocks.pollFirst();
            if (c == null) continue;
            int cy = (int) c[1];
            if (cy == y) {
                found = c;
                break;
            }
            held.add(c);
            if (cy < y) break;
        }
        for (int i = held.size() - 1; i >= 0; i--) {
            blocks.addFirst(held.get(i));
        }
        return found;
    }

    private boolean onlyPlayerNearAtY(Job job, World world, int y) {
        boolean any = false;
        for (long[] c : job.blocks) {
            if (c == null || (int) c[1] != y) continue;
            any = true;
            Block block = world.getBlockAt((int) c[0], (int) c[1], (int) c[2]);
            if (!mayBreak(block, job)) continue;
            if (!nearOnlinePlayer(world, (int) c[0], (int) c[1], (int) c[2])) {
                return false;
            }
        }
        return any;
    }

    private static void deferQueuedAtY(Job job, int y) {
        List<long[]> keep = new ArrayList<>();
        while (!job.blocks.isEmpty()) {
            long[] c = job.blocks.pollFirst();
            if (c == null) continue;
            if ((int) c[1] == y) job.deferredFloor.add(c);
            else keep.add(c);
        }
        for (long[] c : keep) job.blocks.addLast(c);
    }

    private void finishDig(Job job, Iterator<Job> it, String reason) {
        job.done = true;
        byHirer.remove(job.hirer);
        it.remove();
        plugin.presence().despawnHire(job.hirer);
        markDirty();
        saveJobs();
        if (reason != null) {
            plugin.getLogger().info("Hire job end · " + job.hirerName + " · " + reason);
        }
    }

    /** Leave a 3×3 floor under anyone standing in the dig so respawn is not a void. */
    private static boolean nearOnlinePlayer(World world, int x, int y, int z) {
        if (world == null) return false;
        for (Player p : world.getPlayers()) {
            if (p == null || !p.isValid() || p.isDead()) continue;
            Location loc = p.getLocation();
            if (loc.getWorld() != world) continue;
            if (Math.abs(loc.getBlockX() - x) > 1) continue;
            if (Math.abs(loc.getBlockZ() - z) > 1) continue;
            int py = loc.getBlockY();
            if (y >= py - 2 && y <= py + 1) return true;
        }
        return false;
    }

    private boolean mayBreak(Block block, Job job) {
        if (block == null || block.getType().isAir()) return false;
        Material type = block.getType();
        if (isProtected(type)) return false;
        if (isContractType(job.type) && isMiningProtected(type)) return false;
        if (isContractType(job.type) && !isNaturalDigTarget(type)) return false;
        if (!ownsWorkColumn(block.getLocation(), job.hirer)) return false;
        if (job.type == JobType.TEARDOWN && !isTeardownTarget(type)) return false;
        return true;
    }

    /** Fluids don't drop via breakNaturally — clear to air so hollows stay dry. */
    private void breakDigBlock(Block block, Job job) {
        if (block == null) return;
        Material type = block.getType();
        if (isFluidClearTarget(type)) {
            block.setType(Material.AIR, false);
            return;
        }
        java.util.Collection<org.bukkit.inventory.ItemStack> drops = block.getDrops();
        block.setType(Material.AIR, false);
        if (job != null && plugin.playerChests() != null && plugin.playerChests().hasChest(job.hirer)) {
            plugin.playerChests().depositOrDrop(job.hirer, drops);
            return;
        }
        if (block.getWorld() != null) {
            for (org.bukkit.inventory.ItemStack drop : drops) {
                if (drop != null && drop.getAmount() > 0) {
                    block.getWorld().dropItemNaturally(block.getLocation(), drop);
                }
            }
        }
    }

    private String requireHireChest(Player player) {
        if (player == null) return "no_chest";
        if (plugin.playerChests() == null || !plugin.playerChests().hasChest(player.getUniqueId())) {
            return "no_chest";
        }
        return null;
    }

    private record CollectResult(List<Block> blocks, int overlapSkipped, int oresAtNeg59, int buildsSkipped) {}

    private CollectResult collectContractBlocks(Player player, Object claim, JobType type) {
        if (type == JobType.CHUNK) {
            return collectClaimCircle(player, claim);
        }
        List<Block> raw = collectBlocks(player, 0, type);
        List<Block> natural = new ArrayList<>();
        int buildsSkipped = 0;
        for (Block b : raw) {
            if (b == null) continue;
            if (isNaturalDigTarget(b.getType())) {
                natural.add(b);
            } else {
                buildsSkipped++;
            }
        }
        return new CollectResult(natural, 0, countOresAtY(natural, ORE_DROP_Y), buildsSkipped);
    }

    private CollectResult collectClaimCircle(Player player, Object claim) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null || claim == null) {
            return new CollectResult(List.of(), 0, 0, 0);
        }
        int cx = claimCenterX(claim);
        int cz = claimCenterZ(claim);
        int radius = Math.max(1, claimRadius(claim));
        int r2 = radius * radius;
        int maxY = loc.getBlockY();
        int minY = world.getMinHeight();
        int volCap = digMaxVolume(plugin.config().hire(), JobType.CHUNK) + 1;
        int overlapSkipped = 0;
        List<int[]> columns = new ArrayList<>();
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                int dx = x - cx;
                int dz = z - cz;
                if (dx * dx + dz * dz > r2) continue;
                Location col = new Location(world, x + 0.5, Math.max(1, MINING_FLOOR_Y), z + 0.5);
                if (!claimContains(claim, col)) continue;
                if (columnCoveredByForeignClaim(col, claim)) {
                    overlapSkipped++;
                    continue;
                }
                columns.add(new int[] {x, z});
            }
        }
        List<Block> out = new ArrayList<>();
        int oresAtNeg59 = 0;
        int buildsSkipped = 0;
        for (int y = maxY; y >= minY; y--) {
            for (int[] col : columns) {
                Block b = world.getBlockAt(col[0], y, col[1]);
                if (b.getType().isAir()) continue;
                if (isProtected(b.getType())) continue;
                if (isMiningProtected(b.getType())) continue;
                if (!isNaturalDigTarget(b.getType())) {
                    buildsSkipped++;
                    continue;
                }
                out.add(b);
                if (y == ORE_DROP_Y && isOreBlock(b.getType())) {
                    oresAtNeg59++;
                }
                if (out.size() > volCap) {
                    return new CollectResult(out, overlapSkipped, oresAtNeg59, buildsSkipped);
                }
            }
        }
        return new CollectResult(out, overlapSkipped, oresAtNeg59, buildsSkipped);
    }

    private List<Block> collectBlocks(Player player, int radius, JobType type) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return List.of();
        int cx = loc.getBlockX();
        int cy = loc.getBlockY();
        int cz = loc.getBlockZ();
        AvaConfig.HireConfig h = plugin.config().hire();
        int minX;
        int maxX;
        int minZ;
        int maxZ;
        int minY;
        int maxY;
        int volCap = digMaxVolume(h, type) + 1;
        if (type == JobType.MINEDOWN) {
            int half = Math.max(0, (h.minedownSize() - 1) / 2);
            minX = cx - half;
            maxX = cx + half;
            minZ = cz - half;
            maxZ = cz + half;
            maxY = cy;
            minY = world.getMinHeight();
        } else if (type == JobType.TEARDOWN) {
            minX = cx - radius;
            maxX = cx + radius;
            minZ = cz - radius;
            maxZ = cz + radius;
            maxY = world.getMaxHeight() - 1;
            minY = Math.max(world.getMinHeight(), cy - h.teardownDepth());
        } else {
            minX = cx - radius;
            maxX = cx + radius;
            minZ = cz - radius;
            maxZ = cz + radius;
            maxY = Math.min(world.getMaxHeight() - 1, cy + 4);
            minY = Math.max(world.getMinHeight(), cy - 32);
        }
        boolean cylinder = type == JobType.MINE || type == JobType.TEARDOWN;
        int r2 = radius * radius;
        List<Block> out = new ArrayList<>();
        for (int y = maxY; y >= minY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (cylinder) {
                        int dx = x - cx;
                        int dz = z - cz;
                        if (dx * dx + dz * dz > r2) continue;
                    }
                    Block b = world.getBlockAt(x, y, z);
                    if (b.getType().isAir()) continue;
                    if (isProtected(b.getType())) continue;
                    if (isContractType(type) && isMiningProtected(b.getType())) continue;
                    if (!ownsWorkColumn(b.getLocation(), player.getUniqueId())) continue;
                    if (type == JobType.TEARDOWN && !isTeardownTarget(b.getType())) continue;
                    out.add(b);
                    if (out.size() > volCap) return out;
                }
            }
        }
        return out;
    }

    private static boolean isDigType(JobType type) {
        return type == JobType.MINE
                || type == JobType.TEARDOWN
                || type == JobType.CHUNK
                || type == JobType.MINEDOWN;
    }

    private static boolean isContractType(JobType type) {
        return type == JobType.CHUNK || type == JobType.MINEDOWN;
    }

    private static double volumePerBlockRate(int blocks) {
        int n = Math.max(1, (int) Math.ceil(Math.max(1, blocks) / (double) VOLUME_RATE_STEP_BLOCKS));
        return round2(VOLUME_RATE_STEP_GOLD * n);
    }

    public static String jobLabel(JobType type) {
        if (type == JobType.CHUNK) return "claim";
        return type == null ? "hire" : type.name().toLowerCase(Locale.ROOT);
    }

    /** Remaining blocks at average (~4/s) and fastest (8/s) dig pace. */
    public static String etaPhrase(int remaining) {
        if (remaining <= 0) return "done";
        int avgSec = (int) Math.ceil(remaining / Math.max(0.5d, DIG_AVG_PER_SECOND));
        int fastSec = (int) Math.ceil(remaining / (double) Math.max(1, DIG_MAX_PER_SECOND));
        return "~" + formatEta(avgSec) + " avg · ~" + formatEta(fastSec) + " max";
    }

    public static String formatEta(int seconds) {
        int s = Math.max(0, seconds);
        if (s < 60) return s + "s";
        int m = s / 60;
        int remS = s % 60;
        if (m < 60) {
            return remS == 0 ? m + "m" : m + "m " + remS + "s";
        }
        int h = m / 60;
        int remM = m % 60;
        return remM == 0 ? h + "h" : h + "h " + remM + "m";
    }

    private static boolean isOreBlock(Material type) {
        if (type == null || type.isAir()) return false;
        if (type == Material.ANCIENT_DEBRIS) return true;
        String n = type.name();
        return n.contains("ORE");
    }

    /** Claim hollow extras: fluids, redstone, and obsidian (refill / leftover junk). */
    private static boolean isFluidClearTarget(Material type) {
        return type == Material.WATER
                || type == Material.LAVA
                || type == Material.BUBBLE_COLUMN;
    }

    private static boolean isClaimHollowClearTarget(Material type) {
        if (type == null) return false;
        if (isFluidClearTarget(type)) return true;
        if (type == Material.OBSIDIAN || type == Material.CRYING_OBSIDIAN) return true;
        String n = type.name();
        // Redstone components — not ores (those go through isOreBlock).
        if (n.contains("ORE")) return false;
        return type == Material.REDSTONE
                || type == Material.REDSTONE_WIRE
                || type == Material.REDSTONE_BLOCK
                || type == Material.REDSTONE_TORCH
                || type == Material.REDSTONE_WALL_TORCH
                || type == Material.REPEATER
                || type == Material.COMPARATOR
                || type == Material.OBSERVER
                || type == Material.TARGET
                || type == Material.DAYLIGHT_DETECTOR
                || type == Material.PISTON
                || type == Material.STICKY_PISTON
                || type == Material.PISTON_HEAD
                || type == Material.MOVING_PISTON
                || n.contains("REDSTONE");
    }

    /**
     * Claim/minedown: only worldgen-ish blocks. Skip player builds (planks, cobble, chests…).
     * Also clears water/lava/redstone/obsidian so hollows stay empty.
     */
    private static boolean isNaturalDigTarget(Material type) {
        if (type == null || type.isAir() || isProtected(type) || isMiningProtected(type)) {
            return false;
        }
        if (isClaimHollowClearTarget(type)) {
            return true;
        }
        if (type == Material.GRASS_BLOCK
                || type == Material.DIRT
                || type == Material.COARSE_DIRT
                || type == Material.PODZOL
                || type == Material.MYCELIUM
                || type == Material.ROOTED_DIRT
                || type == Material.MUD) {
            return true;
        }
        // Ores before player-built name checks (REDSTONE_ORE contains "REDSTONE").
        if (isOreBlock(type)) {
            return true;
        }
        if (isClearlyPlayerBuilt(type)) {
            return false;
        }
        try {
            if (Tag.DIRT.isTagged(type)
                    || Tag.SAND.isTagged(type)
                    || Tag.LEAVES.isTagged(type)
                    || Tag.LOGS.isTagged(type)
                    || Tag.FLOWERS.isTagged(type)
                    || Tag.SAPLINGS.isTagged(type)
                    || Tag.BASE_STONE_OVERWORLD.isTagged(type)
                    || Tag.BASE_STONE_NETHER.isTagged(type)) {
                return true;
            }
        } catch (Throwable ignored) {
            // older tags
        }
        String n = type.name();
        if (n.contains("POLISHED")
                || n.contains("BRICK")
                || n.contains("TILE")
                || n.startsWith("COBBLED_")
                || n.contains("CHISELED")
                || n.contains("CUT_")
                || n.contains("SMOOTH_SANDSTONE")
                || n.contains("SMOOTH_RED_SANDSTONE")) {
            return false;
        }
        return type == Material.STONE
                || type == Material.GRANITE
                || type == Material.DIORITE
                || type == Material.ANDESITE
                || type == Material.DEEPSLATE
                || type == Material.TUFF
                || type == Material.CALCITE
                || type == Material.DRIPSTONE_BLOCK
                || type == Material.POINTED_DRIPSTONE
                || type == Material.GRAVEL
                || type == Material.CLAY
                || type == Material.TERRACOTTA
                || type == Material.SANDSTONE
                || type == Material.RED_SANDSTONE
                || type == Material.NETHERRACK
                || type == Material.END_STONE
                || type == Material.MAGMA_BLOCK
                || type == Material.BASALT
                || type == Material.SMOOTH_BASALT
                || type == Material.BLACKSTONE
                || type == Material.SOUL_SAND
                || type == Material.SOUL_SOIL
                || type == Material.GLOWSTONE
                || type == Material.AMETHYST_BLOCK
                || type == Material.BUDDING_AMETHYST
                || type == Material.MOSS_BLOCK
                || type == Material.MOSS_CARPET
                || type == Material.ICE
                || type == Material.PACKED_ICE
                || type == Material.BLUE_ICE
                || type == Material.SNOW
                || type == Material.SNOW_BLOCK
                || type == Material.POWDER_SNOW
                || type == Material.SHORT_GRASS
                || type == Material.TALL_GRASS
                || type == Material.FERN
                || type == Material.LARGE_FERN
                || type == Material.VINE
                || type == Material.GLOW_LICHEN
                || type == Material.SCULK
                || type == Material.SCULK_VEIN
                || type == Material.SCULK_CATALYST
                || type == Material.SCULK_SENSOR
                || type == Material.SCULK_SHRIEKER
                || type == Material.CRIMSON_NYLIUM
                || type == Material.WARPED_NYLIUM
                || type == Material.MUSHROOM_STEM
                || type == Material.BROWN_MUSHROOM_BLOCK
                || type == Material.RED_MUSHROOM_BLOCK
                || type == Material.BONE_BLOCK
                || n.startsWith("SCULK")
                || n.endsWith("_NYLIUM");
    }

    private static boolean isClearlyPlayerBuilt(Material type) {
        if (type == null) return false;
        try {
            if (Tag.PLANKS.isTagged(type)
                    || Tag.WOOL.isTagged(type)
                    || Tag.BEDS.isTagged(type)
                    || Tag.FENCES.isTagged(type)
                    || Tag.FENCE_GATES.isTagged(type)
                    || Tag.DOORS.isTagged(type)
                    || Tag.TRAPDOORS.isTagged(type)
                    || Tag.SIGNS.isTagged(type)
                    || Tag.BANNERS.isTagged(type)
                    || Tag.RAILS.isTagged(type)
                    || Tag.CANDLES.isTagged(type)
                    || Tag.CROPS.isTagged(type)
                    || Tag.SHULKER_BOXES.isTagged(type)) {
                return true;
            }
        } catch (Throwable ignored) {
            // older tags
        }
        String n = type.name();
        if (n.endsWith("_STAIRS")
                || n.endsWith("_SLAB")
                || n.endsWith("_WALL")
                || n.contains("GLASS")
                || n.contains("CONCRETE")
                || n.contains("TERRACOTTA") && type != Material.TERRACOTTA
                || n.contains("COPPER") && !n.contains("ORE")
                || n.contains("LANTERN")
                || n.contains("TORCH")
                || n.contains("CHEST")
                || n.contains("SHULKER")
                || n.endsWith("_HEAD")
                || n.endsWith("_SKULL")) {
            return true;
        }
        return type == Material.COBBLESTONE
                || type == Material.MOSSY_COBBLESTONE
                || type == Material.STONE_BRICKS
                || type == Material.MOSSY_STONE_BRICKS
                || type == Material.BRICKS
                || type == Material.MUD_BRICKS
                || type == Material.FARMLAND
                || type == Material.CRAFTING_TABLE
                || type == Material.FURNACE
                || type == Material.BLAST_FURNACE
                || type == Material.SMOKER
                || type == Material.BARREL
                || type == Material.HOPPER
                || type == Material.DROPPER
                || type == Material.DISPENSER
                || type == Material.LECTERN
                || type == Material.LOOM
                || type == Material.SMITHING_TABLE
                || type == Material.ANVIL
                || type == Material.CHIPPED_ANVIL
                || type == Material.DAMAGED_ANVIL
                || type == Material.GRINDSTONE
                || type == Material.STONECUTTER
                || type == Material.CARTOGRAPHY_TABLE
                || type == Material.COMPOSTER
                || type == Material.CAMPFIRE
                || type == Material.SOUL_CAMPFIRE
                || type == Material.SCAFFOLDING
                || type == Material.LADDER
                || type == Material.IRON_BARS
                || "CHAIN".equals(n)
                || "IRON_CHAIN".equals(n)
                || type == Material.ENCHANTING_TABLE
                || type == Material.BREWING_STAND
                || type == Material.CAULDRON
                || type == Material.BELL
                || type == Material.JUKEBOX
                || type == Material.NOTE_BLOCK
                || type == Material.LIGHTNING_ROD
                || type == Material.END_ROD
                || type == Material.BOOKSHELF
                || type == Material.CHISELED_BOOKSHELF
                || type == Material.MELON
                || type == Material.PUMPKIN
                || type == Material.CARVED_PUMPKIN
                || type == Material.JACK_O_LANTERN
                || type == Material.SUGAR_CANE
                || type == Material.BAMBOO
                || type == Material.BAMBOO_BLOCK
                || type == Material.HAY_BLOCK;
    }

    private static int countOresAtY(List<Block> blocks, int y) {
        if (blocks == null || blocks.isEmpty()) return 0;
        int n = 0;
        for (Block b : blocks) {
            if (b != null && b.getY() == y && isOreBlock(b.getType())) {
                n++;
            }
        }
        return n;
    }

    private static double digBase(AvaConfig.HireConfig h, JobType type) {
        return switch (type) {
            case TEARDOWN -> h.teardownBase();
            case CHUNK, MINEDOWN -> 0;
            default -> h.mineBase();
        };
    }

    private static double digPerBlock(AvaConfig.HireConfig h, JobType type) {
        return switch (type) {
            case TEARDOWN -> h.teardownPerBlock();
            case CHUNK -> h.chunkPerBlock();
            case MINEDOWN -> h.minedownPerBlock();
            default -> h.minePerBlock();
        };
    }

    private static int digMaxVolume(AvaConfig.HireConfig h, JobType type) {
        return switch (type) {
            case CHUNK -> h.chunkMaxVolume();
            case MINEDOWN -> h.minedownMaxVolume();
            default -> h.maxVolume();
        };
    }

    private static boolean isTeardownTarget(Material type) {
        if (type.isAir()) return false;
        String n = type.name();
        if (Tag.LOGS.isTagged(type) || Tag.LEAVES.isTagged(type)) return true;
        if (Tag.PLANKS.isTagged(type) || Tag.WOODEN_DOORS.isTagged(type) || Tag.FENCES.isTagged(type)) return true;
        if (Tag.WOOL.isTagged(type) || Tag.BEDS.isTagged(type)) return true;
        if (n.contains("GLASS") || n.contains("TERRACOTTA") || n.contains("CONCRETE")) return true;
        if (n.endsWith("_STAIRS") || n.endsWith("_SLAB") || n.endsWith("_WALL")) return true;
        if (type == Material.COBBLESTONE
                || type == Material.STONE_BRICKS
                || type == Material.BRICKS
                || type == Material.DIRT
                || type == Material.GRASS_BLOCK
                || type == Material.PODZOL
                || type == Material.COARSE_DIRT
                || type == Material.SAND
                || type == Material.GRAVEL
                || type == Material.TORCH
                || type == Material.LANTERN
                || type == Material.CRAFTING_TABLE
                || type == Material.FURNACE
                || type == Material.CHEST
                || type == Material.BARREL) {
            return true;
        }
        // Player-ish builds: skip deep ores / ancient debris / bedrock already protected
        if (n.contains("ORE") || n.contains("DEEPSLATE") || n.contains("NETHERRACK")) return false;
        // Surface plants / snow
        if (Tag.FLOWERS.isTagged(type) || Tag.SAPLINGS.isTagged(type) || type == Material.SNOW
                || type == Material.SNOW_BLOCK || type == Material.SHORT_GRASS || type == Material.TALL_GRASS
                || type == Material.FERN || type == Material.LARGE_FERN || type == Material.VINE) {
            return true;
        }
        return false;
    }

    /** Extra skip list for chunk/minedown contracts — never eat claim storage or beds. */
    private static boolean isMiningProtected(Material type) {
        if (type == Material.CHEST
                || type == Material.TRAPPED_CHEST
                || type == Material.ENDER_CHEST
                || type == Material.BARREL
                || type == Material.HOPPER
                || type == Material.DROPPER
                || type == Material.DISPENSER
                || type == Material.FURNACE
                || type == Material.BLAST_FURNACE
                || type == Material.SMOKER
                || type == Material.BEACON
                || type == Material.RESPAWN_ANCHOR
                || type == Material.CRAFTER) {
            return true;
        }
        try {
            if (Tag.BEDS.isTagged(type) || Tag.SHULKER_BOXES.isTagged(type)) return true;
        } catch (Throwable ignored) {
            // older tags
        }
        String n = type.name();
        return n.contains("CHEST") || n.endsWith("_SHULKER_BOX");
    }

    private static boolean isProtected(Material type) {
        return type == Material.BEDROCK
                || type == Material.BARRIER
                || type == Material.COMMAND_BLOCK
                || type == Material.CHAIN_COMMAND_BLOCK
                || type == Material.REPEATING_COMMAND_BLOCK
                || type == Material.STRUCTURE_BLOCK
                || type == Material.STRUCTURE_VOID
                || type == Material.JIGSAW
                || type == Material.END_PORTAL
                || type == Material.END_PORTAL_FRAME
                || type == Material.NETHER_PORTAL
                || type == Material.SPAWNER
                || type == Material.TRIAL_SPAWNER
                || type == Material.VAULT
                || type == Material.REINFORCED_DEEPSLATE;
    }

    private void spawnHireBody(Player player, Location at, boolean follow) {
        if (!plugin.config().presence().enabled() || at == null) return;
        UUID hirerId = player != null ? player.getUniqueId() : null;
        if (hirerId == null) return;
        String name = player.getName();
        plugin.presence().spawnHire(hirerId, at, name);
        if (follow && player.isOnline()) {
            plugin.presence().follow(hirerId, player);
        }
    }

    private void spawnHireAtWork(Player player, Job job) {
        Location at = digStandLocation(job, player != null ? player.getWorld() : null);
        if (at == null && player != null) at = player.getLocation();
        spawnHireBody(player, at, false);
        if (at != null) {
            plugin.presence().appearWorking(player.getUniqueId(), at);
        }
    }

    private void showHireWorking(Job job, World world, Location focus) {
        if (job == null || !plugin.config().presence().enabled()) return;
        Location at = focus;
        if (at == null) {
            at = digStandLocation(job, world);
        }
        if (at == null) return;
        plugin.presence().appearWorking(job.hirer, at);
    }

    private static Location digStandLocation(Job job, World world) {
        if (job == null) return null;
        if (world == null && job.workAnchor != null) {
            world = job.workAnchor.getWorld();
        }
        if (world != null) {
            long[] c = peekQueuedAtY(job.blocks, job.digY != Integer.MIN_VALUE ? job.digY : Integer.MAX_VALUE);
            if (c == null && !job.blocks.isEmpty()) {
                c = job.blocks.peek();
            }
            if (c == null && !job.deferredFloor.isEmpty()) {
                c = job.deferredFloor.get(0);
            }
            if (c != null) {
                return new Location(world, c[0] + 0.5, c[1], c[2] + 0.5);
            }
        }
        return job.workAnchor;
    }

    private static long[] peekQueuedAtY(Deque<long[]> blocks, int y) {
        if (blocks == null || blocks.isEmpty()) return null;
        if (y == Integer.MAX_VALUE || y == Integer.MIN_VALUE) {
            return blocks.peek();
        }
        for (long[] c : blocks) {
            if (c != null && (int) c[1] == y) return c;
        }
        return null;
    }

    public void onJoin(Player player) {
        if (player == null) return;
        Job job = byHirer.get(player.getUniqueId());
        if (job == null || job.done) return;
        String detail;
        if (job.endsAt > 0) {
            long mins = Math.max(0L, (job.endsAt - System.currentTimeMillis() + 59_999L) / 60_000L);
            detail = mins + "m left";
        } else {
            detail = job.broken + " cleared · " + job.blocks.size() + " left · " + etaPhrase(job.blocks.size());
        }
        player.sendMessage(plugin.colorize(plugin.config().prefix()
                + "&aHire resumed &8· &f" + jobLabel(job.type)
                + " &8· &7" + detail));
        if (job.type == JobType.SIDEKICK) {
            spawnHireBody(player, player.getLocation(), true);
        } else if (isDigType(job.type)) {
            spawnHireAtWork(player, job);
        } else {
            Location at = job.workAnchor != null ? job.workAnchor : player.getLocation();
            spawnHireBody(player, at, false);
            if (job.type == JobType.HOUR && plugin.config().presence().enabled()) {
                plugin.presence().parkAndWork(job.hirer, at);
            }
        }
    }

    private static String requireSurfaceStart(Player player) {
        if (player == null || player.getLocation().getBlockY() < MINING_FLOOR_Y) {
            return "below_mining_floor";
        }
        return null;
    }

    /** Minedown: every XZ column must sit in the hirer's claim or Voronoi territory. */
    private String requireFullPlotInWorkLand(Player player, JobType type) {
        if (type != JobType.MINEDOWN) {
            return null;
        }
        World world = player.getWorld();
        if (world == null) {
            return "not_in_claim";
        }
        int half = Math.max(0, (plugin.config().hire().minedownSize() - 1) / 2);
        int cx = player.getLocation().getBlockX();
        int cz = player.getLocation().getBlockZ();
        int minX = cx - half;
        int maxX = cx + half;
        int minZ = cz - half;
        int maxZ = cz + half;
        UUID playerId = player.getUniqueId();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Location probe = new Location(world, x + 0.5, Math.max(1, MINING_FLOOR_Y), z + 0.5);
                if (!ownsWorkColumn(probe, playerId)) {
                    return "plot_not_fully_in_claim";
                }
            }
        }
        return null;
    }

    private String requireNearClaimRing(Player player, Object claim) {
        if (player == null || claim == null) {
            return "not_in_claim";
        }
        int cx = claimCenterX(claim);
        int cz = claimCenterZ(claim);
        int radius = Math.max(1, claimRadius(claim));
        Location loc = player.getLocation();
        double dist = Math.hypot(loc.getX() - (cx + 0.5), loc.getZ() - (cz + 0.5));
        boolean nearCenter = dist <= RING_STAND_BLOCKS + 0.01;
        boolean nearRim = Math.abs(dist - radius) <= RING_STAND_BLOCKS + 0.01;
        if (nearCenter || nearRim) {
            return null;
        }
        return "stand_near_claim_ring";
    }

    /** Skip other players' claims only — same-owner parent/expansion overlap is the full circle. */
    private boolean columnCoveredByForeignClaim(Location col, Object thisClaim) {
        if (col == null || thisClaim == null) return false;
        UUID owner = AvaClaimCrew.claimOwnerId(thisClaim);
        for (Object other : allClaims()) {
            if (other == null || sameClaimKey(other, thisClaim)) continue;
            UUID otherOwner = AvaClaimCrew.claimOwnerId(other);
            if (owner != null && owner.equals(otherOwner)) continue;
            if (claimContains(other, col)) {
                return true;
            }
        }
        return false;
    }

    private List<Object> allClaims() {
        List<Object> out = new ArrayList<>();
        try {
            Object service = claimsService();
            if (service == null) return out;
            Object raw = service.getClass().getMethod("allClaims").invoke(service);
            if (raw instanceof Iterable<?> it) {
                for (Object o : it) {
                    if (o != null) out.add(o);
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static boolean sameClaimKey(Object a, Object b) {
        if (a == null || b == null) return false;
        if (a == b) return true;
        try {
            Object ka = a.getClass().getMethod("key").invoke(a);
            Object kb = b.getClass().getMethod("key").invoke(b);
            if (ka == null || kb == null) return false;
            return String.valueOf(ka).equals(String.valueOf(kb))
                    || ka.equals(kb);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int claimCenterX(Object claim) {
        try {
            Object key = claim.getClass().getMethod("key").invoke(claim);
            Object x = key.getClass().getMethod("x").invoke(key);
            return x instanceof Number n ? n.intValue() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int claimCenterZ(Object claim) {
        try {
            Object key = claim.getClass().getMethod("key").invoke(claim);
            Object z = key.getClass().getMethod("z").invoke(key);
            return z instanceof Number n ? n.intValue() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int claimRadius(Object claim) {
        try {
            Object v = claim.getClass().getMethod("radiusBlocks").invoke(claim);
            return v instanceof Number n ? Math.max(1, n.intValue()) : 16;
        } catch (Throwable t) {
            return 16;
        }
    }

    private Object claimsService() {
        try {
            org.bukkit.plugin.Plugin claims = Bukkit.getPluginManager().getPlugin("Root-Claims");
            if (claims == null || !claims.isEnabled()) return null;
            return claims.getClass().getMethod("claims").invoke(claims);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Claim at feet, else Voronoi territory the player can stand in. */
    private Object workLandAt(Player player) {
        if (player == null) return null;
        Object land = workLandAtColumn(player.getLocation());
        if (land != null) return land;
        Object claim = claimAt(player.getLocation());
        if (claim != null) return claim;
        return com.rootrecord.minecraft.rootavacore.AvaLandAccess.territoryAt(player);
    }

    private Object workLandAtColumn(Location loc) {
        Object service = claimsService();
        if (service == null || loc == null) return null;
        try {
            try {
                return service.getClass().getMethod("workLandAtColumn", Location.class).invoke(service, loc);
            } catch (NoSuchMethodException ignored) {
                Object claim = claimAtColumn(loc);
                if (claim != null) return claim;
                Location probe = loc.clone();
                probe.setY(Math.max(1, MINING_FLOOR_Y));
                try {
                    return service.getClass().getMethod("territoryAt", Location.class).invoke(service, probe);
                } catch (NoSuchMethodException ignored2) {
                    return null;
                }
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean ownsWorkColumn(Location loc, UUID playerId) {
        return ownsLand(workLandAtColumn(loc), playerId);
    }

    private boolean ownsLand(Object land, UUID playerId) {
        return com.rootrecord.minecraft.rootavacore.AvaLandAccess.isOwner(land, playerId);
    }

    /** Soft Root-Claims: claim at location (Y-aware — underworld returns null). */
    private Object claimAt(Location loc) {
        try {
            org.bukkit.plugin.Plugin claims = Bukkit.getPluginManager().getPlugin("Root-Claims");
            if (claims == null || !claims.isEnabled()) return null;
            Object service = claims.getClass().getMethod("claims").invoke(claims);
            return service.getClass().getMethod("claimAt", Location.class).invoke(service, loc);
        } catch (Throwable t) {
            return null;
        }
    }

    /** XZ column of a surface claim — used so dig jobs can continue below Y 0. */
    private Object claimAtColumn(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        try {
            org.bukkit.plugin.Plugin claims = Bukkit.getPluginManager().getPlugin("Root-Claims");
            if (claims == null || !claims.isEnabled()) return null;
            Object service = claims.getClass().getMethod("claims").invoke(claims);
            try {
                return service.getClass().getMethod("claimAtColumn", Location.class).invoke(service, loc);
            } catch (NoSuchMethodException ignored) {
                Location probe = loc.clone();
                probe.setY(Math.max(1, MINING_FLOOR_Y));
                return service.getClass().getMethod("claimAt", Location.class).invoke(service, probe);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean claimContains(Object claim, Location loc) {
        if (claim == null || loc == null || loc.getWorld() == null) return false;
        try {
            Object v = claim.getClass()
                    .getMethod("coversColumn", String.class, int.class, int.class)
                    .invoke(claim, loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ());
            return Boolean.TRUE.equals(v);
        } catch (Throwable ignored) {
            // fall through
        }
        try {
            Location probe = loc;
            if (loc.getBlockY() < MINING_FLOOR_Y) {
                probe = loc.clone();
                probe.setY(Math.max(1, MINING_FLOOR_Y));
            }
            Object v = claim.getClass().getMethod("contains", Location.class).invoke(claim, probe);
            return Boolean.TRUE.equals(v);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean canManageClaim(Object claim, UUID playerId) {
        if (claim == null || playerId == null) return false;
        try {
            Object v = claim.getClass().getMethod("canManage", UUID.class).invoke(claim, playerId);
            return Boolean.TRUE.equals(v);
        } catch (Throwable t) {
            return false;
        }
    }

    private void markDirty() {
        dirty = true;
    }

    private void flushIfDirty() {
        if (dirty) {
            saveJobs();
        }
    }

    private void ensureLoaded() {
        if (persistReady) return;
        persistReady = true;
        loadRates();
        loadJobs();
    }

    private File ratesFile() {
        return RootRecordFolders.configFile(plugin, "ava-hire-rates.yml");
    }

    private int contractUses(JobType type, UUID playerId) {
        if (playerId == null) return 0;
        if (type == JobType.CHUNK) return chunkUses.getOrDefault(playerId, 0);
        if (type == JobType.MINEDOWN) return minedownUses.getOrDefault(playerId, 0);
        return 0;
    }

    private void incrementContractUses(JobType type, UUID playerId) {
        if (playerId == null || !isContractType(type)) return;
        if (type == JobType.CHUNK) {
            chunkUses.merge(playerId, 1, Integer::sum);
        } else {
            minedownUses.merge(playerId, 1, Integer::sum);
        }
        saveRates();
    }

    private static int contractScale(int uses) {
        return 1 << Math.min(16, Math.max(0, uses));
    }

    private void loadRates() {
        File file = ratesFile();
        if (!file.isFile()) return;
        try {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            loadUseMap(yml.getConfigurationSection("chunk"), chunkUses);
            loadUseMap(yml.getConfigurationSection("minedown"), minedownUses);
        } catch (Throwable t) {
            plugin.getLogger().warning("Hire rates load failed: " + t.getMessage());
        }
    }

    private void saveRates() {
        try {
            YamlConfiguration yml = new YamlConfiguration();
            writeUseMap(yml, "chunk", chunkUses);
            writeUseMap(yml, "minedown", minedownUses);
            yml.save(ratesFile());
        } catch (Throwable t) {
            plugin.getLogger().warning("Hire rates save failed: " + t.getMessage());
        }
    }

    private static void loadUseMap(ConfigurationSection sec, Map<UUID, Integer> into) {
        if (sec == null || into == null) return;
        into.clear();
        for (String key : sec.getKeys(false)) {
            UUID id = parseUuid(key);
            if (id == null) continue;
            into.put(id, Math.max(0, sec.getInt(key, 0)));
        }
    }

    private static void writeUseMap(YamlConfiguration yml, String path, Map<UUID, Integer> from) {
        for (Map.Entry<UUID, Integer> e : from.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue() <= 0) continue;
            yml.set(path + "." + e.getKey(), e.getValue());
        }
    }

    private File jobsFile() {
        return RootRecordFolders.configFile(plugin, "ava-hire-jobs.yml");
    }

    private File blocksDir() {
        return new File(RootRecordFolders.dir(plugin), "ava-hire-blocks");
    }

    private File blocksFile(UUID jobId) {
        return new File(blocksDir(), jobId + ".txt");
    }

    private void saveJobs() {
        ensureLoaded();
        dirty = false;
        try {
            blocksDir().mkdirs();
            YamlConfiguration yml = new YamlConfiguration();
            yml.set("saved-at", System.currentTimeMillis());
            int i = 0;
            Set<String> keep = new HashSet<>();
            for (Job job : List.copyOf(active)) {
                if (job == null || job.done) continue;
                String p = "jobs." + i;
                yml.set(p + ".id", job.id.toString());
                yml.set(p + ".hirer", job.hirer.toString());
                yml.set(p + ".hirer-name", job.hirerName);
                yml.set(p + ".type", job.type.name());
                yml.set(p + ".charged", job.charged);
                yml.set(p + ".started-at", job.startedAt);
                yml.set(p + ".ends-at", job.endsAt);
                yml.set(p + ".world", job.world);
                yml.set(p + ".broken", job.broken);
                if (job.standY != Integer.MIN_VALUE) {
                    yml.set(p + ".stand-y", job.standY);
                }
                if (job.digY != Integer.MIN_VALUE) {
                    yml.set(p + ".dig-y", job.digY);
                }
                yml.set(p + ".ores-neg59", job.oresAtNeg59);
                if (job.claimOwnerId != null) {
                    yml.set(p + ".claim-owner", job.claimOwnerId.toString());
                }
                if (job.workAnchor != null) {
                    World aw = job.workAnchor.getWorld();
                    yml.set(p + ".anchor-world", aw != null ? aw.getName() : job.world);
                    yml.set(p + ".anchor-x", job.workAnchor.getX());
                    yml.set(p + ".anchor-y", job.workAnchor.getY());
                    yml.set(p + ".anchor-z", job.workAnchor.getZ());
                }
                if (isDigType(job.type) && !job.blocks.isEmpty()) {
                    File bf = blocksFile(job.id);
                    writeBlocks(bf, job.blocks);
                    keep.add(bf.getName());
                }
                i++;
            }
            yml.set("count", i);
            yml.save(jobsFile());
            File[] extras = blocksDir().listFiles((d, n) -> n.endsWith(".txt"));
            if (extras != null) {
                for (File f : extras) {
                    if (!keep.contains(f.getName()) && !f.delete()) {
                        plugin.getLogger().fine("Could not delete stale hire blocks " + f.getName());
                    }
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Hire persist save failed: " + t.getMessage());
        }
    }

    private void loadJobs() {
        File file = jobsFile();
        if (!file.isFile()) return;
        try {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            long savedAt = yml.getLong("saved-at", 0L);
            long freeze = savedAt > 0 ? Math.max(0L, System.currentTimeMillis() - savedAt) : 0L;
            ConfigurationSection root = yml.getConfigurationSection("jobs");
            if (root == null) return;
            int loaded = 0;
            for (String key : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) continue;
                JobType type;
                try {
                    type = JobType.valueOf(s.getString("type", ""));
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                UUID hirer = parseUuid(s.getString("hirer"));
                if (hirer == null || byHirer.containsKey(hirer)) continue;
                Job job = new Job(
                        parseUuid(s.getString("id")),
                        hirer,
                        s.getString("hirer-name", "player"),
                        type,
                        s.getDouble("charged", 0),
                        s.getLong("started-at", System.currentTimeMillis()),
                        s.getString("world", "world"));
                job.endsAt = s.getLong("ends-at", 0L);
                if (job.endsAt > 0 && freeze > 0) {
                    job.endsAt += freeze;
                }
                job.broken = s.getInt("broken", 0);
                if (s.isSet("stand-y")) {
                    job.standY = s.getInt("stand-y");
                }
                if (s.isSet("dig-y")) {
                    job.digY = s.getInt("dig-y");
                }
                job.oresAtNeg59 = Math.max(0, s.getInt("ores-neg59", 0));
                job.claimOwnerId = parseUuid(s.getString("claim-owner"));
                if (s.isSet("anchor-x")) {
                    World w = Bukkit.getWorld(s.getString("anchor-world", job.world));
                    if (w != null) {
                        job.workAnchor = new Location(
                                w, s.getDouble("anchor-x"), s.getDouble("anchor-y"), s.getDouble("anchor-z"));
                    }
                }
                if (isDigType(type)) {
                    loadBlocks(blocksFile(job.id), job.blocks);
                    if (job.blocks.isEmpty()
                            && job.digY == Integer.MIN_VALUE
                            && job.standY == Integer.MIN_VALUE) {
                        continue;
                    }
                } else if (type == JobType.HOUR) {
                    if (job.endsAt > 0 && job.endsAt <= System.currentTimeMillis()) continue;
                    UUID owner = job.claimOwnerId != null ? job.claimOwnerId : job.hirer;
                    job.crew = new AvaClaimCrew(plugin, plugin.storage(), job.hirer, owner);
                } else if (type == JobType.SIDEKICK) {
                    if (job.endsAt > 0 && job.endsAt <= System.currentTimeMillis()) continue;
                }
                active.add(job);
                byHirer.put(hirer, job);
                resumePresence(job);
                loaded++;
            }
            if (loaded > 0) {
                plugin.getLogger().info("Resumed " + loaded + " hire job(s) after restart.");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Hire persist load failed: " + t.getMessage());
        }
    }

    private void resumePresence(Job job) {
        if (job == null || !plugin.config().presence().enabled()) return;
        Location at = job.workAnchor;
        if (at == null) {
            World w = Bukkit.getWorld(job.world);
            if (w != null && !job.blocks.isEmpty()) {
                long[] c = job.blocks.peek();
                at = new Location(w, c[0] + 0.5, c[1], c[2] + 0.5);
            }
        }
        if (at == null) return;
        plugin.presence().spawnHire(job.hirer, at, job.hirerName);
        Player p = Bukkit.getPlayer(job.hirer);
        if (job.type == JobType.SIDEKICK && p != null) {
            plugin.presence().follow(job.hirer, p);
        } else if (job.type == JobType.HOUR) {
            plugin.presence().parkAndWork(job.hirer, at);
        } else if (isDigType(job.type)) {
            Location dig = digStandLocation(job, at.getWorld());
            plugin.presence().appearWorking(job.hirer, dig != null ? dig : at);
        }
    }

    private static void writeBlocks(File file, Deque<long[]> blocks) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            for (long[] c : blocks) {
                if (c == null || c.length < 3) continue;
                w.write(c[0] + " " + c[1] + " " + c[2]);
                w.newLine();
            }
        }
    }

    private static void loadBlocks(File file, Deque<long[]> into) {
        if (file == null || !file.isFile() || into == null) return;
        try (BufferedReader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] p = line.split("[ ,]+");
                if (p.length < 3) continue;
                try {
                    into.add(new long[] {
                            Long.parseLong(p[0]), Long.parseLong(p[1]), Long.parseLong(p[2])});
                } catch (NumberFormatException ignored) {
                    // skip bad row
                }
            }
        } catch (IOException ignored) {
            // missing/unreadable blocks file → treat as empty
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String walletFmt(Player player) {
        RootMcEconomyService eco = RootMcEconomyResolver.resolve(plugin);
        if (eco == null) return "?";
        return fmt(eco.balance(player.getUniqueId()));
    }

    private static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.001) return String.valueOf((long) Math.rint(v));
        return String.format(Locale.US, "%.2f", v);
    }
}
