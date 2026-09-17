package com.rootrecord.minecraft.rootavacore.fill;

import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.rootavacore.AvaLandAccess;
import com.rootrecord.minecraft.rootavacore.RootAvaCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /ava fill} and {@code /ava layer}: place the held block into holes from the bottom up,
 * taking one matching item from the player's inventory per place.
 */
public final class AvaFillService {

    public static final int MIN_AREA = 2;   // /ava fill 2 → 3×3
    public static final int MAX_AREA = 16;  // /ava fill 16 → 17×17
    public static final int MIN_RADIUS = 1;
    public static final int MAX_RADIUS = 16;
    public static final int DEFAULT_FILL_DEPTH = 10;
    public static final int MAX_HERE_DEPTH = 256;
    public static final int MAX_LAYER_DEPTH = 64;
    public static final int PLACES_PER_TICK = 16;
    public static final double GOLD_PER_BLOCK = 0.01;

    public enum Shape {
        COLUMN,
        SQUARE,
        CIRCLE
    }

    public static final class Job {
        public final UUID id = UUID.randomUUID();
        public UUID playerId;
        public String playerName;
        public String mode;
        public Shape shape;
        public Material material;
        public int placed;
        public int skippedSolid;
        public int remaining;
        public double goldSpent;
        public boolean done;
        Deque<Block> holes = new ArrayDeque<>();
    }

    private final RootAvaCorePlugin plugin;
    private final Map<UUID, Job> jobs = new HashMap<>();
    private BukkitTask task;

    public AvaFillService(RootAvaCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        jobs.clear();
    }

    public Job jobFor(UUID playerId) {
        Job job = jobs.get(playerId);
        return job != null && !job.done ? job : null;
    }

    public boolean cancel(Player player) {
        if (player == null) {
            return false;
        }
        Job job = jobs.remove(player.getUniqueId());
        return job != null && !job.done;
    }

    public int cancelAll() {
        int n = 0;
        for (Job job : jobs.values()) {
            if (job != null && !job.done) {
                n++;
            }
        }
        jobs.clear();
        return n;
    }

    public String startFill(Player player, String[] args) {
        return start(player, "fill", args, DEFAULT_FILL_DEPTH, true);
    }

    public String startLayer(Player player, String[] args) {
        return start(player, "layer", args, 1, false);
    }

    private String start(Player player, String mode, String[] args, int defaultDepth, boolean hereGoesToWorldFloor) {
        if (player == null) {
            return "players_only";
        }
        if (jobFor(player.getUniqueId()) != null) {
            return "already_running";
        }
        String land = AvaLandAccess.requireOwnedLand(player);
        if (land != null) {
            return land;
        }
        Material mat = heldBlock(player);
        if (mat == null) {
            return "no_block";
        }
        RootMcEconomyService eco = RootMcEconomyResolver.resolve(plugin);
        if (eco == null) {
            return "no_economy";
        }
        if (!eco.has(player.getUniqueId(), GOLD_PER_BLOCK)) {
            return "broke";
        }

        Parsed parsed = parse(args, defaultDepth, mode.equals("layer"));
        if (parsed.error != null) {
            return parsed.error;
        }

        Location origin = originBlock(player, parsed.shape == Shape.COLUMN);
        if (origin == null || origin.getWorld() == null) {
            return "no_target";
        }
        World world = origin.getWorld();
        int cx = origin.getBlockX();
        int cz = origin.getBlockZ();
        int yTop = player.getLocation().getBlockY() - 1;
        if (parsed.shape == Shape.COLUMN && origin.getBlockY() <= yTop) {
            yTop = origin.getBlockY() - 1;
        }
        int minY = world.getMinHeight();
        int yBottom;
        if (mode.equals("layer")) {
            yBottom = Math.max(minY, yTop - parsed.depth + 1);
        } else if (parsed.shape == Shape.COLUMN && hereGoesToWorldFloor) {
            yBottom = Math.max(minY, yTop - MAX_HERE_DEPTH + 1);
        } else {
            yBottom = Math.max(minY, yTop - parsed.depth + 1);
        }
        if (yTop < yBottom) {
            return "no_holes";
        }

        List<Block> holes = new ArrayList<>();
        int half = parsed.shape == Shape.SQUARE ? (parsed.size - 1) / 2 : parsed.radius;
        int scan = parsed.shape == Shape.COLUMN ? 0 : Math.max(half, parsed.radius);
        for (int dx = -scan; dx <= scan; dx++) {
            for (int dz = -scan; dz <= scan; dz++) {
                if (!inShape(parsed, dx, dz)) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                for (int y = yBottom; y <= yTop; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!isHole(block)) {
                        continue;
                    }
                    if (!AvaLandAccess.bypass(player)) {
                        Object claim = AvaLandAccess.claimAt(block.getLocation());
                        if (!AvaLandAccess.isOwner(claim, player.getUniqueId())) {
                            continue;
                        }
                    }
                    holes.add(block);
                }
            }
        }
        holes.sort(Comparator
                .comparingInt(Block::getY)
                .thenComparingInt(Block::getX)
                .thenComparingInt(Block::getZ));
        if (holes.isEmpty()) {
            return "no_holes";
        }

