package com.rootrecord.minecraft.rootmcshops;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Wall signs mounted on the chest face the player is looking at. */
public final class ShopSigns {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final BlockFace[] HORIZONTAL = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
    };

    private ShopSigns() {}

    public record SignPlacement(Block block, boolean migrated) {}

    public static boolean isSignMaterial(Material type) {
        if (type == null) {
            return false;
        }
        String name = type.name();
        return name.endsWith("_WALL_SIGN")
                || (name.endsWith("_SIGN") && !name.contains("HANGING"));
    }

    /** Chest face that points toward the player (where the wall sign mounts). */
    public static BlockFace chestFaceTowardPlayer(Player player) {
        return opposite(player.getFacing());
    }

    public static Material wallSignMaterial(ShopListing shop) {
        return shop != null && shop.isBuyShop() ? Material.CRIMSON_WALL_SIGN : Material.OAK_WALL_SIGN;
    }

    public static Block placeWallSignOnFace(Block chest, BlockFace chestFace) {
        return placeWallSignOnFace(chest, chestFace, Material.OAK_WALL_SIGN);
    }

    public static Block placeWallSignOnFace(Block chest, BlockFace chestFace, Material signMaterial) {
        if (chest == null || !ShopContainers.isShopContainer(chest.getType()) || !isHorizontalChestFace(chestFace)) {
            return null;
        }
        Block signBlock = chest.getRelative(chestFace);
        if (sameBlock(signBlock, chest)) {
            return null;
        }
        if (ShopContainers.isShopContainer(signBlock)) {
            return null;
        }
        if (!signBlock.getType().isAir() && !isSignMaterial(signBlock.getType())) {
            return null;
        }
        if (!signBlock.getType().isAir() && signBlock.getType() != signMaterial) {
            signBlock.setType(Material.AIR, false);
        }
        signBlock.setType(signMaterial);
        if (signBlock.getBlockData() instanceof WallSign wall) {
            wall.setFacing(chestFace);
            signBlock.setBlockData(wall);
        }
        return signBlock.getState() instanceof Sign ? signBlock : null;
    }

    public static Block placeWallSignFacingPlayer(Block chest, Player player) {
        return placeWallSignFacingPlayer(chest, player, Material.BIRCH_WALL_SIGN);
    }

    public static Block placeWallSignFacingPlayer(Block chest, Player player, Material signMaterial) {
        BlockFace toward = chestFaceTowardPlayer(player);
        Block placed = placeWallSignOnFace(chest, toward, signMaterial);
        if (placed != null) {
            return placed;
        }
        for (BlockFace face : HORIZONTAL) {
            if (face == toward) {
                continue;
            }
            placed = placeWallSignOnFace(chest, face, signMaterial);
            if (placed != null) {
                return placed;
            }
        }
        return null;
    }

    public static Block chestBlockForSign(Block signBlock) {
        if (signBlock == null || !isSignMaterial(signBlock.getType())) {
            return null;
        }
        if (signBlock.getBlockData() instanceof WallSign wall) {
            Block chest = signBlock.getRelative(wall.getFacing().getOppositeFace());
            if (ShopContainers.isShopContainer(chest)) {
                return chest;
            }
        }
        return null;
    }

    public static BlockFace chestFaceForSignBlock(Block chest, Block signBlock) {
        if (chest == null || signBlock == null) {
            return null;
        }
        int dx = signBlock.getX() - chest.getX();
        int dy = signBlock.getY() - chest.getY();
        int dz = signBlock.getZ() - chest.getZ();
        if (dy != 0 || Math.abs(dx) + Math.abs(dz) != 1) {
            return null;
        }
        if (dx == 1) {
            return BlockFace.EAST;
        }
        if (dx == -1) {
            return BlockFace.WEST;
        }
        if (dz == 1) {
            return BlockFace.SOUTH;
        }
        if (dz == -1) {
            return BlockFace.NORTH;
        }
        return null;
    }

    public static SignPlacement ensureSignOnChest(Block chest, ShopListing shop) {
        if (chest == null || shop == null) {
            return null;
        }
        Block existing = shop.signBlock();
        if (existing != null && isSignMaterial(existing.getType())) {
            BlockFace face = chestFaceForSignBlock(chest, existing);
            if (face != null) {
                return new SignPlacement(existing, false);
            }
        }

        BlockFace stored = existing == null ? null : chestFaceForSignBlock(chest, existing);
        Material signMaterial = wallSignMaterial(shop);
        Block placed = stored != null ? placeWallSignOnFace(chest, stored, signMaterial) : null;
        if (placed == null) {
            placed = placeWallSignOnFace(chest, BlockFace.NORTH, signMaterial);
        }
        if (placed == null) {
            for (BlockFace face : HORIZONTAL) {
                placed = placeWallSignOnFace(chest, face, signMaterial);
                if (placed != null) {
                    break;
                }
            }
        }
        if (placed == null) {
            return null;
        }
        boolean migrated = existing == null
                || placed.getX() != shop.signX()
                || placed.getY() != shop.signY()
                || placed.getZ() != shop.signZ();
        return new SignPlacement(placed, migrated);
    }

    public static void updateSign(RootMcShopsPlugin plugin, ShopListing shop) {
        updateSign(shop, plugin == null ? 0 : plugin.countStock(shop));
    }

    /** Same path as /shop reload  -  ensure sign exists, migrate coords, then paint stock. */
    public static ShopListing refreshListingSign(RootMcShopsPlugin plugin, ShopStore store, ShopListing shop) {
        if (plugin == null || store == null || shop == null) {
            return shop;
        }
        Block chest = ShopContainers.liveShopBlock(shop);
        if (chest == null) {
            return shop;
        }
        SignPlacement placement = ensureSignOnChest(chest, shop);
        if (placement == null) {
            return shop;
        }
        ShopListing current = shop;
        Block signBlock = placement.block();
        if (placement.migrated()) {
            current = shop.withSignBlock(signBlock.getX(), signBlock.getY(), signBlock.getZ());
            store.put(current);
        }
        updateSign(plugin, current);
        return current;
    }

    public static void updateSign(ShopListing shop, int stockCount) {
        Block block = shop.signBlock();
        if (block == null || !isSignMaterial(block.getType())) {
            return;
        }
        if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
            return;
        }
        ensureWallSignType(block, shop);
        if (!(block.getState() instanceof Sign sign)) {
            return;
        }
        String item = ShopItemKeys.prettyName(shop.itemKey());
        int qty = Math.max(1, shop.saleQty());
        String owner = shop.ownerName() != null ? shop.ownerName() : "shop";
        if (shop.isBuyShop()) {
            setLine(sign, 0, colored("\u00A7b\u00A7l", item, 14));
            setLine(sign, 1, colored("\u00A7c\u00A7lBUY \u00A76", String.format(Locale.US, "%.3f G", shop.price()), 14));
            setLine(sign, 2, colored("\u00A77", truncate(owner, 10) + " \u00A78x" + qty, 15));
            String stockLine = stockCount > 0 ? "\u00A7e\u00A7lwants " + stockCount : "\u00A7c\u00A7lfull";
            setLine(sign, 3, truncate(stockLine, 15));
        } else {
            setLine(sign, 0, colored("\u00A7a\u00A7l", item, 14));
            String price = qty > 1
                    ? String.format(Locale.US, "%.3f ea", shop.price())
                    : String.format(Locale.US, "%.3f G", shop.price());
            setLine(sign, 1, colored("\u00A76\u00A7l", price, 14));
            setLine(sign, 2, colored("\u00A77", truncate(owner, 10) + " \u00A78x" + qty, 15));
            String stockLine = stockCount > 0 ? "\u00A7a\u00A7l" + stockCount + " stock" : "\u00A7c\u00A7lOUT";
            setLine(sign, 3, truncate(stockLine, 15));
        }
        sign.update(true, false);
    }

    private static void ensureWallSignType(Block signBlock, ShopListing shop) {
        Material want = wallSignMaterial(shop);
        if (signBlock.getType() == want) {
            return;
        }
        if (!(signBlock.getBlockData() instanceof WallSign wall)) {
            return;
        }
        BlockFace facing = wall.getFacing();
        signBlock.setType(want);
        if (signBlock.getBlockData() instanceof WallSign updated) {
            updated.setFacing(facing);
            signBlock.setBlockData(updated);
        }
    }

    public static void clearSign(ShopListing shop) {
        Block block = shop.signBlock();
        Block chest = shop.block();
        removeSignBlock(block, chest);
    }

    public static boolean removeSignBlock(Block signBlock, Block chestBlock) {
        if (signBlock == null) {
            return true;
        }
        if (!isSignMaterial(signBlock.getType())) {
            return signBlock.getType().isAir();
        }
        if (chestBlock != null && sameBlock(signBlock, chestBlock)) {
            return false;
        }
        if (ShopContainers.isShopContainer(signBlock)) {
            return false;
        }
        signBlock.setType(Material.AIR, false);
        return true;
    }

    public static boolean clearSignsOnChestFaces(Block chest) {
        if (chest == null || !ShopContainers.isShopContainer(chest)) {
            return false;
        }
        boolean removed = false;
        for (BlockFace face : HORIZONTAL) {
            if (removeSignBlock(chest.getRelative(face), chest)) {
                removed = true;
            }
        }
        return removed;
    }

    private static boolean sameBlock(Block a, Block b) {
        return a.getWorld().equals(b.getWorld())
                && a.getX() == b.getX()
                && a.getY() == b.getY()
                && a.getZ() == b.getZ();
    }

    public static void markPendingSign(Block signBlock, String itemKey, int qty) {
        if (signBlock == null || !(signBlock.getState() instanceof Sign sign)) {
            return;
        }
        setLine(sign, 0, colored("\u00A7a\u00A7l", ShopItemKeys.prettyName(itemKey), 14));
        setLine(sign, 1, "\u00A7e\u00A7lprice");
        setLine(sign, 2, "\u00A77in chat");
        setLine(sign, 3, "\u00A78x" + qty);
        sign.update(true, false);
    }

    private static String colored(String prefix, String text, int maxVisible) {
        return prefix + truncate(text, maxVisible);
    }

    private static boolean isHorizontalChestFace(BlockFace face) {
        return face == BlockFace.NORTH
                || face == BlockFace.SOUTH
                || face == BlockFace.EAST
                || face == BlockFace.WEST;
    }

    private static void setLine(Sign sign, int index, String legacyText) {
        sign.getSide(Side.FRONT).line(index, legacy(legacyText));
    }

    private static Component legacy(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }

    private static BlockFace opposite(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.NORTH;
            case EAST -> BlockFace.WEST;
            case WEST -> BlockFace.EAST;
            default -> BlockFace.NORTH;
        };
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
