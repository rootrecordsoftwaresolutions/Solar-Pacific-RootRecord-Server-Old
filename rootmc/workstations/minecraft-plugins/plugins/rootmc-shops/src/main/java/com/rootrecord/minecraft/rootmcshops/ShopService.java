package com.rootrecord.minecraft.rootmcshops;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/** Create, update, and remove chest shops. */
public final class ShopService {

    private ShopService() {}

    public static boolean isOwner(ShopListing shop, Player player) {
        if (player == null || shop == null) {
            return false;
        }
        String ownerUuid = shop.ownerUuid();
        if (ownerUuid != null && !ownerUuid.isBlank()) {
            try {
                if (java.util.UUID.fromString(ownerUuid).equals(player.getUniqueId())) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                if (ownerUuid.equalsIgnoreCase(player.getUniqueId().toString())) {
                    return true;
                }
            }
        }
        String ownerName = shop.ownerName();
        return ownerName != null
                && !ownerName.isBlank()
                && ownerName.equalsIgnoreCase(player.getName());
    }

    public static boolean beginCreate(
            RootMcShopsPlugin plugin,
            ShopStore store,
            ShopInputManager inputs,
            Player player,
            Block chest,
            BlockFace clickedFace,
            Double immediateTotalPrice) {
        if (!player.hasPermission("rootshops.create")) {
            player.sendMessage(plugin.colorize("&cYou don't have permission to create shops."));
            return true;
        }
        if (!ShopContainers.isShopContainer(chest)) {
            return false;
        }
        if (store.getAt(chest.getWorld().getName(), chest.getX(), chest.getY(), chest.getZ()) != null) {
            player.sendMessage(plugin.msg("create-already-here"));
            return true;
        }
        ShopListing linkedShop = ShopProtection.shopForContainerBlock(store, chest);
        if (linkedShop != null) {
            if (!ShopService.isOwner(linkedShop, player) && !player.hasPermission("rootshops.admin")) {
                ShopProtection.deny(player, plugin, linkedShop);
                return true;
            }
            player.sendMessage(plugin.msg("create-already-part-of-shop"));
            return true;
        }
        if (inputs.has(player.getUniqueId())) {
            player.sendMessage(plugin.msg("create-already-pending"));
            return true;
        }

        if (plugin.requireShopPlot()
                && !player.hasPermission("rootshops.admin")
                && !ShopTownyAccess.allowsShopAt(chest.getLocation(), plugin.shopPlotTypeNames())) {
            player.sendMessage(plugin.msg("create-need-shop-plot"));
            return true;
        }

        if (adjacentChestBlocksCreate(chest, store)) {
            player.sendMessage(plugin.msg("create-adjacent-chest"));
            return true;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (isShopContainerItem(hand.getType())) {
            return false;
        }
        String itemKey = ShopStore.itemKeyFromStack(hand);
        if (itemKey == null) {
            player.sendMessage(plugin.msg("create-hold-item"));
            return true;
        }
        if (ShopItemKeys.isForbiddenGoldResource(hand)) {
            player.sendMessage(plugin.msg("forbidden-item"));
            return true;
        }

        Block signBlock = ShopSigns.placeWallSignFacingPlayer(chest, player);
        if (signBlock == null) {
            player.sendMessage(plugin.msg("create-need-sign-space"));
            return true;
        }

        int saleQty = Math.max(1, hand.getAmount());

        ShopSigns.markPendingSign(signBlock, itemKey, saleQty);
        ShopInputManager.Pending pending = ShopInputManager.Pending.create(
                chest.getWorld().getName(),
                chest.getX(),
                chest.getY(),
                chest.getZ(),
                signBlock.getX(),
                signBlock.getY(),
                signBlock.getZ(),
                itemKey,
                saleQty);

        if (immediateTotalPrice != null) {
            if (!finishCreate(plugin, store, player, pending, immediateTotalPrice)) {
                abortCreate(plugin, store, player, pending);
            }
            return true;
        }

        inputs.put(player.getUniqueId(), pending);

        player.sendMessage(plugin.msg("create-enter-price")
                .replace("{item}", itemKey.toLowerCase(Locale.ROOT).replace('_', ' '))
                .replace("{qty}", String.valueOf(saleQty)));
        player.sendMessage(plugin.msg("create-stock-hint"));
        return true;
    }

    public static void abortCreate(
            RootMcShopsPlugin plugin,
            ShopStore store,
            Player player,
            ShopInputManager.Pending pending) {
        var world = Bukkit.getWorld(pending.world());
        if (world != null) {
            int chunkX = pending.chestX() >> 4;
            int chunkZ = pending.chestZ() >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                player.sendMessage(plugin.msg("create-cancelled"));
                return;
            }
        }
        Block chest = pending.chestBlock(world);
        Block sign = pending.signBlock(world);
        boolean removed = ShopSigns.removeSignBlock(sign, chest);
        if (!removed && chest != null) {
            removed = ShopSigns.clearSignsOnChestFaces(chest);
        }
        if (!removed && chest != null && sign != null && !sign.getType().isAir()) {
            plugin.getLogger().warning(
                    "Shop create cancel: no sign removed for "
                            + player.getName()
                            + " at chest "
                            + pending.chestX()
                            + ","
                            + pending.chestY()
                            + ","
                            + pending.chestZ());
        }
        player.sendMessage(plugin.msg("create-cancelled"));
    }

