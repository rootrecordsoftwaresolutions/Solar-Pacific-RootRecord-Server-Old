package com.rootrecord.minecraft.rootappreciation;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Right-click Appreciation Token to redeem one weighted reward. */
public final class AppreciationTokenListener implements Listener {

    private final RootAppreciationPlugin plugin;

    public AppreciationTokenListener(RootAppreciationPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        // Let chest shops / containers / signs handle their own right-clicks.
        if (action == Action.RIGHT_CLICK_BLOCK && isShopOrContainerClick(event.getClickedBlock())) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (!plugin.tokens().isAppreciationToken(stack)) {
            return;
        }
        if (!plugin.config().enabled() || !plugin.config().redeemEnabled()) {
            return;
        }
        if (!player.hasPermission("rootappreciation.redeem")) {
            return;
        }
        event.setCancelled(true);
        // Defer one tick so interact does not fight inventory mutations.
        Bukkit.getScheduler().runTask(plugin, () -> plugin.service().redeemOne(player));
    }

    private static boolean isShopOrContainerClick(Block block) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        if (type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL) {
            return true;
        }
        String name = type.name();
        if (name.endsWith("_SIGN") || name.endsWith("_HANGING_SIGN") || name.equals("SIGN") || name.equals("WALL_SIGN")) {
            return true;
        }
        return block.getState() instanceof InventoryHolder;
    }
}
