package com.rootrecord.minecraft.rootavacore.gui;

import com.rootrecord.minecraft.rootavacore.RootAvaCorePlugin;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Right-click Ava (mannequin or Ava_Ivy) → menu. Bread + this player's hire stats. */
public final class AvaMenuListener implements Listener {

    private static final long BREAD_COOLDOWN_MS = 20_000L;

    private final RootAvaCorePlugin plugin;
    private final Map<UUID, Long> breadAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> openAt = new ConcurrentHashMap<>();

    public AvaMenuListener(RootAvaCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        tryOpen(event.getPlayer(), event.getRightClicked(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        tryOpen(event.getPlayer(), event.getRightClicked(), event);
    }

    private void tryOpen(Player clicker, Entity target, org.bukkit.event.Cancellable event) {
        if (clicker == null || target == null) return;
        if (!isAvaTarget(target)) return;
        if (plugin.powers() != null && plugin.powers().isAva(clicker)) return;
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        Long last = openAt.put(clicker.getUniqueId(), now);
        if (last != null && now - last < 250L) return;
        AvaMenuGui.open(plugin, clicker);
    }

    private boolean isAvaTarget(Entity entity) {
        if (plugin.presence() != null && plugin.presence().isAvaBody(entity)) return true;
        if (entity instanceof Player p && plugin.powers() != null && plugin.powers().isAva(p)) return true;
        return false;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AvaMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.getUniqueId().equals(holder.playerId())) return;
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == AvaMenuHolder.SLOT_BREAD) {
            giveBread(player);
            return;
        }
        if (slot == AvaMenuHolder.SLOT_JOBS || slot == AvaMenuHolder.SLOT_QUOTE || slot == AvaMenuHolder.SLOT_HELLO) {
            AvaMenuGui.open(plugin, player);
            return;
        }
        if (slot == AvaMenuHolder.SLOT_HELP) {
            player.closeInventory();
            player.performCommand("ava help");
            return;
        }
        if (slot == AvaMenuHolder.SLOT_CLOSE) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof AvaMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void giveBread(Player player) {
        long now = System.currentTimeMillis();
        Long last = breadAt.get(player.getUniqueId());
        if (last != null && now - last < BREAD_COOLDOWN_MS) {
            long wait = (BREAD_COOLDOWN_MS - (now - last) + 999L) / 1000L;
            player.sendMessage(plugin.colorize(
                    plugin.config().prefix() + "&eOven's hot — bread in &f" + wait + "s&e."));
            return;
        }
        breadAt.put(player.getUniqueId(), now);
        ItemStack loaf = new ItemStack(Material.BREAD, 1);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(loaf);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        }
        player.sendMessage(plugin.colorize(plugin.config().prefix() + "&6Here's bread. Don't make it weird."));
    }
}
