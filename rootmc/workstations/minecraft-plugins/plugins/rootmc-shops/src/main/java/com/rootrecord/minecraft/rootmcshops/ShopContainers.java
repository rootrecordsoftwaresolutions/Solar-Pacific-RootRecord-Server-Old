package com.rootrecord.minecraft.rootmcshops;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

/** Chests and barrels that can hold shop stock. */
public final class ShopContainers {

    @FunctionalInterface
    public interface StockChangeListener {
        void onStockChanged(ShopListing shop);
    }

    private static volatile StockChangeListener stockChangeListener;

    private ShopContainers() {}

    public static void setStockChangeListener(StockChangeListener listener) {
        stockChangeListener = listener;
    }

    private static void notifyStockChanged(ShopListing shop) {
        StockChangeListener listener = stockChangeListener;
        if (listener != null && shop != null) {
            listener.onStockChanged(shop);
        }
    }

    public static boolean isShopContainer(Material type) {
        return type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL;
    }

    public static boolean isShopContainer(Block block) {
        return block != null && isShopContainer(block.getType());
    }

    public static Container containerState(Block block) {
        if (block != null && block.getState() instanceof Container container) {
            return container;
        }
        return null;
    }

    /** Live world inventory for this container (not a stale BlockState snapshot). Never call {@link BlockState#update} after edits. */
    public static Inventory containerInventory(Block block) {
        return liveContainerInventory(block);
    }

    private static Inventory liveContainerInventory(Block block) {
        if (block == null) {
            return null;
        }
        BlockState state = block.getState();
        if (state instanceof Chest chest) {
            return chest.getInventory();
        }
        if (state instanceof Barrel barrel) {
            return barrel.getInventory();
        }
        if (state instanceof Container container) {
            return container.getInventory();
        }
        return null;
    }

    public static Block liveShopBlock(ShopListing shop) {
        return liveShopBlock(shop, false);
    }