    public static boolean finishCreate(
            RootMcShopsPlugin plugin,
            ShopStore store,
            Player player,
            ShopInputManager.Pending pending,
            double price) {
        if (price <= 0) {
            player.sendMessage(plugin.msg("create-invalid-price"));
            return false;
        }
        int saleQty = Math.max(1, pending.saleQty());
        double unitPrice = price / saleQty;
        if (unitPrice <= 0 || !Double.isFinite(unitPrice)) {
            player.sendMessage(plugin.msg("create-invalid-price"));
            return false;
        }
        if (!plugin.validatePrice(pending.itemKey(), unitPrice)) {
            plugin.sendPriceTooHigh(player, pending.itemKey(), unitPrice);
            return false;
        }
        String id = ShopStore.newId(pending.world(), pending.chestX(), pending.chestY(), pending.chestZ());
        ShopListing shop = new ShopListing(
                id,
                player.getUniqueId().toString(),
                player.getName(),
                pending.world(),
                pending.chestX(),
                pending.chestY(),
                pending.chestZ(),
                pending.itemKey(),
                unitPrice,
                "sell",
                saleQty,
                pending.signX(),
                pending.signY(),
                pending.signZ());
        store.put(shop);
        ShopSigns.updateSign(plugin, shop);
        ShopListingSync.onChanged(plugin, shop);
        player.sendMessage(plugin.msg("shop-created")
                .replace("{item}", pending.itemKey())
                .replace("{price}", String.format(Locale.US, "%.3f", unitPrice))
                .replace("{total}", String.format(Locale.US, "%.3f", price))
                .replace("{qty}", String.valueOf(saleQty)));
        return true;
    }

    public static boolean finishEditPrice(
            RootMcShopsPlugin plugin,
            ShopStore store,
            Player player,
            ShopInputManager.Pending pending,
            double price) {
        ShopListing existing = store.getById(pending.listingId());
        if (existing == null) {
            player.sendMessage(plugin.colorize("&eShop no longer exists."));
            return true;
        }
        if (!isOwner(existing, player) && !player.hasPermission("rootshops.admin")) {
            player.sendMessage(plugin.colorize("&cYou don't own this shop."));
            return true;
        }
        if (price <= 0) {
            player.sendMessage(plugin.msg("create-invalid-price"));
            return false;
        }
        int saleQty = Math.max(1, existing.saleQty());
        double unitPrice = price / saleQty;
        if (unitPrice <= 0 || !Double.isFinite(unitPrice)) {
            player.sendMessage(plugin.msg("create-invalid-price"));
            return false;
        }
        if (!plugin.validatePrice(existing.itemKey(), unitPrice)) {
            plugin.sendPriceTooHigh(player, existing.itemKey(), unitPrice);
            return false;
        }
        ShopListing updated = new ShopListing(
                existing.id(),
                existing.ownerUuid(),
                existing.ownerName(),
                existing.world(),
                existing.x(),
                existing.y(),
                existing.z(),
                existing.itemKey(),
                unitPrice,
                existing.normalizedType(),
                existing.saleQty(),
                existing.signX(),
                existing.signY(),
                existing.signZ());
        store.put(updated);
        ShopSigns.updateSign(plugin, updated);
        ShopListingSync.onChanged(plugin, updated);
        player.sendMessage(plugin.msg("shop-price-updated")
                .replace("{price}", String.format(Locale.US, "%.3f", unitPrice))
                .replace("{total}", String.format(Locale.US, "%.3f", price))
                .replace("{qty}", String.valueOf(saleQty)));
        return true;
    }

    public static boolean finishEditSaleQty(
            RootMcShopsPlugin plugin,
            ShopStore store,
            Player player,
            ShopInputManager.Pending pending,
            int saleQty) {
        ShopListing existing = store.getById(pending.listingId());
        if (existing == null) {
            player.sendMessage(plugin.colorize("&eShop no longer exists."));
            return true;
        }
        if (!isOwner(existing, player) && !player.hasPermission("rootshops.admin")) {
            player.sendMessage(plugin.colorize("&cYou don't own this shop."));
            return true;
        }
        if (saleQty < 1 || saleQty > 64) {
            player.sendMessage(plugin.msg("edit-invalid-qty"));
            return false;
        }
        int available = plugin.countStock(existing);
        if (available > 0 && saleQty > available) {
            player.sendMessage(plugin.msg("edit-qty-exceeds-stock")
                    .replace("{stock}", String.valueOf(available)));
            return false;
        }
        ShopListing updated = new ShopListing(
                existing.id(),
                existing.ownerUuid(),
                existing.ownerName(),
                existing.world(),
                existing.x(),
                existing.y(),
                existing.z(),
                existing.itemKey(),
                existing.price(),
                existing.normalizedType(),
                saleQty,
                existing.signX(),
                existing.signY(),
                existing.signZ());
        store.put(updated);
        ShopSigns.updateSign(plugin, updated);
        ShopListingSync.onChanged(plugin, updated);
        player.sendMessage(plugin.msg("shop-qty-updated")
                .replace("{qty}", String.valueOf(saleQty)));
        return true;
    }