        Job job = new Job();
        job.playerId = player.getUniqueId();
        job.playerName = player.getName();
        job.mode = mode;
        job.shape = parsed.shape;
        job.material = mat;
        job.holes.addAll(holes);
        job.remaining = holes.size();
        jobs.put(player.getUniqueId(), job);
        double estimate = holes.size() * GOLD_PER_BLOCK;
        return "ok:" + holes.size() + ":" + mat.name().toLowerCase(Locale.ROOT) + ":" + fmtGold(estimate);
    }

    private void tick() {
        if (jobs.isEmpty()) {
            return;
        }
        List<UUID> done = new ArrayList<>();
        for (Job job : jobs.values()) {
            if (job.done) {
                done.add(job.playerId);
                continue;
            }
            Player player = Bukkit.getPlayer(job.playerId);
            if (player == null || !player.isOnline()) {
                job.done = true;
                done.add(job.playerId);
                continue;
            }
            int placed = 0;
            while (placed < PLACES_PER_TICK && !job.holes.isEmpty()) {
                Block block = job.holes.pollFirst();
                job.remaining = job.holes.size();
                if (block == null || !isHole(block)) {
                    job.skippedSolid++;
                    continue;
                }
                if (!chargeOne(player, job)) {
                    job.holes.addFirst(block);
                    job.remaining = job.holes.size();
                    job.done = true;
                    tell(player, "&eStopped — need &f0.01 G &eper block. Placed &f" + job.placed
                            + " &e(&f" + fmtGold(job.goldSpent) + " G&e).");
                    done.add(job.playerId);
                    break;
                }
                if (!takeOne(player, job.material)) {
                    refundOne(player, job, GOLD_PER_BLOCK);
                    job.holes.addFirst(block);
                    job.remaining = job.holes.size();
                    job.done = true;
                    tell(player, "&eStopped — out of &f" + pretty(job.material)
                            + "&e. Placed &f" + job.placed
                            + " &e(&f" + fmtGold(job.goldSpent) + " G&e).");
                    done.add(job.playerId);
                    break;
                }
                block.setType(job.material, false);
                job.placed++;
                placed++;
            }
            if (job.holes.isEmpty()) {
                job.done = true;
                job.remaining = 0;
                tell(player, "&aFilled &f" + job.placed + " &ahole"
                        + (job.placed == 1 ? "" : "s")
                        + " with &f" + pretty(job.material)
                        + " &8· &f" + fmtGold(job.goldSpent) + " G&a.");
                done.add(job.playerId);
            }
        }
        for (UUID id : done) {
            jobs.remove(id);
        }
    }

    private static boolean inShape(Parsed parsed, int dx, int dz) {
        return switch (parsed.shape) {
            case COLUMN -> dx == 0 && dz == 0;
            case SQUARE -> Math.max(Math.abs(dx), Math.abs(dz)) <= (parsed.size - 1) / 2;
            case CIRCLE -> dx * dx + dz * dz <= parsed.radius * parsed.radius;
        };
    }

    private static boolean isHole(Block block) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        return type.isAir()
                || type == Material.CAVE_AIR
                || type == Material.VOID_AIR
                || type == Material.WATER
                || type == Material.LAVA
                || type == Material.BUBBLE_COLUMN;
    }

    private static Material heldBlock(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir() || hand.getAmount() < 1) {
            return null;
        }
        Material mat = hand.getType();
        if (!mat.isBlock() || mat.isAir()) {
            return null;
        }
        return mat;
    }

    private static boolean takeOne(Player player, Material mat) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }
        PlayerInventory inv = player.getInventory();
        ItemStack hand = inv.getItemInMainHand();
        if (hand != null && hand.getType() == mat && hand.getAmount() > 0) {
            hand.setAmount(hand.getAmount() - 1);
            if (hand.getAmount() <= 0) {
                inv.setItemInMainHand(null);
            }
            return true;
        }
        HashMap<Integer, ItemStack> leftover = inv.removeItem(new ItemStack(mat, 1));
        return leftover.isEmpty();
    }

    private boolean chargeOne(Player player, Job job) {
        RootMcEconomyService eco = RootMcEconomyResolver.resolve(plugin);
        if (eco == null) {
            return false;
        }
        if (!eco.has(player.getUniqueId(), GOLD_PER_BLOCK)
                || !eco.withdraw(player.getUniqueId(), GOLD_PER_BLOCK)) {
            return false;
        }
        try {
            eco.sinkServiceFee(player.getUniqueId(), player.getName(), GOLD_PER_BLOCK, "fill");
        } catch (Throwable ignored) {
            // Gold already left the wallet; reserve sink is best-effort.
        }
        job.goldSpent += GOLD_PER_BLOCK;
        return true;
    }

    private void refundOne(Player player, Job job, double amount) {
        RootMcEconomyService eco = RootMcEconomyResolver.resolve(plugin);
        if (eco == null || amount <= 0) {
            return;
        }
        try {
            eco.deposit(player.getUniqueId(), amount);
            job.goldSpent = Math.max(0, job.goldSpent - amount);
        } catch (Throwable ignored) {
            // ignore
        }
    }

    private static String fmtGold(double g) {
        return String.format(java.util.Locale.US, "%.2f", g);
    }

    private static Location originBlock(Player player, boolean column) {
        if (column) {
            RayTraceResult hit = player.rayTraceBlocks(6.0);
            if (hit != null && hit.getHitBlock() != null) {
                Block hitBlock = hit.getHitBlock();
                BlockFace face = hit.getHitBlockFace();
                if (face == BlockFace.UP) {
                    return hitBlock.getLocation();
                }
                return hitBlock.getLocation();
            }
        }
        return player.getLocation().getBlock().getRelative(BlockFace.DOWN).getLocation();
    }

    private void tell(Player player, String msg) {
        player.sendMessage(plugin.colorize(plugin.config().prefix() + msg));
    }

    private static String pretty(Material mat) {
        return mat.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static final class Parsed {
        Shape shape = Shape.SQUARE;
        int size = 3;      // odd width for square
        int radius = 1;
        int depth;
        String error;
    }

    /**
     * fill: here | [area|square] N | N | circle R
     * layer: same, last number may be depth when two numbers after shape.
     */
    static Parsed parse(String[] args, int defaultDepth, boolean layer) {
        Parsed p = new Parsed();
        p.depth = defaultDepth;
        if (args == null || args.length == 0) {
            p.error = layer ? "usage_layer" : "usage_fill";
            return p;
        }
        int i = 0;
        String a0 = args[0].toLowerCase(Locale.ROOT);
        if (a0.equals("here") || a0.equals("column") || a0.equals("under")) {
            p.shape = Shape.COLUMN;
            p.size = 1;
            i = 1;
        } else if (a0.equals("circle") || a0.equals("round")) {
            p.shape = Shape.CIRCLE;
            i = 1;
            if (i >= args.length) {
                p.error = "usage_circle";
                return p;
            }
            Integer r = parseInt(args[i]);
            if (r == null || r < MIN_RADIUS || r > MAX_RADIUS) {
                p.error = "bad_radius";
                return p;
            }
            p.radius = r;
            i++;
        } else if (a0.equals("area") || a0.equals("square") || a0.equals("sq")) {
            p.shape = Shape.SQUARE;
            i = 1;
            if (i >= args.length) {
                p.error = "usage_area";
                return p;
            }
            Integer n = parseInt(args[i]);
            if (n == null || n < MIN_AREA || n > MAX_AREA) {
                p.error = "bad_area";
                return p;
            }
            p.size = n + 1; // /ava fill 10 → 11×11
            i++;
        } else {
            Integer n = parseInt(args[0]);
            if (n == null) {
                p.error = layer ? "usage_layer" : "usage_fill";
                return p;
            }
            if (layer && args.length == 1) {
                p.shape = Shape.COLUMN;
                p.size = 1;
                if (n < 1 || n > MAX_LAYER_DEPTH) {
                    p.error = "bad_depth";
                    return p;
                }
                p.depth = n;
                return p;
            }
            if (n < MIN_AREA || n > MAX_AREA) {
                p.error = "bad_area";
                return p;
            }
            p.shape = Shape.SQUARE;
            p.size = n + 1;
            i = 1;
        }
        if (i < args.length) {
            Integer d = parseInt(args[i]);
            if (d == null || d < 1 || d > MAX_LAYER_DEPTH) {
                p.error = "bad_depth";
                return p;
            }
            p.depth = d;
        } else if (layer && p.shape != Shape.COLUMN) {
            p.error = "usage_layer_depth";
        }
        return p;
    }

    private static Integer parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
