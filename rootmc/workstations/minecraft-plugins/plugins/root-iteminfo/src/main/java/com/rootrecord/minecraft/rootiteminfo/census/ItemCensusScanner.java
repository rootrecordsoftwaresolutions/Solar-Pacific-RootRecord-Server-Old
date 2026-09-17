package com.rootrecord.minecraft.rootiteminfo.census;

import com.rootrecord.minecraft.rootiteminfo.RootItemInfoPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Counts items in online inventories, ender chests, loaded containers, ground drops,
 * and (when RootMC-Shops Gen2 market is ready) virtual market storage + listings.
 * Unloaded chunks are not visible — totals are a live scanned snapshot, not a full world map.
 * Physical shop chests are counted once via their container inventory (same as any other chest).
 */
public final class ItemCensusScanner {

    private final RootItemInfoPlugin plugin;
    private final ItemCensusStore store;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile long lastCompletedScanMs;

    public ItemCensusScanner(RootItemInfoPlugin plugin, ItemCensusStore store) {
        this.plugin = plugin;
        this.store = store;
        this.store.setLogger(plugin.getLogger());
    }

    public void requestFullScan(String reason) {
        if (!plugin.featureEnabled()) {
            return;
        }
        long minGapMs = Math.max(
                        10,
                        plugin.configFile() == null
                                ? 30
                                : plugin.configFile().getLong("scan.min-trigger-interval-seconds", 30))
                * 1000L;
        if (lastCompletedScanMs > 0 && System.currentTimeMillis() - lastCompletedScanMs < minGapMs) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        // Market MySQL totals off the main thread; world containers stay sync on next tick.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, Long> market = fetchMarketplaceStock();
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    runScan(reason, market);
                } finally {
                    lastCompletedScanMs = System.currentTimeMillis();
                    running.set(false);
                }
            });
        });
    }

    private void runScan(String reason, Map<String, Long> marketStock) {
        Map<String, Long> totals = new HashMap<>();
        // Double chests (and some shop holders) expose the same Inventory from both halves —
        // count each inventory instance once so shop stock is not doubled vs a normal chest.
        Set<Inventory> seenInventories = Collections.newSetFromMap(new IdentityHashMap<>());
        int players = 0;
        int containers = 0;
        int skippedDupContainers = 0;
        int ground = 0;
        int chunksSampled = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            players++;
            addInventory(totals, player.getInventory(), seenInventories);
            addInventory(totals, player.getEnderChest(), seenInventories);
        }

        var cfg = plugin.configFile();
        boolean groundItems = cfg == null || cfg.getBoolean("scan.include-ground-items", true);
        int maxChunks = Math.max(1, cfg == null ? 48 : cfg.getInt("scan.max-chunks-per-pass", 48));
        List<Chunk> loadedChunks = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            Collections.addAll(loadedChunks, world.getLoadedChunks());
        }
        loadedChunks.sort(Comparator.comparingLong(ItemCensusScanner::distanceToNearestPlayer));
        for (Chunk chunk : loadedChunks.subList(0, Math.min(maxChunks, loadedChunks.size()))) {
            chunksSampled++;
            for (BlockState state : chunk.getTileEntities()) {
                if (!(state instanceof Container container)) {
                    continue;
                }
                Inventory inv = container.getInventory();
                if (inv == null || !seenInventories.add(inv)) {
                    skippedDupContainers++;
                    continue;
                }
                addInventoryContents(totals, inv);
                containers++;
            }
            if (groundItems) {
                for (org.bukkit.entity.Entity entity : chunk.getEntities()) {
                    if (entity instanceof Item dropped) {
                        addStack(totals, dropped.getItemStack());
                        ground++;
                    }
                }
            }
        }

        int marketKeys = 0;
        if (marketStock != null && !marketStock.isEmpty()) {
            for (Map.Entry<String, Long> entry : marketStock.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) {
                    continue;
                }
                totals.merge(entry.getKey().toLowerCase(java.util.Locale.ROOT), entry.getValue(), Long::sum);
                marketKeys++;
            }
        }

        String note = "reason=" + reason
                + " players=" + players
                + " containers=" + containers
                + " skippedDupContainers=" + skippedDupContainers
                + " groundStacks=" + ground
                + " chunks=" + chunksSampled
                + " marketKeys=" + marketKeys
                + " distinct=" + totals.size();
        if (totals.isEmpty() && players == 0 && chunksSampled == 0 && marketKeys == 0) {
            plugin.getLogger().info("Item census empty startup snapshot skipped — " + note);
            return;
        }
        store.replaceAll(totals, note);
        plugin.getLogger().info("Item census updated — " + note);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.mysqlSync().saveIfChanged());
    }

    /**
     * Soft-hook RootMC-Shops Gen2 marketplace (storage bins + ACTIVE/RESERVED listings).
     * Call off the main thread — performs JDBC.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Long> fetchMarketplaceStock() {
        org.bukkit.plugin.Plugin shops = Bukkit.getPluginManager().getPlugin("Root-ChestShops");
        if (shops == null) {
            shops = Bukkit.getPluginManager().getPlugin("RootMC-Shops");
        }
        if (shops == null || !shops.isEnabled()) {
            return Map.of();
        }
        try {
            var method = shops.getClass().getMethod("marketplaceCensusByMaterialKey");
            Object result = method.invoke(shops);
            if (!(result instanceof Map<?, ?> map) || map.isEmpty()) {
                return Map.of();
            }
            Map<String, Long> out = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof Number qty)) {
                    continue;
                }
                long amount = qty.longValue();
                if (amount <= 0 || key.isBlank()) {
                    continue;
                }
                out.merge(key.toLowerCase(java.util.Locale.ROOT), amount, Long::sum);
            }
            return out;
        } catch (NoSuchMethodException ignored) {
            return Map.of();
        } catch (Exception ex) {
            plugin.getLogger().warning("Market census hook failed: " + ex.getMessage());
            return Map.of();
        }
    }

    private static long distanceToNearestPlayer(Chunk chunk) {
        long nearest = Long.MAX_VALUE / 2;
        for (Player player : chunk.getWorld().getPlayers()) {
            int dx = chunk.getX() - player.getChunk().getX();
            int dz = chunk.getZ() - player.getChunk().getZ();
            nearest = Math.min(nearest, (long) dx * dx + (long) dz * dz);
        }
        return nearest;
    }

    private static void addInventory(Map<String, Long> totals, Inventory inventory, Set<Inventory> seen) {
        if (inventory == null || !seen.add(inventory)) {
            return;
        }
        addInventoryContents(totals, inventory);
    }

    /** Count storage stacks once — PlayerInventory.getContents() already includes armor + offhand. */
    private static void addInventoryContents(Map<String, Long> totals, Inventory inventory) {
        if (inventory == null) {
            return;
        }
        ItemStack[] contents = inventory.getContents();
        if (contents == null) {
            return;
        }
        for (ItemStack stack : contents) {
            addStack(totals, stack);
            addNestedShulker(totals, stack);
        }
    }

    private static void addNestedShulker(Map<String, Long> totals, ItemStack stack) {
        if (stack == null || !stack.getType().name().endsWith("SHULKER_BOX")) {
            return;
        }
        if (!(stack.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta meta)) {
            return;
        }
        if (!(meta.getBlockState() instanceof Container container)) {
            return;
        }
        for (ItemStack inner : container.getInventory().getContents()) {
            addStack(totals, inner);
        }
    }

    private static void addStack(Map<String, Long> totals, ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return;
        }
        String key = RootItemInfoPlugin.materialKey(stack.getType());
        totals.merge(key, (long) stack.getAmount(), Long::sum);
    }

    public static boolean isGoldMaterial(Material material) {
        if (material == null) {
            return false;
        }
        return switch (material) {
            case GOLD_NUGGET, RAW_GOLD, GOLD_INGOT, GOLD_BLOCK, RAW_GOLD_BLOCK -> true;
            default -> false;
        };
    }

    public static double mintPegG(Material material) {
        if (material == null) {
            return 0;
        }
        return switch (material) {
            case GOLD_NUGGET -> 1.0 / 9.0;
            case RAW_GOLD, GOLD_INGOT -> 1.0;
            case GOLD_BLOCK, RAW_GOLD_BLOCK -> 9.0;
            default -> 0;
        };
    }
}