    public static boolean setListingType(
            RootMcShopsPlugin plugin,
            ShopStore store,
            Player player,
            ShopListing existing,
            String requestedType) {
        if (existing == null) {
            player.sendMessage(plugin.colorize("&eShop no longer exists."));
            return true;
        }
        if (!isOwner(existing, player) && !player.hasPermission("rootshops.admin")) {
            player.sendMessage(plugin.colorize("&cYou don't own this shop."));
            return true;
        }
        String nextType = requestedType == null || requestedType.isBlank()
                ? (existing.isBuyShop() ? "sell" : "buy")
                : ShopListing.normalizeListingType(requestedType);
        if (nextType.equals(existing.normalizedType())) {
            player.sendMessage(plugin.msg("edit-type-unchanged").replace("{type}", nextType));
            return true;
        }
        ShopListing updated = existing.withListingType(nextType);
        store.put(updated);
        ShopSigns.updateSign(plugin, updated);
        ShopListingSync.onChanged(plugin, updated);
        player.sendMessage(plugin.msg("edit-type-updated")
                .replace("{type}", nextType)
                .replace("{hint}", nextType.equals("buy")
                        ? plugin.rawMsg("edit-type-buy-hint")
                        : plugin.rawMsg("edit-type-sell-hint")));
        return true;
    }

    public static void removeShop(RootMcShopsPlugin plugin, ShopStore store, ShopListing shop, Player player) {
        ShopListingSync.onRemoved(plugin, shop);
        Block container = shop.block();
        ShopSigns.clearSign(shop);
        if (container != null) {
            ShopSigns.clearSignsOnChestFaces(container);
        }
        store.remove(shop.id());
        if (player != null) {
            player.sendMessage(plugin.msg("shop-removed"));
            player.sendMessage(plugin.msg("shop-break-hint"));
        }
    }

    /** Remove listings whose anchor block is no longer a chest/barrel. */
    public static void pruneStaleListings(RootMcShopsPlugin plugin, ShopStore store) {
        int pruned = 0;
        for (ShopListing shop : java.util.List.copyOf(store.all())) {
            Block anchor = shop.block();
            if (anchor != null && ShopContainers.isShopContainer(anchor)) {
                continue;
            }
            ShopSigns.clearSign(shop);
            if (anchor != null) {
                ShopSigns.clearSignsOnChestFaces(anchor);
            }
            store.remove(shop.id());
            ShopListingSync.onRemoved(plugin, shop);
            pruned++;
        }
        if (pruned > 0) {
            plugin.getLogger().info("Pruned " + pruned + " stale shop listing(s) with missing containers.");
        }
    }

    public static boolean isShopContainerItem(Material type) {
        return type == Material.CHEST || type == Material.BARREL || type == Material.TRAPPED_CHEST;
    }

    /**
     * Block merging a new shop into a plain chest that already holds items (not an existing shop).
     * Does not block compact rows: barrels/trapped chests beside shops, or chests beside shop stock.
     */
    private static boolean adjacentChestBlocksCreate(Block chest, ShopStore store) {
        if (chest == null || chest.getType() != Material.CHEST) {
            return false;
        }
        Inventory selfInv = ShopContainers.containerInventory(chest);
        if (selfInv == null) {
            return false;
        }
        for (BlockFace face : new BlockFace[] {
                BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST }) {
            Block neighbor = chest.getRelative(face);
            if (neighbor.getType() != Material.CHEST) {
                continue;
            }
            if (ShopProtection.shopForContainerBlock(store, neighbor) != null) {
                continue;
            }
            Inventory neighborInv = ShopContainers.containerInventory(neighbor);
            if (neighborInv != null && inventoryHasItems(neighborInv) && selfInv.equals(neighborInv)) {
                return true;
            }
        }
        return false;
    }

    private static boolean inventoryHasItems(Inventory inv) {
        for (ItemStack stack : inv.getStorageContents()) {
            if (stack != null && !stack.getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    public static void refreshAllSigns(RootMcShopsPlugin plugin, ShopStore store) {
        for (ShopListing shop : store.all()) {
            try {
                ShopSigns.refreshListingSign(plugin, store, shop);
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not refresh shop sign " + shop.id() + ": " + ex.getMessage());
            }
        }
    }
}