    /** @param loadChunk when true, loads the shop chunk so /buy can see distant listings */
    public static Block liveShopBlock(ShopListing shop, boolean loadChunk) {
        if (shop == null) {
            return null;
        }
        World world = org.bukkit.Bukkit.getWorld(shop.world());
        if (world == null) {
            return null;
        }
        int chunkX = shop.x() >> 4;
        int chunkZ = shop.z() >> 4;
        try {
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                if (!loadChunk) {
                    return null;
                }
                world.getChunkAt(chunkX, chunkZ);
            }
            org.bukkit.Chunk chunk = world.getChunkAt(chunkX, chunkZ, false);
            if (chunk == null || !chunk.isLoaded()) {
                return null;
            }
            Block block = world.getBlockAt(shop.x(), shop.y(), shop.z());
            return isShopContainer(block) ? block : null;
        } catch (IllegalStateException ex) {
            return null;
        }
    }

    public static Inventory shopInventory(ShopListing shop) {
        return shopInventory(shop, false);
    }

    public static Inventory shopInventory(ShopListing shop, boolean loadChunk) {
        return liveContainerInventory(liveShopBlock(shop, loadChunk));
    }

    public static ItemStack firstMatchingStack(ShopListing shop, String itemKey) {
        return firstMatchingStack(shop, itemKey, false);
    }

    public static ItemStack firstMatchingStack(ShopListing shop, String itemKey, boolean loadChunk) {
        Inventory inv = shopInventory(shop, loadChunk);
        if (inv == null || itemKey == null) {
            return null;
        }
        for (ItemStack stack : inv.getContents()) {
            if (ShopItemKeys.matches(stack, itemKey)) {
                return stack.clone();
            }
        }
        return null;
    }

    /** Removes one matching item and returns a single-item clone (preserves custom NBT such as bond ids). */
    public static ItemStack withdrawOneMatching(ShopListing shop, String itemKey, boolean loadChunk) {
        if (shop == null || itemKey == null || itemKey.isBlank()) {
            return null;
        }
        Block block = liveShopBlock(shop, loadChunk);
        Inventory inv = liveContainerInventory(block);
        if (inv == null) {
            return null;
        }
        ItemStack[] contents = inv.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (!ShopItemKeys.matches(stack, itemKey)) {
                continue;
            }
            ItemStack out = stack.clone();
            out.setAmount(1);
            if (stack.getAmount() <= 1) {
                contents[slot] = null;
            } else {
                stack.setAmount(stack.getAmount() - 1);
            }
            inv.setContents(contents);
            notifyStockChanged(shop);
            return out;
        }
        return null;
    }

    /** Removes one matching item from a player's storage inventory. */
    public static ItemStack withdrawOneFromPlayer(org.bukkit.entity.Player player, String itemKey) {
        if (player == null || itemKey == null || itemKey.isBlank()) {
            return null;
        }
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (!ShopItemKeys.matches(stack, itemKey)) {
                continue;
            }
            ItemStack out = stack.clone();
            out.setAmount(1);
            if (stack.getAmount() <= 1) {
                contents[slot] = null;
            } else {
                stack.setAmount(stack.getAmount() - 1);
            }
            player.getInventory().setStorageContents(contents);
            return out;
        }
        return null;
    }

    public static boolean depositOne(ShopListing shop, ItemStack stack) {
        if (shop == null || stack == null || stack.getType().isAir()) {
            return false;
        }
        Block block = liveShopBlock(shop, true);
        Inventory inv = liveContainerInventory(block);
        if (inv == null) {
            return false;
        }
        ItemStack one = stack.clone();
        one.setAmount(1);
        if (!inv.addItem(one).isEmpty()) {
            return false;
        }
        notifyStockChanged(shop);
        return true;
    }

    public static int countMatchingItems(ShopListing shop, String itemKey) {
        return countMatchingItems(shop, itemKey, false);
    }

    public static int countMatchingItems(ShopListing shop, String itemKey, boolean loadChunk) {
        if (shop == null || itemKey == null || itemKey.isBlank()) {
            return 0;
        }
        Inventory inv = shopInventory(shop, loadChunk);
        return inv == null ? 0 : countInInventory(inv, itemKey);
    }

  /**
     * Removes items from the live tile inventory. Do not call {@link BlockState#update} afterward —
     * on Paper that can re-apply a stale snapshot and restore stock (dupe).
     */
    public static boolean withdrawMatchingItems(ShopListing shop, String itemKey, int quantity) {
        if (shop == null || itemKey == null || itemKey.isBlank() || quantity <= 0) {
            return false;
        }
        Block block = liveShopBlock(shop, true);
        Inventory inv = liveContainerInventory(block);
        if (inv == null) {
            return false;
        }
        int before = countInInventory(inv, itemKey);
        if (before < quantity) {
            return false;
        }
        int removed = 0;
        ItemStack[] contents = inv.getContents();
        for (int slot = 0; slot < contents.length && removed < quantity; slot++) {
            ItemStack stack = contents[slot];
            if (!ShopItemKeys.matches(stack, itemKey)) {
                continue;
            }
            int take = Math.min(quantity - removed, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) {
                contents[slot] = null;
            }
            removed += take;
        }
        inv.setContents(contents);
        notifyStockChanged(shop);
        return removed == quantity;
    }

    /** Returns items to the shop container (e.g. rollback after a failed payment). */
    public static boolean depositMatchingItems(ShopListing shop, String itemKey, int quantity, ItemStack template) {
        if (shop == null || itemKey == null || itemKey.isBlank() || quantity <= 0) {
            return false;
        }
        Block block = liveShopBlock(shop, true);
        Inventory inv = liveContainerInventory(block);
        if (inv == null) {
            return false;
        }
        Material mat = ShopItemKeys.baseMaterial(itemKey);
        if (mat == null || mat.isAir()) {
            return false;
        }
        int remaining = quantity;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, mat.getMaxStackSize());
            ItemStack stack = template != null ? template.clone() : new ItemStack(mat, stackSize);
            stack.setAmount(stackSize);
            HashMap<Integer, ItemStack> leftover = inv.addItem(stack);
            if (!leftover.isEmpty()) {
                return false;
            }
            remaining -= stackSize;
        }
        notifyStockChanged(shop);
        return true;
    }

    public static int countBuyCapacity(Inventory inv, String itemKey) {
        Material mat = ShopItemKeys.baseMaterial(itemKey);
        if (inv == null || mat == null || mat.isAir()) {
            return 0;
        }
        int maxStack = mat.getMaxStackSize();
        int capacity = 0;
        for (ItemStack stack : inv.getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                capacity += maxStack;
            } else if (ShopItemKeys.matches(stack, itemKey)) {
                capacity += maxStack - stack.getAmount();
            }
        }
        return capacity;
    }

    private static int countInInventory(Inventory inv, String itemKey) {
        int count = 0;
        for (ItemStack stack : inv.getContents()) {
            if (ShopItemKeys.matches(stack, itemKey)) {
                count += stack.getAmount();
            }
        }
        return count;
    }
}
