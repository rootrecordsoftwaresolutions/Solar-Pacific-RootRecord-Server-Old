package com.rootrecord.minecraft.rootavacore.autonomy;

import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.rootavacore.RootAvaCorePlugin;
import com.rootrecord.minecraft.rootavacore.presence.AvaPresenceService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Progressive homestead builder — spends chest materials to raise Ava's base on her claim.
 * Meant to read like a real starter / spawn claim: cabin + workstations + yard.
 * Stages: pad → walls → roof → fittings → path → yard → farm.
 */
public final class AvaHomesteadBuilder {

    public enum Stage {
        PAD,
        WALLS,
        ROOF,
        FITTINGS,
        PATH,
        YARD,
        FARM,
        DONE
    }

    private final RootAvaCorePlugin plugin;
    private final AvaStorageService storage;
    private final UUID ownerId;
    private final UUID crewId;
    private Stage stage = Stage.PAD;
    private int cursor;
    private int placedTotal;
    private String lastNote = "idle";

    public AvaHomesteadBuilder(RootAvaCorePlugin plugin, AvaStorageService storage) {
        this(plugin, storage, null, AvaPresenceService.HOME_CREW);
    }

    public AvaHomesteadBuilder(RootAvaCorePlugin plugin, AvaStorageService storage, UUID ownerId, UUID crewId) {
        this.plugin = plugin;
        this.storage = storage;
        this.ownerId = ownerId;
        this.crewId = crewId == null ? AvaPresenceService.HOME_CREW : crewId;
        load();
    }

    public Stage stage() {
        return stage;
    }

    public int placedTotal() {
        return placedTotal;
    }

    public String lastNote() {
        return lastNote;
    }

    public String statusLine() {
        return stage.name().toLowerCase(Locale.ROOT) + " · built " + placedTotal + " · " + lastNote;
    }

    /**
     * Place up to {@code budget} blocks this tick. Returns blocks placed.
     */
    public int tick(Object claim, Location anchor, List<Block> chests, int budget) {
        if (anchor == null || anchor.getWorld() == null || budget <= 0) {
            lastNote = "no_anchor";
            return 0;
        }
        if (stage == Stage.DONE) {
            lastNote = "homestead complete";
            return 0;
        }
        Location origin = houseOrigin(anchor);
        int placed = 0;
        switch (stage) {
            case PAD -> placed = buildPad(claim, origin, chests, budget);
            case WALLS -> placed = buildWalls(claim, origin, chests, budget);
            case ROOF -> placed = buildRoof(claim, origin, chests, budget);
            case FITTINGS -> placed = buildFittings(claim, origin, chests, budget);
            case PATH -> placed = buildPath(claim, origin, anchor, chests, budget);
            case YARD -> placed = buildYard(claim, origin, chests, budget);
            case FARM -> placed = buildFarm(claim, origin, chests, budget);
            default -> {
            }
        }
        if (placed > 0) {
            placedTotal += placed;
            save();
        }
        return placed;
    }

    private Location houseOrigin(Location anchor) {
        // Offset from claim center so we don't bury the chest stacks
        World w = anchor.getWorld();
        int x = anchor.getBlockX() + 6;
        int z = anchor.getBlockZ() + 6;
        int y = anchor.getBlockY();
        Block surface = AvaStorageService.findTerrainSurface(w, x, y, z);
        if (surface != null) {
            return new Location(w, x + 0.5, surface.getY() + 1, z + 0.5);
        }
        return new Location(w, x + 0.5, y, z + 0.5);
    }

    private int buildPad(Object claim, Location origin, List<Block> chests, int budget) {
        // 7x7 floor at y-1
        List<int[]> jobs = new ArrayList<>();
        for (int dx = 0; dx < 7; dx++) {
            for (int dz = 0; dz < 7; dz++) {
                jobs.add(new int[] {dx, -1, dz});
            }
        }
        int placed = placeJobs(claim, origin, chests, jobs, preferFloor(), budget);
        if (cursor >= jobs.size()) {
            advance(Stage.WALLS);
        }
        lastNote = "pad " + cursor + "/" + jobs.size();
        return placed;
    }

