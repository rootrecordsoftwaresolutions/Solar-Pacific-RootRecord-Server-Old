package com.rootrecord.minecraft.rootmcshops;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/** Ownership checks and shop block lookups for grief protection. */
public final class ShopProtection {

    private static final BlockFace[] HORIZONTAL = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
    };

    private static final BlockFace[] ADJACENT = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
            BlockFace.UP, BlockFace.DOWN,
    };

    private static final Material[] LOGISTICS = {
            Material.HOPPER,
            Material.DROPPER,
            Material.DISPENSER,
    };

    private ShopProtection() {}

    public static boolean canManage(Player player, ShopListing shop) {
        return ShopService.isOwner(shop, player) || player.hasPermission("rootshops.admin");
    }

    /** Listing registered exactly on this block (not double-chest partner inference). */
    public static ShopListing shopAnchorAt(ShopStore store, Block block) {
        if (block == null) {
            return null;
        }
        return store.getAt(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    /** Break/sign protection — anchor container or shop sign only. */
    public static ShopListing shopProtectingBlockForBreak(ShopStore store, Block block) {
        if (block == null) {
            return null;
        }
        ShopListing anchor = shopAnchorAt(store, block);
        if (anchor != null && ShopContainers.isShopContainer(block)) {
            return anchor;
        }
        return store.getBySignBlock(block);
    }

    /** Hopper/dropper placement guard — horizontal neighbors of shop anchors/signs only. */
    public static ShopListing shopNearAnchor(ShopStore store, Block block) {
        if (block == null) {
            return null;
        }
        ShopListing direct = shopProtectingBlockForBreak(store, block);
        if (direct != null) {
            return direct;
        }
        for (BlockFace face : ADJACENT) {
            ShopListing neighbor = shopProtectingBlockForBreak(store, block.getRelative(face));
            if (neighbor != null) {
                return neighbor;
            }
        }
        return null;
    }

    /** Hopper/dropper/dispenser directly touching a shop container (incl. double-chest partner) or sign. */
    public static boolean logisticsAttachedToShop(ShopListing shop, Block logisticsBlock) {
        if (shop == null || logisticsBlock == null || !isLogisticsBlock(logisticsBlock.getType())) {
            return false;
        }
        return pipePeerTouchesShop(shop, logisticsBlock);
    }

    /**
     * Allows owner restock pipes: block hoppers/droppers/dispensers and hopper minecarts
     * touching the shop chest (or its double-chest half) / sign.
     */
    public static boolean allowsShopPipeTransfer(ShopListing shop, org.bukkit.inventory.Inventory otherInventory) {
        if (shop == null || otherInventory == null) {
            return false;
        }
        org.bukkit.inventory.InventoryHolder holder = otherInventory.getHolder();
        if (holder instanceof org.bukkit.entity.minecart.HopperMinecart cart) {
            return pipePeerTouchesShop(shop, cart.getLocation().getBlock());
        }
        if (holder instanceof org.bukkit.block.BlockState state) {
            Block block = state.getBlock();
            if (!isLogisticsBlock(block.getType())) {
                return false;
            }
            return pipePeerTouchesShop(shop, block);
        }
        return false;
    }

    /** True when {@code peer} is adjacent to the shop anchor, shared double-chest half, or sign. */
    public static boolean pipePeerTouchesShop(ShopListing shop, Block peer) {
        if (shop == null || peer == null) {
            return false;
        }
        Block anchor = shop.block();
        if (anchor != null) {
            if (blocksAdjacent(anchor, peer)) {
                return true;
            }
            Inventory inv = ShopContainers.containerInventory(anchor);
            for (BlockFace face : HORIZONTAL) {
                Block other = anchor.getRelative(face);
                if (!ShopContainers.isShopContainer(other)) {
                    continue;
                }
                Inventory otherInv = ShopContainers.containerInventory(other);
                if (inv != null && otherInv != null && inv.equals(otherInv) && blocksAdjacent(other, peer)) {
                    return true;
                }
            }
        }
        Block sign = shop.signBlock();
        return sign != null && blocksAdjacent(sign, peer);
    }

    private static boolean blocksAdjacent(Block a, Block b) {
        if (a == null || b == null || !a.getWorld().equals(b.getWorld())) {
            return false;
        }
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ()) == 1;
    }

    public static ShopListing shopProtectingBlock(ShopStore store, Block block) {
        return shopProtectingBlockForBreak(store, block);
    }

    /** Drops listings whose container block is gone or no longer a chest/barrel. */
    public static ShopListing resolveShopListing(ShopStore store, ShopListing shop, RootMcShopsPlugin plugin) {
        if (shop == null) {
            return null;
        }
        Block anchor = shop.block();
        if (anchor != null && ShopContainers.isShopContainer(anchor)) {
            return shop;
        }
        Block sign = shop.signBlock();
        if (sign != null && ShopSigns.isSignMaterial(sign.getType())) {
            return shop;
        }
        store.remove(shop.id());
        ShopListingSync.onRemoved(plugin, shop);
        if (plugin != null) {
            plugin.getLogger().info("Pruned stale shop listing " + shop.id());
        }
        return null;
    }

    /** Shop registered on this container block, or its double-chest partner. */
    public static ShopListing shopForContainerBlock(ShopStore store, Block block) {
        if (block == null || !ShopContainers.isShopContainer(block)) {
            return null;
        }
        String world = block.getWorld().getName();
        ShopListing direct = store.getAt(world, block.getX(), block.getY(), block.getZ());
        if (direct != null) {
            return direct;
        }
        if (block.getType() != Material.CHEST) {
            return null;
        }
        Inventory inv = ShopContainers.containerInventory(block);
        for (BlockFace face : HORIZONTAL) {
            Block other = block.getRelative(face);
            if (other.getType() != Material.CHEST) {
                continue;
            }
            ShopListing linked = store.getAt(world, other.getX(), other.getY(), other.getZ());
            if (linked == null) {
                continue;
            }
            Inventory otherInv = ShopContainers.containerInventory(other);
            if (inv != null && otherInv != null && inv.equals(otherInv)) {
                return linked;
            }
        }
        return null;
    }

    public static ShopListing shopForInventory(ShopStore store, Inventory inventory) {
        List<ShopListing> shops = shopsForInventory(store, inventory);
        return shops.isEmpty() ? null : shops.get(0);
    }

    public static List<ShopListing> shopsForInventory(ShopStore store, Inventory inventory) {
        if (inventory == null) {
            return List.of();
        }
        List<ShopListing> holderShops = shopsForContainerInventory(store, inventory);
        if (!holderShops.isEmpty()) {
            return holderShops;
        }
        ShopListing logisticsShop = shopForLogisticsInventory(store, inventory);
        if (logisticsShop != null) {
            return List.of(logisticsShop);
        }
        return List.of();
    }

    /** All shop listings tied to this container inventory (both halves of a double chest). */
    public static List<ShopListing> shopsForContainerInventory(ShopStore store, Inventory inventory) {
        if (inventory == null) {
            return List.of();
        }
        org.bukkit.inventory.InventoryHolder holder = inventory.getHolder();
        if (holder == null) {
            return List.of();
        }
        if (holder instanceof org.bukkit.block.DoubleChest doubleChest) {
            java.util.ArrayList<ShopListing> shops = new java.util.ArrayList<>(2);
            addDistinctShop(shops, shopForContainerHolder(store, doubleChest.getLeftSide()));
            addDistinctShop(shops, shopForContainerHolder(store, doubleChest.getRightSide()));
            return List.copyOf(shops);
        }
        ShopListing one = shopForContainerHolder(store, holder);
        return one != null ? List.of(one) : List.of();
    }

    private static void addDistinctShop(java.util.List<ShopListing> shops, ShopListing shop) {
        if (shop == null) {
            return;
        }
        for (ShopListing existing : shops) {
            if (existing.id().equals(shop.id())) {
                return;
            }
        }
        shops.add(shop);
    }

    /** First shop on this container inventory (left half of a double chest wins). */
    public static ShopListing shopForInventoryHolder(ShopStore store, Inventory inventory) {
        List<ShopListing> shops = shopsForContainerInventory(store, inventory);
        return shops.isEmpty() ? null : shops.get(0);
    }

    public static ShopListing shopForLogisticsInventory(ShopStore store, Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        org.bukkit.inventory.InventoryHolder holder = inventory.getHolder();
        if (!(holder instanceof org.bukkit.block.TileState state)) {
            return null;
        }
        Block block = state.getBlock();
        if (!isLogisticsBlock(block.getType())) {
            return null;
        }
        return shopAdjacentToLogistics(store, block);
    }

    private static ShopListing shopAdjacentToLogistics(ShopStore store, Block logisticsBlock) {
        for (BlockFace face : ADJACENT) {
            Block neighbor = logisticsBlock.getRelative(face);
            ShopListing containerShop = shopForContainerBlock(store, neighbor);
            if (containerShop != null) {
                return containerShop;
            }
            ShopListing signShop = store.getBySignBlock(neighbor);
            if (signShop != null) {
                return signShop;
            }
        }
        return null;
    }

    private static ShopListing shopForContainerHolder(
            ShopStore store,
            org.bukkit.inventory.InventoryHolder holder) {
        if (!(holder instanceof org.bukkit.block.Container container)) {
            return null;
        }
        Block block = container.getBlock();
        if (!ShopContainers.isShopContainer(block)) {
            return null;
        }
        ShopListing anchor = shopAnchorAt(store, block);
        if (anchor != null) {
            return anchor;
        }
        return shopForContainerBlock(store, block);
    }

    /**
     * Shop whose container or sign is horizontally adjacent (hopper/chest placement guard).
     * @deprecated use {@link #shopNearAnchor(ShopStore, Block)} for logistics placement
     */
    public static ShopListing shopTouchingBlock(ShopStore store, Block block) {
        return shopNearAnchor(store, block);
    }

    public static boolean isLogisticsBlock(Material type) {
        if (type == null) {
            return false;
        }
        for (Material logistics : LOGISTICS) {
            if (type == logistics) {
                return true;
            }
        }
        return false;
    }

    public static void deny(Player player, RootMcShopsPlugin plugin, ShopListing shop) {
        String owner = shop.ownerName() != null && !shop.ownerName().isBlank()
                ? shop.ownerName()
                : "another player";
        player.sendMessage(plugin.msg("protect-deny").replace("{owner}", owner));
    }
}
