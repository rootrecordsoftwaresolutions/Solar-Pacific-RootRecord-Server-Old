package com.rootrecord.minecraft.rootessentials.ender;

import com.rootrecord.minecraft.common.RootMcEnderChestService;
import com.rootrecord.minecraft.rootessentials.data.MySqlSupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** 54-slot per-server ender chest backed by MySQL. */
public final class DoubleEnderChestService implements RootMcEnderChestService, Listener {

    public static final class Holder implements InventoryHolder {
        private final UUID ownerId;
        private Inventory inventory;

        Holder(UUID ownerId) {
            this.ownerId = ownerId;
        }

        public UUID ownerId() {
            return ownerId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        void bind(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    private final JavaPlugin plugin;
    private final DoubleEnderChestStore store;
    private final Map<UUID, Inventory> open = new ConcurrentHashMap<>();

    public DoubleEnderChestService(JavaPlugin plugin, MySqlSupport sql, String tablePrefix, String serverId) {
        this.plugin = plugin;
        this.store = new DoubleEnderChestStore(sql, tablePrefix, serverId, plugin.getLogger());
        try {
            store.initSchema();
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Double ender chest schema failed: " + ex.getMessage(), ex);
        }
    }

    public DoubleEnderChestStore store() {
        return store;
    }

    @Override
    public void open(Player viewer, Player owner) {
        if (viewer == null || owner == null) {
            return;
        }
        Inventory inv = inventory(owner);
        viewer.openInventory(inv);
    }

    @Override
    public Inventory inventory(Player player) {
        return open.computeIfAbsent(player.getUniqueId(), id -> loadOrCreate(player));
    }

    private Inventory loadOrCreate(Player player) {
        Holder holder = new Holder(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(
                holder,
                SLOT_COUNT,
                Component.text("Ender Chest", NamedTextColor.DARK_PURPLE));
        holder.bind(inv);
        ItemStack[] contents = DoubleEnderChestStore.emptyContents(SLOT_COUNT);
        boolean migrated = false;
        try {
            DoubleEnderChestStore.Row row = store.load(player.getUniqueId());
            if (row != null) {
                contents = row.contents();
                migrated = row.migratedVanilla();
            }
            if (!migrated) {
                ItemStack[] vanilla = player.getEnderChest().getContents();
                for (int i = 0; i < Math.min(27, vanilla.length); i++) {
                    if (contents[i] == null && vanilla[i] != null) {
                        contents[i] = vanilla[i].clone();
                    }
                }
                migrated = true;
                store.save(player.getUniqueId(), player.getName(), contents, true);
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Load double EC for " + player.getName() + ": " + ex.getMessage());
        }
        inv.setContents(pad(contents));
        return inv;
    }

    private static ItemStack[] pad(ItemStack[] contents) {
        ItemStack[] out = new ItemStack[SLOT_COUNT];
        if (contents != null) {
            System.arraycopy(contents, 0, out, 0, Math.min(SLOT_COUNT, contents.length));
        }
        return out;
    }

    @Override
    public ItemStack[] contents(UUID playerId) {
        if (playerId == null) {
            return DoubleEnderChestStore.emptyContents(SLOT_COUNT);
        }
        Inventory live = open.get(playerId);
        if (live != null) {
            return pad(live.getContents());
        }
        try {
            DoubleEnderChestStore.Row row = store.load(playerId);
            if (row != null) {
                return pad(row.contents());
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Offline EC load " + playerId + ": " + ex.getMessage());
        }
        return DoubleEnderChestStore.emptyContents(SLOT_COUNT);
    }

    @Override
    public void save(Player player) {
        if (player == null) {
            return;
        }
        Inventory inv = open.get(player.getUniqueId());
        if (inv == null) {
            return;
        }
        save(player.getUniqueId(), player.getName(), inv.getContents());
    }

    @Override
    public void save(UUID playerId, String playerName, ItemStack[] contents) {
        try {
            store.save(playerId, playerName, pad(contents), true);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Save double EC " + playerId + ": " + ex.getMessage());
        }
    }

    @Override
    public ItemStack[] addItems(Player player, ItemStack... items) {
        if (player == null || items == null || items.length == 0) {
            return items == null ? new ItemStack[0] : items;
        }
        Inventory inv = inventory(player);
        java.util.HashMap<Integer, ItemStack> left = inv.addItem(items);
        save(player);
        return left.values().toArray(new ItemStack[0]);
    }

    /** Total stack amounts that match a predicate — used by vote shard scans. */
    public ItemStack[] liveOrStoredContents(Player player) {
        return inventory(player).getContents();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        save(player);
        open.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof Holder) {
            save(player.getUniqueId(), player.getName(), event.getInventory().getContents());
        }
    }

    /** Redirect vanilla ender chest block / vanilla EC opens to the double inventory. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVanillaEnderOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof Holder) {
            return;
        }
        if (event.getInventory().getType() != InventoryType.ENDER_CHEST) {
            return;
        }
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> open(player, player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoinWarm(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                inventory(event.getPlayer());
            }
        }, 40L);
    }
}