    private int buildWalls(Object claim, Location origin, List<Block> chests, int budget) {
        List<int[]> jobs = new ArrayList<>();
        for (int dy = 0; dy < 3; dy++) {
            for (int i = 0; i < 7; i++) {
                jobs.add(new int[] {i, dy, 0});
                jobs.add(new int[] {i, dy, 6});
                jobs.add(new int[] {0, dy, i});
                jobs.add(new int[] {6, dy, i});
            }
        }
        // Door gap on +z face center
        jobs.removeIf(j -> j[0] == 3 && j[2] == 6 && (j[1] == 0 || j[1] == 1));
        int placed = placeJobs(claim, origin, chests, jobs, preferWalls(), budget);
        if (cursor >= jobs.size()) {
            advance(Stage.ROOF);
        }
        lastNote = "walls " + cursor + "/" + jobs.size();
        return placed;
    }

    private int buildRoof(Object claim, Location origin, List<Block> chests, int budget) {
        List<int[]> jobs = new ArrayList<>();
        for (int dx = 0; dx < 7; dx++) {
            for (int dz = 0; dz < 7; dz++) {
                jobs.add(new int[] {dx, 3, dz});
            }
        }
        int placed = placeJobs(claim, origin, chests, jobs, preferRoof(), budget);
        if (cursor >= jobs.size()) {
            advance(Stage.FITTINGS);
        }
        lastNote = "roof " + cursor + "/" + jobs.size();
        return placed;
    }

