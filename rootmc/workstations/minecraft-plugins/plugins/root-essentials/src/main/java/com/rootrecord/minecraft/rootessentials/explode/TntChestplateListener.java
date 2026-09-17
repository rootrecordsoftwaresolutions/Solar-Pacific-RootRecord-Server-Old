package com.rootrecord.minecraft.rootessentials.explode;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Keeps TNT stacks stamped as wearable chestplates. */
public final class TntChestplateListener implements Listener {

    private final JavaPlugin plugin;

    public TntChestplateListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        TntChestplate.stampInventory(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> TntChestplate.stampInventory(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Item item = event.getItem();
        ItemStack stack = item.getItemStack();
        if (!TntChestplate.isTnt(stack)) {
            return;
        }
        TntChestplate.makeWearable(stack);
        item.setItemStack(stack);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        if (!TntChestplate.isTnt(result)) {
            return;
        }
        TntChestplate.makeWearable(result);
        event.setCurrentItem(result);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        stamp(event.getCurrentItem());
        stamp(event.getCursor());
        if (event.getWhoClicked() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> TntChestplate.stampInventory(player));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        for (ItemStack stack : event.getNewItems().values()) {
            stamp(stack);
        }
        stamp(event.getOldCursor());
        stamp(event.getCursor());
    }

    private static void stamp(ItemStack stack) {
        if (stack == null || stack.getType() != Material.TNT) {
            return;
        }
        TntChestplate.makeWearable(stack);
    }
}
