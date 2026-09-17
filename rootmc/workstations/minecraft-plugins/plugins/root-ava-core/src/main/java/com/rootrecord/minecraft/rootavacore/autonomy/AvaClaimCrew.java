package com.rootrecord.minecraft.rootavacore.autonomy;

import com.rootrecord.minecraft.rootavacore.AvaConfig;
import com.rootrecord.minecraft.rootavacore.RootAvaCorePlugin;
import com.rootrecord.minecraft.rootavacore.presence.AvaPresenceService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One gather→chests→deposit→build→farm→vacuum loop, scoped to a claim owner + Mannequin crew.
 */
public final class AvaClaimCrew {

    public enum Phase {
        GATHER,
        ENSURE_CHESTS,
        DEPOSIT,
        BUILD,
        FARM,
        VACUUM,
        PAUSED
    }

    private static final List<String> DEFAULT_CYCLE =
            List.of("gather", "chests", "deposit", "build", "farm", "vacuum");

    private final RootAvaCorePlugin plugin;
    private final AvaStorageService storage;
    private final AvaHomesteadBuilder builder;
    private final AvaFarmService farm;
    private final UUID crewId;
    private final UUID claimOwnerId;
    private final boolean homeCrew;
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.GATHER);
    private final AtomicInteger goalIndex = new AtomicInteger(0);
    private final AtomicInteger gathered = new AtomicInteger(0);
    private final AtomicInteger deposited = new AtomicInteger(0);
    private final AtomicInteger chestsPlaced = new AtomicInteger(0);
    private final AtomicInteger built = new AtomicInteger(0);
    private volatile String lastNote = "boot";

    public AvaClaimCrew(RootAvaCorePlugin plugin, AvaStorageService storage, UUID crewId, UUID claimOwnerId) {
        this.plugin = plugin;
        this.storage = storage;
        this.crewId = crewId == null ? AvaPresenceService.HOME_CREW : crewId;
        this.claimOwnerId = claimOwnerId;
        this.homeCrew = AvaPresenceService.HOME_CREW.equals(this.crewId);
        this.builder = new AvaHomesteadBuilder(plugin, storage, claimOwnerId, this.crewId);
        this.farm = new AvaFarmService(plugin, storage);
    }

    public AvaHomesteadBuilder builder() {
        return builder;
    }

    public UUID crewId() {
        return crewId;
    }

    public UUID claimOwnerId() {
        return claimOwnerId;
    }

    public Phase phase() {
        return phase.get();
    }

    public String lastNote() {
        return lastNote;
    }

    public String statusLine() {
        return phase.get().name().toLowerCase(Locale.ROOT)
                + " · gathered " + gathered.get()
                + " · deposited " + deposited.get()
                + " · built " + built.get()
                + " · chests+" + chestsPlaced.get()
                + " · " + lastNote
                + " · homestead " + builder.statusLine();
    }

    public void setPaused(boolean on) {
        paused.set(on);
        phase.set(on ? Phase.PAUSED : Phase.GATHER);
        lastNote = on ? "paused" : "resumed";
    }

    public boolean paused() {
        return paused.get();
    }

    public void stop() {
        stopped.set(true);
    }

    public boolean stopped() {
        return stopped.get();
    }

    /** Home Ava: tick every claim owned by {@link #claimOwnerId}. */
    public void tickOwned() {
        if (!alive()) return;
        List<Object> claims = ownedClaims(claimOwnerId);
        if (claims.isEmpty()) {
            lastNote = "no_claims";
            if (homeCrew) {
                try {
                    org.bukkit.plugin.Plugin rc = Bukkit.getPluginManager().getPlugin("Root-Claims");
                    if (rc != null && rc.isEnabled()) {
                        rc.getClass().getMethod("ensureAvaSpawnClaim").invoke(rc);
                        lastNote = "no_claims · requested Ava spawn grant";
                    }
                } catch (Throwable ignored) {
                }
            }
            return;
        }
        int idx = goalIndex.getAndIncrement() % DEFAULT_CYCLE.size();
        Object claim = claims.get(Math.floorMod(idx, claims.size()));
        Location anchor = claimAnchor(claim);
        if (anchor == null) {
            lastNote = "bad_anchor";
            return;
        }
        runStep(DEFAULT_CYCLE.get(idx), claim, anchor);
        parkPresence(anchor);
    }

    /** Hire hour: one specific claim, same cycle. */
    public void tickClaim(Object claim, Location anchor) {
        if (!alive()) return;
        if (claim == null || anchor == null || anchor.getWorld() == null) {
            lastNote = "bad_claim";
            return;
        }
        int idx = goalIndex.getAndIncrement() % DEFAULT_CYCLE.size();
        runStep(DEFAULT_CYCLE.get(idx), claim, anchor);
        parkPresence(anchor);
    }

    private boolean alive() {
        if (stopped.get() || paused.get()) {
            phase.set(Phase.PAUSED);
            lastNote = paused.get() ? "paused" : "stopped";
            return false;
        }
        return true;
    }

    private void runStep(String step, Object claim, Location anchor) {
        AvaConfig.AutonomyConfig cfg = plugin.config().autonomy();
        switch (step) {
            case "gather", "mine" -> doGather(claim, anchor, cfg);
            case "chests" -> doEnsureChests(claim, anchor, cfg);
            case "deposit" -> doDeposit(claim, anchor, cfg);
            case "build" -> doBuild(claim, anchor, cfg);
            case "farm" -> doFarm(claim, anchor, cfg);
            default -> doVacuum(claim, anchor, cfg);
        }
    }

    private void parkPresence(Location anchor) {
        if (!plugin.config().presence().enabled()) return;
        AvaPresenceService presence = plugin.presence();
        if (presence == null || anchor == null || anchor.getWorld() == null) return;
        try {
            if (!presence.isSpawned(crewId)) {
                presence.parkAndWork(crewId, anchor);
                if (!presence.isSpawned(crewId)) return;
            }
            if (presence.mode() == AvaPresenceService.Mode.FOLLOW && homeCrew) return;
            Location here = presence.bodyLocation(crewId);
            if (here == null
                    || here.getWorld() == null
                    || !here.getWorld().equals(anchor.getWorld())
                    || here.distanceSquared(anchor) > 96.0) {
                presence.parkAndWork(crewId, anchor);
            } else if (presence.mode() == AvaPresenceService.Mode.WANDER && homeCrew) {
                presence.parkAndWork(crewId, here);
            }
        } catch (Throwable t) {
            plugin.getLogger().fine("park presence: " + t.getMessage());
        }
    }

    private void doFarm(Object claim, Location anchor, AvaConfig.AutonomyConfig cfg) {
        phase.set(Phase.FARM);
        List<Block> chests = storage.findAvaChests(anchor, Math.max(cfg.gatherRadius(), 16));
        int speed = plugin.playerChests() != null ? plugin.playerChests().getSpeedLevel(crewId) : 0;
        lastNote = farm.tick(claim, anchor, chests, Math.max(4, cfg.gatherBlocksPerTick()) + speed, crewId);
    }

    private void doBuild(Object claim, Location anchor, AvaConfig.AutonomyConfig cfg) {
        phase.set(Phase.BUILD);
        if (!cfg.buildEnabled()) {
            lastNote = "build_disabled";
            return;
        }
        List<Block> chests = storage.findAvaChests(anchor, Math.max(cfg.gatherRadius(), 16));
        int placed = builder.tick(claim, anchor, chests, cfg.buildBlocksPerTick());
        built.addAndGet(placed);
        lastNote = builder.lastNote();
    }

    private void doGather(Object claim, Location anchor, AvaConfig.AutonomyConfig cfg) {
        phase.set(Phase.GATHER);
        World world = anchor.getWorld();
        if (world == null) return;
        int budget = cfg.gatherBlocksPerTick();
        int claimR = claimRadius(claim);
        int buf = cfg.workTerritory() ? plugin.config().powers().territoryBufferBlocks() : 0;
        int r = Math.min(cfg.gatherRadius() + buf, claimR + buf);
        int cx = anchor.getBlockX();
        int cy = anchor.getBlockY();
        int cz = anchor.getBlockZ();
        List<Block> chests = storage.findAvaChests(anchor, Math.max(r, 12));
        List<Block> candidates = new ArrayList<>();
        for (int x = cx - r; x <= cx + r && candidates.size() < 400; x++) {
            for (int z = cz - r; z <= cz + r && candidates.size() < 400; z++) {
                if ((x - cx) * (x - cx) + (z - cz) * (z - cz) > r * r) continue;
                for (int y = Math.max(world.getMinHeight(), cy - 40); y <= Math.min(world.getMaxHeight() - 1, cy + 6); y++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (!isGatherTarget(b.getType())) continue;
                    if (!inWorkZone(claim, b.getLocation(), cfg)) continue;
                    candidates.add(b);
                }
            }
        }
        candidates.sort((a, b) -> Integer.compare(gatherPriority(b.getType()), gatherPriority(a.getType())));
        List<Block> batch = new ArrayList<>(candidates.subList(0, Math.min(budget, candidates.size())));
        if (batch.isEmpty()) {
            lastNote = "gather idle — nothing in work zone";
            return;
        }
        long step = Math.max(8L, (cfg.tickSeconds() * 20L) / Math.max(1, batch.size() + 1));
        for (int i = 0; i < batch.size(); i++) {
            Block b = batch.get(i);
            final int idx = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (stopped.get() || paused.get()) return;
                if (!b.getWorld().equals(world) || !isGatherTarget(b.getType())) return;
                if (!inWorkZone(claim, b.getLocation(), cfg)) return;
                AvaPresenceService presence = plugin.presence();
                if (presence != null) {
                    presence.appearWorking(crewId, b.getLocation());
                }
                Collection<ItemStack> drops = b.getDrops(new ItemStack(Material.NETHERITE_PICKAXE));
                b.setType(Material.AIR);
                for (ItemStack drop : drops) {
                    stashOrDrop(drop, chests, anchor);
                }
                gathered.incrementAndGet();
                if (homeCrew && idx == batch.size() - 1 && plugin.godLoadout() != null) {
                    plugin.godLoadout().applyNow();
                }
            }, i * step);
        }
        lastNote = "gather +" + batch.size() + " @ " + cx + "," + cz;
    }

    private void doEnsureChests(Object claim, Location anchor, AvaConfig.AutonomyConfig cfg) {
        phase.set(Phase.ENSURE_CHESTS);
        if (!cfg.placeChests()) {
            lastNote = "chests_disabled";
            return;
        }
        List<Block> chests = storage.findAvaChests(anchor, Math.max(cfg.gatherRadius(), 16));
        adoptChests(claim, anchor, chests);
        chests = storage.findAvaChests(anchor, Math.max(cfg.gatherRadius(), 16));
        Map<AvaStorageService.Category, Integer> counts = storage.countByCategory(chests);
        int placed = 0;
        for (AvaStorageService.Category cat : AvaStorageService.Category.values()) {
            if (chests.size() + placed >= cfg.maxChests()) break;
            if (counts.getOrDefault(cat, 0) > 0) continue;
            Block made = storage.placeChest(anchor, cat, claim);
            if (made != null) {
                placed++;
                chestsPlaced.incrementAndGet();
                appear(made.getLocation());
            }
        }
        if (placed == 0 && !chests.isEmpty()) {
            appear(chests.get(0).getLocation());
        }
        lastNote = "chests ensure +" + placed + " (have " + (chests.size() + placed) + ")";
    }

    private void adoptChests(Object claim, Location anchor, List<Block> known) {
        World world = anchor.getWorld();
        if (world == null) return;
        int r = 16;
        int cx = anchor.getBlockX();
        int cy = anchor.getBlockY();
        int cz = anchor.getBlockZ();
        AvaStorageService.Category[] cats = AvaStorageService.Category.values();
        int i = 0;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                for (int y = cy - 6; y <= cy + 6; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (b.getType() != Material.CHEST && b.getType() != Material.BARREL) continue;
                    if (!claimContains(claim, b.getLocation())) continue;
                    if (storage.isAvaChest(b)) continue;
                    storage.tagChest(b, cats[i % cats.length]);
                    i++;
                    known.add(b);
                }
            }
        }
    }

    private void doDeposit(Object claim, Location anchor, AvaConfig.AutonomyConfig cfg) {
        phase.set(Phase.DEPOSIT);
        List<Block> chests = storage.findAvaChests(anchor, Math.max(cfg.gatherRadius(), 16));
        if (chests.isEmpty()) {
            lastNote = "deposit skip — no chests";
            return;
        }
        int moved = 0;
        if (homeCrew) {
            Player ava = findAvaOnline();
            if (ava != null) {
                for (int slot = 0; slot < ava.getInventory().getSize(); slot++) {
                    ItemStack stack = ava.getInventory().getItem(slot);
                    if (stack == null || stack.getType().isAir()) continue;
                    if (isGodKit(stack.getType())) continue;
                    ItemStack left = storage.deposit(stack, chests);
                    if (left == null) {
                        ava.getInventory().setItem(slot, null);
                        moved += stack.getAmount();
                    } else if (left.getAmount() < stack.getAmount()) {
                        ava.getInventory().setItem(slot, left);
                        moved += stack.getAmount() - left.getAmount();
                    }
                }
                ava.updateInventory();
            }
        }
        deposited.addAndGet(moved);
        if (!chests.isEmpty()) appear(chests.get(0).getLocation());
        lastNote = "deposit +" + moved + " into " + chests.size() + " chests";
    }

    private void doVacuum(Object claim, Location anchor, AvaConfig.AutonomyConfig cfg) {
        phase.set(Phase.VACUUM);
        World world = anchor.getWorld();
        if (world == null) return;
        List<Block> chests = storage.findAvaChests(anchor, Math.max(cfg.gatherRadius(), 16));
        double rad = cfg.vacuumRadius();
        int sucked = 0;
        appear(anchor);
        for (Item item : world.getEntitiesByClass(Item.class)) {
            if (item.getLocation().distanceSquared(anchor) > rad * rad) continue;
            if (!claimContains(claim, item.getLocation())) continue;
            ItemStack stack = item.getItemStack();
            ItemStack left = storage.deposit(stack, chests);
            if (left == null) {
                item.remove();
                sucked += stack.getAmount();
            } else if (left.getAmount() < stack.getAmount()) {
                item.setItemStack(left);
                sucked += stack.getAmount() - left.getAmount();
            }
        }
        if (homeCrew) {
            Player ava = findAvaOnline();
            if (ava != null) {
                Location al = ava.getLocation();
                for (Item item : world.getEntitiesByClass(Item.class)) {
                    if (item.getLocation().distanceSquared(al) > rad * rad) continue;
                    if (!claimContains(claim, item.getLocation())) continue;
                    ItemStack stack = item.getItemStack();
                    Map<Integer, ItemStack> overflow = ava.getInventory().addItem(stack.clone());
                    if (overflow.isEmpty()) {
                        item.remove();
                        sucked += stack.getAmount();
                    } else {
                        ItemStack left = overflow.values().iterator().next();
                        ItemStack still = storage.deposit(left, chests);
                        if (still == null) {
                            item.remove();
                            sucked += stack.getAmount();
                        } else {
                            item.setItemStack(still);
                            sucked += Math.max(0, stack.getAmount() - still.getAmount());
                        }
                    }
                }
                ava.updateInventory();
            }
        }
        deposited.addAndGet(sucked);
        lastNote = "vacuum +" + sucked;
    }

    public String acceptGift(Player from, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return "empty";
        List<Object> claims = ownedClaims(claimOwnerId);
        if (claims.isEmpty()) return "no_claims";
        Object claim = claims.get(0);
        Location anchor = claimAnchor(claim);
        if (anchor == null) return "no_anchor";
        Location fromLoc = from.getLocation();
        boolean near = false;
        Player ava = findAvaOnline();
        if (ava != null && ava.getLocation().distanceSquared(fromLoc) <= 64) near = true;
        if (claimContains(claim, fromLoc)) near = true;
        if (!near) return "too_far";

        List<Block> chests = storage.findAvaChests(anchor, 24);
        if (chests.isEmpty() && plugin.config().autonomy().placeChests()) {
            storage.placeChest(anchor, AvaStorageService.categorize(stack.getType()), claim);
            chests = storage.findAvaChests(anchor, 24);
        }
        ItemStack left = storage.deposit(stack.clone(), chests);
        if (left == null) {
            deposited.addAndGet(stack.getAmount());
            lastNote = "gift from " + from.getName() + " · " + stack.getType();
            return null;
        }
        if (left.getAmount() < stack.getAmount()) {
            deposited.addAndGet(stack.getAmount() - left.getAmount());
            return "partial:" + left.getAmount();
        }
        return "chests_full";
    }

    private void stashOrDrop(ItemStack drop, List<Block> chests, Location anchor) {
        if (drop == null || drop.getType().isAir()) return;
        if (homeCrew) {
            Player ava = findAvaOnline();
            if (ava != null) {
                Map<Integer, ItemStack> overflow = ava.getInventory().addItem(drop);
                if (overflow.isEmpty()) return;
                drop = overflow.values().iterator().next();
            }
        }
        ItemStack left = storage.deposit(drop, chests);
        if (left != null && left.getAmount() > 0) {
            Location dropAt = dropInFront(chests, anchor);
            if (dropAt != null && dropAt.getWorld() != null) {
                dropAt.getWorld().dropItemNaturally(dropAt, left);
            }
        }
    }

    private static Location dropInFront(List<Block> chests, Location fallback) {
        if (chests != null) {
            for (Block b : chests) {
                if (b == null || b.getWorld() == null) continue;
                Location at = b.getLocation().add(0.5, 0.2, 0.5);
                if (b.getBlockData() instanceof org.bukkit.block.data.Directional dir) {
                    at.add(dir.getFacing().getModX(), 0, dir.getFacing().getModZ());
                } else {
                    at.add(0, 0, 1);
                }
                return at;
            }
        }
        return fallback;
    }

    private void appear(Location at) {
        AvaPresenceService presence = plugin.presence();
        if (presence != null) {
            presence.appearWorking(crewId, at);
        }
    }

    private Player findAvaOnline() {
        AvaConfig.PowersConfig p = plugin.config().powers();
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (p.isAvaPlayer(pl.getUniqueId(), pl.getName())) return pl;
        }
        return null;
    }

    private static boolean isGodKit(Material type) {
        return type.name().startsWith("NETHERITE_");
    }

    private static boolean isGatherTarget(Material type) {
        if (type == null || type.isAir() || type == Material.BEDROCK || type == Material.CHEST
                || type == Material.BARREL || type == Material.SPAWNER) {
            return false;
        }
        String n = type.name();
        if (n.contains("ORE") || type == Material.ANCIENT_DEBRIS) return true;
        if (Tag.LOGS.isTagged(type)) return true;
        return type == Material.STONE || type == Material.COBBLESTONE || type == Material.DEEPSLATE
                || type == Material.COBBLED_DEEPSLATE || type == Material.GRANITE || type == Material.DIORITE
                || type == Material.ANDESITE || type == Material.NETHERRACK || type == Material.BASALT
                || type == Material.BLACKSTONE || type == Material.TUFF;
    }

    private static int gatherPriority(Material type) {
        String n = type.name();
        if (n.contains("ANCIENT") || n.contains("DIAMOND") || n.contains("EMERALD")) return 100;
        if (n.contains("ORE")) return 80;
        if (Tag.LOGS.isTagged(type)) return 50;
        return 10;
    }

    public static List<Object> ownedClaims(UUID ownerId) {
        List<Object> out = new ArrayList<>();
        if (ownerId == null) return out;
        try {
            org.bukkit.plugin.Plugin claims = Bukkit.getPluginManager().getPlugin("Root-Claims");
            if (claims == null || !claims.isEnabled()) return out;
            Object service = claims.getClass().getMethod("claims").invoke(claims);
            @SuppressWarnings("unchecked")
            List<Object> owned = (List<Object>) service.getClass()
                    .getMethod("ownedBy", UUID.class)
                    .invoke(service, ownerId);
            if (owned != null) out.addAll(owned);
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static Location claimAnchor(Object claim) {
        try {
            Object key = claim.getClass().getMethod("key").invoke(claim);
            String world = String.valueOf(key.getClass().getMethod("world").invoke(key));
            int x = ((Number) key.getClass().getMethod("x").invoke(key)).intValue();
            int y = ((Number) key.getClass().getMethod("y").invoke(key)).intValue();
            int z = ((Number) key.getClass().getMethod("z").invoke(key)).intValue();
            World w = Bukkit.getWorld(world);
            if (w == null) return null;
            return new Location(w, x + 0.5, y, z + 0.5);
        } catch (Throwable t) {
            return null;
        }
    }

    public static UUID claimOwnerId(Object claim) {
        if (claim == null) return null;
        try {
            Object v = claim.getClass().getMethod("ownerId").invoke(claim);
            if (v instanceof UUID id) return id;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private int claimRadius(Object claim) {
        try {
            return ((Number) claim.getClass().getMethod("radiusBlocks").invoke(claim)).intValue();
        } catch (Throwable t) {
            return 16;
        }
    }

    private boolean claimContains(Object claim, Location loc) {
        try {
            Object v = claim.getClass().getMethod("contains", Location.class).invoke(claim, loc);
            return Boolean.TRUE.equals(v);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean inWorkZone(Object claim, Location loc, AvaConfig.AutonomyConfig cfg) {
        if (claimContains(claim, loc)) return true;
        if (cfg == null || !cfg.workTerritory()) return false;
        try {
            Object key = claim.getClass().getMethod("key").invoke(claim);
            int cx = ((Number) key.getClass().getMethod("x").invoke(key)).intValue();
            int cz = ((Number) key.getClass().getMethod("z").invoke(key)).intValue();
            int r = claimRadius(claim);
            int buf = plugin.config().powers().territoryBufferBlocks();
            double dx = loc.getBlockX() - cx;
            double dz = loc.getBlockZ() - cz;
            return dx * dx + dz * dz <= (r + buf) * (double) (r + buf);
        } catch (Throwable t) {
            return false;
        }
    }
}
