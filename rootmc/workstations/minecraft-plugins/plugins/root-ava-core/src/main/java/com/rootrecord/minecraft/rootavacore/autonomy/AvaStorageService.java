package com.rootrecord.minecraft.rootavacore.autonomy;

import com.rootrecord.minecraft.rootavacore.RootAvaCorePlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.TileState;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ava claim storage — categorized chests with PDC tags.
 */
public final class AvaStorageService {

    public enum Category {
        ORE,
        WOOD,
        STONE,
        FOOD,
        TOOL,
        MISC
    }

    private final RootAvaCorePlugin plugin;
    private final NamespacedKey catKey;

    public AvaStorageService(RootAvaCorePlugin plugin) {
        this.plugin = plugin;
        this.catKey = new NamespacedKey(plugin, "ava_storage");
    }

    public NamespacedKey categoryKey() {
        return catKey;
    }

    public static Category categorize(Material type) {
        if (type == null || type.isAir()) return Category.MISC;
        String n = type.name();
        if (n.contains("ORE") || n.contains("RAW_") || n.contains("INGOT") || n.contains("NUGGET")
                || type == Material.COAL || type == Material.CHARCOAL || type == Material.DIAMOND
                || type == Material.EMERALD || type == Material.LAPIS_LAZULI || type == Material.REDSTONE
                || type == Material.QUARTZ || type == Material.ANCIENT_DEBRIS || type == Material.NETHERITE_SCRAP
                || type == Material.AMETHYST_SHARD) {
            return Category.ORE;
        }
        if (Tag.LOGS.isTagged(type) || Tag.PLANKS.isTagged(type) || Tag.LEAVES.isTagged(type)
                || Tag.SAPLINGS.isTagged(type) || n.contains("WOOD") || type == Material.STICK) {
            return Category.WOOD;
        }
        if (type == Material.STONE || type == Material.COBBLESTONE || type == Material.DEEPSLATE
                || type == Material.COBBLED_DEEPSLATE || type == Material.GRANITE || type == Material.DIORITE
                || type == Material.ANDESITE || type == Material.TUFF || type == Material.CALCITE
                || type == Material.DIRT || type == Material.GRAVEL || type == Material.SAND
                || type == Material.NETHERRACK || type == Material.BASALT || type == Material.BLACKSTONE
                || n.endsWith("_STONE") || n.contains("BRICK")) {
            return Category.STONE;
        }
        if (type.isEdible() || n.contains("SEEDS") || type == Material.WHEAT
                || type == Material.CARROT || type == Material.POTATO || type == Material.BEETROOT
                || type == Material.APPLE || type == Material.BREAD) {
            return Category.FOOD;
        }
        if (n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE")
                || n.endsWith("_SWORD") || n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS") || type == Material.BOW
                || type == Material.CROSSBOW || type == Material.SHIELD || type == Material.BUCKET
                || type == Material.WATER_BUCKET || type == Material.LAVA_BUCKET) {
            return Category.TOOL;
        }
        return Category.MISC;
    }

    public boolean isAvaChest(Block block) {
        if (block == null || !(block.getState() instanceof TileState tile)) return false;
        return tile.getPersistentDataContainer().has(catKey, PersistentDataType.STRING);
    }

    public Category chestCategory(Block block) {
        if (!(block.getState() instanceof TileState tile)) return null;
        String raw = tile.getPersistentDataContainer().get(catKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return Category.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Category.MISC;
        }
    }

    public void tagChest(Block block, Category category) {
        if (!(block.getState() instanceof Chest chest)) return;
        chest.getPersistentDataContainer().set(catKey, PersistentDataType.STRING, category.name());
        chest.setCustomName("Ava · " + pretty(category));
        chest.update(true, false);
    }

