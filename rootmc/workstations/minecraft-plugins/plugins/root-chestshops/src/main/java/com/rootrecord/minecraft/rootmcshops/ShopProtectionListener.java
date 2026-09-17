package com.rootrecord.minecraft.rootmcshops;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;

/** Prevents griefing and inventory exploits on registered shop anchors only. */
public final class ShopProtectionListener implements Listener {

    private final RootMcShopsPlugin plugin;
    private final ShopStore store;
    private final ShopEconomy economy;
    private final ShopInputManager inputs;

    public ShopProtectionListener(
            RootMcShopsPlugin plugin,
            ShopStore store,
            ShopEconomy economy,
            ShopInputManager inputs) {
        this.plugin = plugin;
        this.store = store;
        this.economy = economy;
        this.inputs = inputs;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (!ShopProtection.isLogisticsBlock(event.getBlockPlaced().getType())) {
            return;
        }
        ShopListing nearShop = ShopProtection.shopNearAnchor(store, event.getBlockPlaced());
        if (nearShop != null && !ShopProtection.canManage(player, nearShop)) {
            event.setCancelled(true);
            ShopProtection.deny(player, plugin, nearShop);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Block block = event.getBlock();

        clearPendingCreateIfMatches(player, block);

        ShopListing shop = ShopProtection.shopProtectingBlockForBreak(store, block);
        shop = ShopProtection.resolveShopListing(store, shop, plugin);
        if (shop == null) {
            return;
        }
        if (ShopProtection.canManage(player, shop)) {
            ShopService.removeShop(plugin, store, shop, player);
            inputs.clear(player.getUniqueId());
            event.setCancelled(false);
            return;
        }
        event.setCancelled(true);
        ShopProtection.deny(player, plugin, shop);
    }

    private void clearPendingCreateIfMatches(Player player, Block block) {
        ShopInputManager.Pending pending = inputs.get(player.getUniqueId());
        if (pending == null || pending.kind() != ShopInputManager.Kind.CREATE_PRICE) {
            return;
        }
        if (!pending.world().equals(block.getWorld().getName())) {
            return;
        }
        Block chest = pending.chestBlock(block.getWorld());
        Block sign = pending.signBlock(block.getWorld());
        boolean matchesChest = chest != null && sameBlock(chest, block);
        boolean matchesSign = sign != null && sameBlock(sign, block);
        if (!matchesChest && !matchesSign) {
            return;
        }
        inputs.remove(player.getUniqueId());
        if (matchesChest && chest != null) {
            ShopSigns.clearSignsOnChestFaces(chest);
        } else if (matchesSign) {
            ShopSigns.removeSignBlock(sign, chest);
        }
    }

    private static boolean sameBlock(Block a, Block b) {
        return a.getX() == b.getX() && a.getY() == b.getY() && a.getZ() == b.getZ() && a.getWorld().equals(b.getWorld());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (event.getInventory().getType() == InventoryType.PLAYER) {
            return;
        }

        ShopListing shop = ShopProtection.shopForInventoryHolder(store, event.getInventory());
        if (shop == null) {
            return;
        }
        if (ShopProtection.canManage(player, shop)) {
            return;
        }

        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (shop.isBuyShop()) {
                ShopSellService.offerSale(player, plugin, economy, shop);
            } else {
                ShopBuyService.offerPurchase(player, plugin, economy, shop);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (pistonWouldMoveProtectedBlock(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (pistonWouldMoveProtectedBlock(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (ShopProtection.shopProtectingBlockForBreak(store, event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (ShopProtection.shopProtectingBlockForBreak(store, event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> ShopProtection.shopProtectingBlockForBreak(store, block) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> ShopProtection.shopProtectingBlockForBreak(store, block) != null);
    }

    private boolean pistonWouldMoveProtectedBlock(java.util.List<Block> blocks, BlockFace direction) {
        for (Block block : blocks) {
            if (ShopProtection.shopProtectingBlockForBreak(store, block) != null) {
                return true;
            }
            if (ShopProtection.shopProtectingBlockForBreak(store, block.getRelative(direction)) != null) {
                return true;
            }
        }
        return false;
    }
}