    private int buildFittings(Object claim, Location origin, List<Block> chests, int budget) {
        // Classic starter house kit — workstations a real player would drop first night.
        List<RunnablePlace> steps = new ArrayList<>();
        steps.add((c, o, ch) -> placeDoor(c, o, ch));
        // Sleep + craft corner (north wall)
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(1, 0, 1), ch, Material.RED_BED));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(5, 0, 1), ch, Material.CRAFTING_TABLE));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(5, 0, 2), ch, Material.FURNACE));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(4, 0, 2), ch, Material.SMOKER));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(4, 0, 1), ch, Material.BLAST_FURNACE));
        // Storage wall
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(1, 0, 4), ch, Material.CHEST));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(2, 0, 4), ch, Material.CHEST));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(1, 0, 5), ch, Material.BARREL));
        // Extra workstations
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(5, 0, 4), ch, Material.STONECUTTER));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(5, 0, 5), ch, Material.GRINDSTONE));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(3, 0, 1), ch, Material.CARTOGRAPHY_TABLE));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(2, 0, 1), ch, Material.LOOM));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(2, 0, 5), ch, Material.COMPOSTER));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(3, 0, 5), ch, Material.SMITHING_TABLE));
        // Light + life
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(1, 2, 1), ch, Material.TORCH));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(5, 2, 5), ch, Material.TORCH));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(1, 2, 5), ch, Material.TORCH));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(5, 2, 1), ch, Material.TORCH));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(3, 0, 3), ch, Material.FLOWER_POT));

        return runSteps(claim, origin, chests, steps, budget, Stage.PATH, "fittings");
    }

    private int buildPath(Object claim, Location origin, Location anchor, List<Block> chests, int budget) {
        // Cobble path from door toward claim chests / anchor
        List<int[]> jobs = new ArrayList<>();
        int ox = origin.getBlockX() + 3;
        int oz = origin.getBlockZ() + 7;
        int tx = anchor.getBlockX();
        int tz = anchor.getBlockZ();
        int x = ox;
        int z = oz;
        for (int i = 0; i < 16; i++) {
            jobs.add(new int[] {x - origin.getBlockX(), -1, z - origin.getBlockZ()});
            if (x == tx && z == tz) break;
            if (Math.abs(tx - x) >= Math.abs(tz - z)) {
                x += Integer.compare(tx, x);
            } else {
                z += Integer.compare(tz, z);
            }
        }
        int placed = placeJobs(claim, origin, chests, jobs, List.of(Material.COBBLESTONE, Material.STONE, Material.DIRT), budget);
        if (cursor >= jobs.size()) {
            advance(Stage.YARD);
        }
        lastNote = "path " + cursor + "/" + jobs.size();
        return placed;
    }

    /** Outdoor spawn plaza — campfire, benches, gate, lights, welcome chest. */
    private int buildYard(Object claim, Location origin, List<Block> chests, int budget) {
        List<RunnablePlace> steps = new ArrayList<>();
        // Front patio pad (already path-ish) + campfire hangout
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(3, 0, 8), ch, Material.CAMPFIRE));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(2, 0, 8), ch, Material.OAK_STAIRS));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(4, 0, 8), ch, Material.OAK_STAIRS));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(1, 0, 8), ch, Material.OAK_STAIRS));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(5, 0, 8), ch, Material.OAK_STAIRS));
        // Fence gate entrance + posts
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(3, 0, 10), ch, Material.OAK_FENCE_GATE));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(2, 0, 10), ch, Material.OAK_FENCE));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(4, 0, 10), ch, Material.OAK_FENCE));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(2, 1, 10), ch, Material.TORCH));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(4, 1, 10), ch, Material.TORCH));
        // Welcome / community chest + outdoor craft (spawn visitors)
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(0, 0, 8), ch, Material.CHEST));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(6, 0, 8), ch, Material.CRAFTING_TABLE));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(6, 0, 9), ch, Material.FURNACE));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(0, 0, 9), ch, Material.BARREL));
        // Torch ring for night safety around the door
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(0, 0, 6), ch, Material.TORCH));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(6, 0, 6), ch, Material.TORCH));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(3, 0, 12), ch, Material.TORCH));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(-1, 0, 9), ch, Material.TORCH));
        steps.add((c, o, ch) -> placeUtility(c, o.clone().add(7, 0, 9), ch, Material.TORCH));

        return runSteps(claim, origin, chests, steps, budget, Stage.FARM, "yard");
    }

    private int runSteps(
            Object claim,
            Location origin,
            List<Block> chests,
            List<RunnablePlace> steps,
            int budget,
            Stage next,
            String label) {
        int placed = 0;
        while (cursor < steps.size() && placed < budget) {
            int got = steps.get(cursor).run(claim, origin, chests);
            if (got <= 0) {
                lastNote = "need materials for " + label + " @" + cursor;
                break;
            }
            placed += got > 0 ? 1 : 0;
            cursor++;
        }
        if (cursor >= steps.size()) {
            advance(next);
        }
        lastNote = label + " " + cursor + "/" + steps.size();
        return placed;
    }

    private int buildFarm(Object claim, Location origin, List<Block> chests, int budget) {
        List<int[]> jobs = new ArrayList<>();
        // 5x5 farm patch west of house
        for (int dx = -5; dx <= -1; dx++) {
            for (int dz = 1; dz <= 5; dz++) {
                jobs.add(new int[] {dx, -1, dz});
            }
        }
        int placed = 0;
        // Center water
        if (cursor == 0) {
            Location water = origin.clone().add(-3, -1, 3);
            if (inZone(claim, water) && placeOne(claim, water, chests, Material.WATER) > 0) {
                placed++;
            }
            cursor = 1;
        }
        List<int[]> soilJobs = jobs;
        // Re-map: place farmland using dirt/hoe conceptually — set FARMLAND if we have dirt
        while (cursor - 1 < soilJobs.size() && placed < budget) {
            int[] j = soilJobs.get(cursor - 1);
            Location at = origin.clone().add(j[0], j[1], j[2]);
            if (!inZone(claim, at)) {
                cursor++;
                continue;
            }
            Block b = at.getBlock();
            if (b.getType() == Material.FARMLAND || b.getType() == Material.WATER) {
                cursor++;
                continue;
            }
            if (!withdraw(chests, List.of(Material.DIRT, Material.GRASS_BLOCK, Material.COARSE_DIRT), 1)) {
                lastNote = "need dirt for farm";
                break;
            }
            b.setType(Material.FARMLAND, false);
            appear(at);
            placed++;
            cursor++;
        }
        if (cursor - 1 >= soilJobs.size()) {
            // Seed the patch so AvaFarmService can harvest immediately after.
            plantFarmPatch(claim, origin, chests);
            advance(Stage.DONE);
            lastNote = "homestead complete · farm seeded";
        } else {
            lastNote = "farm " + (cursor - 1) + "/" + soilJobs.size();
        }
        return placed;
    }

    private void plantFarmPatch(Object claim, Location origin, List<Block> chests) {
        Material[][] crops = {
                {Material.WHEAT_SEEDS, Material.WHEAT},
                {Material.CARROT, Material.CARROTS},
                {Material.POTATO, Material.POTATOES},
                {Material.BEETROOT_SEEDS, Material.BEETROOTS},
        };
        for (int dx = -5; dx <= -1; dx++) {
            for (int dz = 1; dz <= 5; dz++) {
                Location soilAt = origin.clone().add(dx, -1, dz);
                if (!inZone(claim, soilAt)) continue;
                Block soil = soilAt.getBlock();
                if (soil.getType() != Material.FARMLAND) continue;
                Block air = soil.getRelative(org.bukkit.block.BlockFace.UP);
                if (!air.getType().isAir()) continue;
                for (Material[] pair : crops) {
                    if (withdraw(chests, List.of(pair[0]), 1)) {
                        air.setType(pair[1], false);
                        appear(air.getLocation());
                        break;
                    }
                }
            }
        }
    }

    private int placeJobs(
            Object claim,
            Location origin,
            List<Block> chests,
            List<int[]> jobs,
            List<Material> prefer,
            int budget) {
        int placed = 0;
        while (cursor < jobs.size() && placed < budget) {
            int[] j = jobs.get(cursor);
            Location at = origin.clone().add(j[0], j[1], j[2]);
            if (!inZone(claim, at)) {
                cursor++;
                continue;
            }
            Block b = at.getBlock();
            if (!b.getType().isAir() && b.getType().isSolid()) {
                cursor++;
                continue;
            }
            Material mat = pickMaterial(chests, prefer);
            if (mat == null) {
                lastNote = "need " + prefer.get(0).name().toLowerCase(Locale.ROOT);
                break;
            }
            if (!withdraw(chests, List.of(mat), 1)) {
                lastNote = "need " + mat.name().toLowerCase(Locale.ROOT);
                break;
            }
            b.setType(mat, false);
            appear(at);
            placed++;
            cursor++;
        }
        return placed;
    }

    private int placeOne(Object claim, Location at, List<Block> chests, Material want) {
        return placeUtility(claim, at, chests, want);
    }

    /**
     * Place a workstation / amenity, crafting from chest stock when the finished item is missing.
     * Returns 1 if present or placed, 0 if materials are short.
     */
    private int placeUtility(Object claim, Location at, List<Block> chests, Material want) {
        if (!inZone(claim, at)) return 1; // skip OOB so the stage can finish
        if (want == Material.CHEST || want == Material.BARREL) {
            Block surface = AvaStorageService.findTerrainSurface(
                    at.getWorld(), at.getBlockX(), at.getBlockY(), at.getBlockZ());
            if (surface == null) return 1;
            at = surface.getLocation().add(0, 1, 0);
        }
        Block b = at.getBlock();
        Material cur = b.getType();
        if (cur == want) return 1;
        if (want == Material.RED_BED && cur.name().endsWith("_BED")) return 1;
        if (want == Material.OAK_STAIRS && cur.name().endsWith("_STAIRS")) return 1;
        if (want == Material.OAK_FENCE && cur.name().endsWith("_FENCE") && !cur.name().contains("GATE")) return 1;
        if (want == Material.OAK_FENCE_GATE && cur.name().endsWith("FENCE_GATE")) return 1;
        if (!cur.isAir() && cur.isSolid() && want != Material.TORCH) return 1;

        if (want == Material.WATER) {
            b.setType(Material.WATER, false);
            appear(at);
            return 1;
        }
        if (want == Material.TORCH) {
            withdraw(chests, List.of(Material.TORCH), 1);
            // soft light if no coal — night-safe spawn
            b.setType(Material.TORCH, false);
            appear(at);
            return 1;
        }
        if (want == Material.FLOWER_POT) {
            withdraw(chests, List.of(Material.FLOWER_POT, Material.BRICK), 1);
            b.setType(Material.FLOWER_POT, false);
            appear(at);
            return 1;
        }
        if (!craftAndPlace(claim, at, chests, want)) {
            return 0;
        }
        return 1;
    }

    private boolean craftAndPlace(Object claim, Location at, List<Block> chests, Material want) {
        Block b = at.getBlock();
        if (withdraw(chests, List.of(want), 1)) {
            setPlaced(b, at, want);
            return true;
        }
        return switch (want) {
            case CRAFTING_TABLE -> spendPlanks(chests, 4) && setPlaced(b, at, Material.CRAFTING_TABLE);
            case FURNACE -> spendStone(chests, 8) && setPlaced(b, at, Material.FURNACE);
            case BLAST_FURNACE ->
                    spendStone(chests, 5)
                            && (withdraw(chests, List.of(Material.FURNACE), 1) || spendStone(chests, 8))
                            && setPlaced(b, at, Material.BLAST_FURNACE);
            case SMOKER ->
                    spendLogs(chests, 4)
                            && (withdraw(chests, List.of(Material.FURNACE), 1) || spendStone(chests, 8))
                            && setPlaced(b, at, Material.SMOKER);
            case CHEST -> spendPlanks(chests, 8) && setPlaced(b, at, Material.CHEST);
            case BARREL -> spendPlanks(chests, 7) && setPlaced(b, at, Material.BARREL);
            case STONECUTTER -> spendStone(chests, 3) && setPlaced(b, at, Material.STONECUTTER);
            case GRINDSTONE ->
                    spendPlanks(chests, 2) && spendStone(chests, 2) && setPlaced(b, at, Material.GRINDSTONE);
            case CARTOGRAPHY_TABLE -> spendPlanks(chests, 4) && setPlaced(b, at, Material.CARTOGRAPHY_TABLE);
            case LOOM -> spendPlanks(chests, 2) && setPlaced(b, at, Material.LOOM);
            case SMITHING_TABLE -> spendPlanks(chests, 4) && setPlaced(b, at, Material.SMITHING_TABLE);
            case COMPOSTER -> spendPlanks(chests, 7) && setPlaced(b, at, Material.COMPOSTER);
            case CAMPFIRE -> spendLogs(chests, 3) && setPlaced(b, at, Material.CAMPFIRE);
            case OAK_STAIRS -> spendPlanks(chests, 6) && setPlaced(b, at, Material.OAK_STAIRS);
            case OAK_FENCE -> spendPlanks(chests, 4) && setPlaced(b, at, Material.OAK_FENCE);
            case OAK_FENCE_GATE -> spendPlanks(chests, 4) && setPlaced(b, at, Material.OAK_FENCE_GATE);
            case RED_BED, WHITE_BED, YELLOW_BED, BLUE_BED -> placeBed(b, at, chests);
            default -> false;
        };
    }

    private boolean placeBed(Block b, Location at, List<Block> chests) {
        // Prefer a real bed from stock; else soft-place for spawn hospitality.
        Material bed = Material.RED_BED;
        for (Material m : List.of(
                Material.RED_BED, Material.WHITE_BED, Material.YELLOW_BED, Material.BLUE_BED,
                Material.ORANGE_BED, Material.CYAN_BED, Material.GREEN_BED)) {
            if (withdraw(chests, List.of(m), 1)) {
                bed = m;
                setPlaced(b, at, bed);
                return true;
            }
        }
        // wool + planks recipe stand-in
        if (withdraw(chests, List.of(Material.WHITE_WOOL, Material.RED_WOOL, Material.ORANGE_WOOL), 3)
                && spendPlanks(chests, 3)) {
            setPlaced(b, at, Material.RED_BED);
            return true;
        }
        setPlaced(b, at, Material.RED_BED);
        return true;
    }

    private boolean setPlaced(Block b, Location at, Material mat) {
        try {
            b.setType(mat, false);
            appear(at);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean spendPlanks(List<Block> chests, int amount) {
        List<Material> planks = List.of(
                Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS,
                Material.JUNGLE_PLANKS, Material.ACACIA_PLANKS, Material.DARK_OAK_PLANKS,
                Material.CHERRY_PLANKS, Material.MANGROVE_PLANKS, Material.BAMBOO_PLANKS);
        int got = storage.withdraw(chests, planks, amount);
        if (got >= amount) return true;
        int still = amount - got;
        return spendLogs(chests, (still + 3) / 4);
    }

    private boolean spendLogs(List<Block> chests, int amount) {
        List<Material> logs = List.of(
                Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
                Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.CHERRY_LOG, Material.MANGROVE_LOG,
                Material.OAK_WOOD, Material.SPRUCE_WOOD, Material.BIRCH_WOOD);
        return storage.withdraw(chests, logs, amount) >= amount;
    }

    private boolean spendStone(List<Block> chests, int amount) {
        List<Material> stone = List.of(
                Material.COBBLESTONE, Material.STONE, Material.COBBLED_DEEPSLATE,
                Material.DEEPSLATE, Material.ANDESITE, Material.DIORITE, Material.GRANITE,
                Material.STONE_BRICKS);
        return storage.withdraw(chests, stone, amount) >= amount;
    }

    private int placeDoor(Object claim, Location origin, List<Block> chests) {
        Location lower = origin.clone().add(3, 0, 6);
        Location upper = origin.clone().add(3, 1, 6);
        if (!inZone(claim, lower)) return 1;
        if (lower.getBlock().getType().name().contains("DOOR")) return 1;
        boolean hasDoor = withdraw(chests, List.of(Material.OAK_DOOR, Material.SPRUCE_DOOR, Material.BIRCH_DOOR), 1);
        if (!hasDoor && !spendPlanks(chests, 6)) {
            // soft door so the cabin is enterable
        }
        Material doorMat = Material.OAK_DOOR;
        try {
            lower.getBlock().setType(doorMat, false);
            if (lower.getBlock().getBlockData() instanceof Door door) {
                door.setHalf(Bisected.Half.BOTTOM);
                door.setFacing(BlockFace.SOUTH);
                lower.getBlock().setBlockData(door, false);
            }
            upper.getBlock().setType(doorMat, false);
            if (upper.getBlock().getBlockData() instanceof Door door) {
                door.setHalf(Bisected.Half.TOP);
                door.setFacing(BlockFace.SOUTH);
                upper.getBlock().setBlockData(door, false);
            }
            appear(lower);
            return 1;
        } catch (Throwable t) {
            return 0;
        }
    }

    private Material pickMaterial(List<Block> chests, List<Material> prefer) {
        for (Material m : prefer) {
            if (storage.countMaterial(chests, m) > 0) return m;
        }
        // Fallbacks by category
        for (Material m : prefer) {
            AvaStorageService.Category cat = AvaStorageService.categorize(m);
            Material any = storage.findAnyInCategory(chests, cat);
            if (any != null) return any;
        }
        return null;
    }

    private boolean withdraw(List<Block> chests, List<Material> prefer, int amount) {
        return storage.withdraw(chests, prefer, amount) > 0;
    }

    private void appear(Location at) {
        AvaPresenceService presence = plugin.presence();
        if (presence != null) {
            presence.appearWorking(crewId, at);
        }
    }

    private boolean inZone(Object claim, Location loc) {
        try {
            Object v = claim.getClass().getMethod("contains", Location.class).invoke(claim, loc);
            if (Boolean.TRUE.equals(v)) return true;
        } catch (Throwable ignored) {
        }
        // Territory buffer fallback via powers config
        try {
            Object key = claim.getClass().getMethod("key").invoke(claim);
            int cx = ((Number) key.getClass().getMethod("x").invoke(key)).intValue();
            int cz = ((Number) key.getClass().getMethod("z").invoke(key)).intValue();
            int r = ((Number) claim.getClass().getMethod("radiusBlocks").invoke(claim)).intValue();
            int buf = plugin.config().powers().territoryBufferBlocks();
            double dx = loc.getBlockX() - cx;
            double dz = loc.getBlockZ() - cz;
            return dx * dx + dz * dz <= (r + buf) * (double) (r + buf);
        } catch (Throwable t) {
            return false;
        }
    }

    private void advance(Stage next) {
        stage = next;
        cursor = 0;
        save();
    }

    private static List<Material> preferFloor() {
        return List.of(
                Material.COBBLESTONE, Material.STONE, Material.OAK_PLANKS, Material.SPRUCE_PLANKS,
                Material.DIRT, Material.COBBLED_DEEPSLATE);
    }

    private static List<Material> preferWalls() {
        return List.of(
                Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS,
                Material.COBBLESTONE, Material.STONE_BRICKS);
    }

    private static List<Material> preferRoof() {
        return List.of(
                Material.OAK_SLAB, Material.SPRUCE_SLAB, Material.OAK_PLANKS,
                Material.SPRUCE_PLANKS, Material.COBBLESTONE);
    }

    private File progressFile() {
        UUID ava = plugin.config().powers().avaUuid();
        if (ownerId == null || (ava != null && ownerId.equals(ava))) {
            return RootRecordFolders.configFile(plugin, "ava-build-progress.yml");
        }
        return RootRecordFolders.configFile(plugin, "ava-build-progress-" + ownerId + ".yml");
    }

    private void load() {
        File f = progressFile();
        if (!f.isFile()) return;
        try {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
            String s = yml.getString("stage", "PAD");
            try {
                stage = Stage.valueOf(s);
            } catch (IllegalArgumentException ex) {
                stage = Stage.PAD;
            }
            cursor = yml.getInt("cursor", 0);
            placedTotal = yml.getInt("placed-total", 0);
            int schema = yml.getInt("schema", 1);
            // schema 2: expanded fittings + yard plaza for spawn-like claim
            if (schema < 2) {
                if (stage == Stage.FARM || stage == Stage.DONE) {
                    stage = Stage.YARD;
                    cursor = 0;
                }
                // Old short fittings list — re-run full workstation kit
                if (stage == Stage.PATH) {
                    stage = Stage.FITTINGS;
                    cursor = 0;
                }
            }
        } catch (Throwable t) {
            stage = Stage.PAD;
            cursor = 0;
        }
    }

    private void save() {
        try {
            YamlConfiguration yml = new YamlConfiguration();
            yml.set("schema", 2);
            yml.set("stage", stage.name());
            yml.set("cursor", cursor);
            yml.set("placed-total", placedTotal);
            yml.save(progressFile());
        } catch (Throwable t) {
            plugin.getLogger().fine("build progress save: " + t.getMessage());
        }
    }

    @FunctionalInterface
    private interface RunnablePlace {
        int run(Object claim, Location origin, List<Block> chests);
    }
}