    /**
     * Deposit stack into matching category chest; leftover returned (may be empty).
     */
    public ItemStack deposit(ItemStack stack, List<Block> chests) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return null;
        }
        Category want = categorize(stack.getType());
        ItemStack remaining = stack.clone();
        // Prefer matching category, then MISC, then any Ava chest
        remaining = tryDepositInto(remaining, chests, want);
        if (remaining != null && remaining.getAmount() > 0 && want != Category.MISC) {
            remaining = tryDepositInto(remaining, chests, Category.MISC);
        }
        if (remaining != null && remaining.getAmount() > 0) {
            remaining = tryDepositInto(remaining, chests, null);
        }
        return remaining != null && remaining.getAmount() > 0 ? remaining : null;
    }

    /** Count items of a material across Ava chests. */
    public int countMaterial(List<Block> chests, Material type) {
        if (type == null || chests == null) return 0;
        int n = 0;
        for (Block b : chests) {
            if (!(b.getState() instanceof Chest chest)) continue;
            for (ItemStack stack : chest.getBlockInventory().getContents()) {
                if (stack != null && stack.getType() == type) {
                    n += stack.getAmount();
                }
            }
        }
        return n;
    }

    public int countCategory(List<Block> chests, Category category) {
        if (category == null || chests == null) return 0;
        int n = 0;
        for (Block b : chests) {
            if (!(b.getState() instanceof Chest chest)) continue;
            for (ItemStack stack : chest.getBlockInventory().getContents()) {
                if (stack == null || stack.getType().isAir()) continue;
                if (categorize(stack.getType()) == category) {
                    n += stack.getAmount();
                }
            }
        }
        return n;
    }

    public Material findAnyInCategory(List<Block> chests, Category category) {
        if (category == null) return null;
        for (Block b : chests) {
            if (!(b.getState() instanceof Chest chest)) continue;
            for (ItemStack stack : chest.getBlockInventory().getContents()) {
                if (stack == null || stack.getType().isAir()) continue;
                if (categorize(stack.getType()) == category) {
                    return stack.getType();
                }
            }
        }
        return null;
    }

    /**
     * Remove up to {@code amount} of the first matching preferred material.
     * @return amount actually withdrawn
     */
    public int withdraw(List<Block> chests, List<Material> prefer, int amount) {
        if (chests == null || prefer == null || amount <= 0) return 0;
        int need = amount;
        int taken = 0;
        for (Material want : prefer) {
            if (need <= 0) break;
            for (Block b : chests) {
                if (need <= 0) break;
                if (!(b.getState() instanceof Chest chest)) continue;
                Inventory inv = chest.getBlockInventory();
                ItemStack[] contents = inv.getContents();
                for (int i = 0; i < contents.length && need > 0; i++) {
                    ItemStack stack = contents[i];
                    if (stack == null || stack.getType() != want) continue;
                    int use = Math.min(need, stack.getAmount());
                    stack.setAmount(stack.getAmount() - use);
                    if (stack.getAmount() <= 0) {
                        inv.setItem(i, null);
                    }
                    need -= use;
                    taken += use;
                }
                chest.update(true, false);
            }
        }
        return taken;
    }

    private ItemStack tryDepositInto(ItemStack stack, List<Block> chests, Category filter) {
        ItemStack remaining = stack;
        for (Block b : chests) {
            if (remaining == null || remaining.getAmount() <= 0) return null;
            Category cat = chestCategory(b);
            if (filter != null && cat != filter) continue;
            if (!(b.getState() instanceof Chest chest)) continue;
            Inventory inv = chest.getBlockInventory();
            Map<Integer, ItemStack> overflow = inv.addItem(remaining);
            if (overflow.isEmpty()) {
                chest.update(true, false);
                return null;
            }
            remaining = overflow.values().iterator().next();
            chest.update(true, false);
        }
        return remaining;
    }

    public List<Block> findAvaChests(Location center, int radius) {
        List<Block> out = new ArrayList<>();
        if (center == null || center.getWorld() == null) return out;
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int r = Math.max(4, radius);
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                for (int y = cy - 8; y <= cy + 8; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    if (b.getType() != Material.CHEST && b.getType() != Material.TRAPPED_CHEST
                            && b.getType() != Material.BARREL) {
                        continue;
                    }
                    if (isAvaChest(b) || b.getType() == Material.CHEST || b.getType() == Material.BARREL) {
                        // Claim any unlabeled chest Ava owns/uses in scan — tag later if needed
                        if (isAvaChest(b)) {
                            out.add(b);
                        }
                    }
                }
            }
        }
        return out;
    }

    public Map<Category, Integer> countByCategory(List<Block> chests) {
        Map<Category, Integer> map = new EnumMap<>(Category.class);
        for (Category c : Category.values()) map.put(c, 0);
        for (Block b : chests) {
            Category c = chestCategory(b);
            if (c == null) continue;
            map.put(c, map.get(c) + 1);
        }
        return map;
    }

    /** Dirt/stone/grass — never chests, barrels, or other inventory blocks. */
    public static boolean isTerrainSupport(Material type) {
        if (type == null || type.isAir() || !type.isSolid()) {
            return false;
        }
        String n = type.name();
        if (type == Material.CHEST
                || type == Material.TRAPPED_CHEST
                || type == Material.BARREL
                || type == Material.ENDER_CHEST
                || type == Material.HOPPER
                || type == Material.DROPPER
                || type == Material.DISPENSER
                || n.endsWith("_SHULKER_BOX")
                || n.endsWith("_SIGN")
                || n.contains("BANNER")
                || n.contains("DOOR")
                || n.contains("FENCE")
                || n.contains("SLAB")
                || n.contains("STAIRS")
                || n.contains("CARPET")) {
            return false;
        }
        return true;
    }

    /**
     * Highest terrain block in this column near {@code yHint}, or null.
     */
    public static Block findTerrainSurface(World world, int x, int yHint, int z) {
        if (world == null) return null;
        int top = Math.min(world.getMaxHeight() - 2, yHint + 4);
        int bottom = Math.max(world.getMinHeight() + 1, yHint - 16);
        for (int y = top; y >= bottom; y--) {
            Block ground = world.getBlockAt(x, y, z);
            if (!isTerrainSupport(ground.getType())) {
                continue;
            }
            Block above = ground.getRelative(0, 1, 0);
            if (!above.getType().isAir()) {
                continue;
            }
            return ground;
        }
        return null;
    }

    /**
     * Place a single chest on real terrain near center; tag with category.
     * Never stacks on chests or hangs in air.
     * @return placed block or null
     */
    public Block placeChest(Location center, Category category, Object claim) {
        if (center == null || center.getWorld() == null) return null;
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int[][] offsets = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {2, 0}, {-2, 0}, {0, 2}, {0, -2},
            {1, 1}, {-1, -1}, {2, 1}, {-2, 1}, {1, 2}, {-1, 2}
        };
        for (int[] off : offsets) {
            int x = cx + off[0];
            int z = cz + off[1];
            Block ground = findTerrainSurface(world, x, cy, z);
            if (ground == null) continue;
            Block air = ground.getRelative(0, 1, 0);
            if (!air.getType().isAir()) continue;
            if (claim != null && !claimContains(claim, air.getLocation())) continue;
            boolean columnHasChest = false;
            for (int y = ground.getY() + 1; y <= ground.getY() + 2; y++) {
                Material t = world.getBlockAt(x, y, z).getType();
                if (t == Material.CHEST || t == Material.TRAPPED_CHEST || t == Material.BARREL) {
                    columnHasChest = true;
                    break;
                }
            }
            if (columnHasChest) continue;
            air.setType(Material.CHEST);
            tagChest(air, category);
            plugin.getLogger().info("Ava autonomy · placed " + category + " chest at "
                    + air.getX() + "," + air.getY() + "," + air.getZ());
            return air;
        }
        return null;
    }

    private boolean claimContains(Object claim, Location loc) {
        try {
            Object v = claim.getClass().getMethod("contains", Location.class).invoke(claim, loc);
            return Boolean.TRUE.equals(v);
        } catch (Throwable t) {
            return false;
        }
    }

    private static String pretty(Category c) {
        String n = c.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
