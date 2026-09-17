package com.rootrecord.minecraft.rootessentials.listener;

import com.rootrecord.minecraft.common.SystemGoldPayout;
import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import com.rootrecord.minecraft.rootessentials.data.GoldFoundStore;
import com.rootrecord.minecraft.rootessentials.goldfound.GoldFoundSource;
import com.rootrecord.minecraft.rootessentials.goldfound.GoldItemEventType;
import com.rootrecord.minecraft.rootessentials.service.GoldSiteRates;
import com.rootrecord.minecraft.rootessentials.util.GoldItemValue;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Records every path that introduces mint-peg gold (or gold ore forms) into play.
 * Form conversions that are peg-neutral (nuggetâ†”ingotâ†”block crafts) are not treated as new supply.
 */
public final class GoldFoundListener implements Listener {

    private static final NamespacedKey PLAYER_DROP_KEY =
            new NamespacedKey("rootessentials", "player_drop");
    private static final NamespacedKey CREDITED_KEY =
            new NamespacedKey("rootessentials", "gold_credited");

    private static final List<Material> GOLD_ORES = List.of(
            Material.GOLD_ORE,
            Material.DEEPSLATE_GOLD_ORE,
            Material.NETHER_GOLD_ORE);

    /** Pickups for these are normally credited at block break to avoid double-counting. */
    private static final Set<Material> MINED_PICKUP_MATERIALS = Set.of(
            Material.GOLD_ORE,
            Material.DEEPSLATE_GOLD_ORE,
            Material.NETHER_GOLD_ORE,
            Material.RAW_GOLD,
            Material.RAW_GOLD_BLOCK,
            Material.GOLD_NUGGET,
            Material.GOLD_BLOCK);

    private final RootEconomyPlugin plugin;

    public GoldFoundListener(RootEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (skipPlayer(player)) {
            return;
        }
        Block block = event.getBlock();
        Material type = block.getType();
        GoldFoundSource source;
        if (GOLD_ORES.contains(type)) {
            // DigitalGoldMineListener records ore → Notes (with multipliers).
            return;
        } else if (type == Material.GOLD_BLOCK || type == Material.RAW_GOLD_BLOCK) {
            source = GoldFoundSource.MINED_BLOCK;
        } else {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        Collection<ItemStack> drops = block.getDrops(tool, player);
        Location loc = block.getLocation();
        for (ItemStack drop : drops) {
            markCredited(drop, source.name());
        }
        recordStacks(player, drops, source, loc, true, type);
    }

    /**
     * Smelting that increases mint-peg G (e.g. silk-touched ore â†’ ingot). Rawâ†’ingot is peg-neutral.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        ItemStack source = event.getSource();
        ItemStack result = event.getResult();
        if (source == null || result == null) {
            return;
        }
        if (!GoldItemValue.isGoldRelated(source.getType()) && !GoldItemValue.isGoldItem(result.getType())) {
            return;
        }
        double inEach = rateOrZero(source.getType());
        double outEach = rateOrZero(result.getType());
        if (source.getType() == Material.DEEPSLATE_GOLD_ORE) {
            outEach *= GoldSiteRates.DEEPSLATE_VALUE;
        }
        double deltaEach = outEach - inEach;
        if (deltaEach <= 1e-9) {
            // Peg-neutral (e.g. raw gold â†’ ingot) â€” not new supply.
            return;
        }
        Player smoker = furnaceViewer(event);
        if (smoker == null) {
            Location loc = event.getBlock() != null ? event.getBlock().getLocation() : null;
            smoker = nearestPlayer(loc, 16.0);
        }
        if (smoker == null || skipPlayer(smoker)) {
            return;
        }
        ItemStack credited = result.clone();
        markCredited(credited, GoldFoundSource.SMELT.name());
        Location at = event.getBlock() != null ? event.getBlock().getLocation() : smoker.getLocation();
        recordNetGold(smoker, credited, GoldFoundSource.SMELT, at, deltaEach * credited.getAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || skipPlayer(killer)) {
            return;
        }
        List<ItemStack> goldDrops = event.getDrops().stream()
                .filter(s -> s != null && GoldItemValue.isGoldItem(s.getType()))
                .filter(s -> !SystemGoldPayout.isMarked(s))
                .filter(s -> !isCredited(s))
                .toList();
        if (goldDrops.isEmpty()) {
            return;
        }
        for (ItemStack drop : goldDrops) {
            markCredited(drop, GoldFoundSource.LOOT_MOB.name());
        }
        recordStacks(killer, goldDrops, GoldFoundSource.LOOT_MOB, event.getEntity().getLocation(), true, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPiglinBarter(org.bukkit.event.entity.PiglinBarterEvent event) {
        Player player = nearestPlayer(event.getEntity().getLocation(), 8.0);
        if (player == null || skipPlayer(player)) {
            return;
        }
        List<ItemStack> outcome = event.getOutcome();
        if (outcome == null || outcome.isEmpty()) {
            return;
        }
        List<ItemStack> gold = outcome.stream()
                .filter(s -> s != null && GoldItemValue.isGoldItem(s.getType()))
                .toList();
        if (gold.isEmpty()) {
            return;
        }
        for (ItemStack stack : gold) {
            markCredited(stack, GoldFoundSource.BARTER.name());
        }
        recordStacks(player, gold, GoldFoundSource.BARTER, event.getEntity().getLocation(), true, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLootGenerate(org.bukkit.event.world.LootGenerateEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player) || skipPlayer(player)) {
            return;
        }
        Collection<ItemStack> loot = event.getLoot();
        if (loot == null || loot.isEmpty()) {
            return;
        }
        List<ItemStack> gold = loot.stream()
                .filter(s -> s != null && GoldItemValue.isGoldRelated(s.getType()))
                .filter(s -> !SystemGoldPayout.isMarked(s))
                .toList();
        if (gold.isEmpty()) {
            return;
        }
        for (ItemStack stack : gold) {
            markCredited(stack, GoldFoundSource.LOOT_GENERATE.name());
        }
        recordStacks(player, gold, GoldFoundSource.LOOT_GENERATE, player.getLocation(), true, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        Player player = event.getPlayer();
        if (skipPlayer(player)) {
            return;
        }
        Entity caught = event.getCaught();
        if (!(caught instanceof Item item)) {
            return;
        }
        ItemStack stack = item.getItemStack();
        if (!GoldItemValue.isGoldItem(stack.getType()) || SystemGoldPayout.isMarked(stack) || isCredited(stack)) {
            return;
        }
        markCredited(stack, GoldFoundSource.FISHING.name());
        item.setItemStack(stack);
        recordStacks(player, List.of(stack), GoldFoundSource.FISHING, item.getLocation(), true, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChestTake(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || skipPlayer(player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (top instanceof MerchantInventory) {
            handleMerchantTake(event, player, top);
            return;
        }
        if (!isWorldLootContainer(top)) {
            return;
        }
        int topSize = top.getSize();
        if (event.getRawSlot() >= topSize) {
            return;
        }
        if (!isTakeAction(event.getAction())) {
            return;
        }
        ItemStack stack = event.getCurrentItem();
        if (stack == null || stack.getType().isAir() || !GoldItemValue.isGoldRelated(stack.getType())) {
            return;
        }
        if (SystemGoldPayout.isMarked(stack) || isCredited(stack)) {
            return;
        }
        // Unmarked world-container take â€” provenance only; may be player stockpiles, still an acquire event.
        recordStacks(player, List.of(stack), GoldFoundSource.LOOT_CHEST, player.getLocation(), true, null);
    }

    private void handleMerchantTake(InventoryClickEvent event, Player player, Inventory top) {
        if (!isTakeAction(event.getAction())) {
            return;
        }
        // Slot 2 is the trade result in a merchant inventory.
        if (event.getRawSlot() != 2) {
            return;
        }
        ItemStack stack = event.getCurrentItem();
        if (stack == null || stack.getType().isAir() || !GoldItemValue.isGoldItem(stack.getType())) {
            return;
        }
        if (SystemGoldPayout.isMarked(stack) || isCredited(stack)) {
            return;
        }
        ItemStack copy = stack.clone();
        markCredited(copy, GoldFoundSource.TRADE.name());
        recordStacks(player, List.of(copy), GoldFoundSource.TRADE, player.getLocation(), true, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || skipPlayer(player)) {
            return;
        }
        Item item = event.getItem();
        if (item.getPersistentDataContainer().has(PLAYER_DROP_KEY, PersistentDataType.BYTE)) {
            return;
        }
        ItemStack stack = item.getItemStack();
        if (SystemGoldPayout.isMarked(stack) || isCredited(stack)) {
            return;
        }
        if (MINED_PICKUP_MATERIALS.contains(stack.getType())) {
            return;
        }
        if (!GoldItemValue.isGoldRelated(stack.getType())) {
            return;
        }
        GoldFoundSource source = stack.getType() == Material.GOLD_INGOT
                ? GoldFoundSource.LOOT_MOB
                : GoldFoundSource.PICKUP;
        markCredited(stack, source.name());
        item.setItemStack(stack);
        recordStacks(player, List.of(stack), source, item.getLocation(), true, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        Item item = event.getItemDrop();
        item.getPersistentDataContainer().set(PLAYER_DROP_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    private static boolean skipPlayer(Player player) {
        return player == null
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR;
    }

    private static boolean isTakeAction(InventoryAction action) {
        return action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.PICKUP_ALL
                || action == InventoryAction.PICKUP_HALF
                || action == InventoryAction.PICKUP_ONE
                || action == InventoryAction.PICKUP_SOME
                || action == InventoryAction.HOTBAR_SWAP
                || action == InventoryAction.SWAP_WITH_CURSOR;
    }

    /** World chests/barrels only â€” not ender chests or plugin menu inventories. */
    private static boolean isWorldLootContainer(Inventory top) {
        if (top == null) {
            return false;
        }
        if (top.getType() == InventoryType.ENDER_CHEST
                || top.getType() == InventoryType.PLAYER
                || top.getType() == InventoryType.CREATIVE
                || top.getType() == InventoryType.MERCHANT
                || top.getType() == InventoryType.WORKBENCH
                || top.getType() == InventoryType.CRAFTING) {
            return false;
        }
        if (top.getHolder() instanceof Player) {
            return false;
        }
        return top.getLocation() != null;
    }

    private static Player furnaceViewer(FurnaceSmeltEvent event) {
        Block block = event.getBlock();
        if (block == null) {
            return null;
        }
        if (!(block.getState() instanceof org.bukkit.block.Furnace furnace)) {
            return null;
        }
        Inventory inv = furnace.getInventory();
        for (org.bukkit.entity.HumanEntity viewer : inv.getViewers()) {
            if (viewer instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private static Player nearestPlayer(Location loc, double radius) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        Player best = null;
        double bestDist = radius * radius;
        for (Player player : loc.getWorld().getPlayers()) {
            if (skipPlayer(player)) {
                continue;
            }
            double d = player.getLocation().distanceSquared(loc);
            if (d <= bestDist) {
                bestDist = d;
                best = player;
            }
        }
        return best;
    }

    private static double rateOrZero(Material material) {
        Double rate = GoldItemValue.oreDropRate(material);
        if (rate != null) {
            return rate;
        }
        // Ores have no mint peg until smelted.
        return 0;
    }

    private static void markCredited(ItemStack stack, String via) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(CREDITED_KEY, PersistentDataType.STRING, via);
        stack.setItemMeta(meta);
    }

    private static boolean isCredited(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(CREDITED_KEY, PersistentDataType.STRING);
    }

    private void recordNetGold(
            Player player,
            ItemStack stack,
            GoldFoundSource source,
            Location location,
            double netG) {
        UUID uuid = player.getUniqueId();
        String name = player.getName() == null ? "Unknown" : player.getName();
        plugin.recordGoldItemEvent(
                uuid,
                name,
                GoldItemEventType.ACQUIRED,
                source.name(),
                stack.getType(),
                stack.getAmount(),
                Math.max(0, netG),
                location,
                "{\"net\":true}");
        if (netG <= 0) {
            return;
        }
        final double aggregateG = netG;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                GoldFoundStore store = plugin.goldFoundStore();
                if (store == null) {
                    return;
                }
                store.record(uuid, name, aggregateG, source);
            } catch (Exception ex) {
                plugin.getLogger().warning("Gold found record failed for "
                        + name + " (" + source.name().toLowerCase(Locale.ROOT) + "): " + ex.getMessage());
            }
        });
    }

    private void recordStacks(
            Player player,
            Iterable<ItemStack> stacks,
            GoldFoundSource source,
            Location location,
            boolean countTowardFound,
            Material sourceBlock) {
        UUID uuid = player.getUniqueId();
        String name = player.getName() == null ? "Unknown" : player.getName();
        double totalG = 0;
        boolean logged = false;
        double valueMult = GoldSiteRates.foundValueMultiplier(sourceBlock);
        for (ItemStack stack : stacks) {
            if (stack == null || stack.getType().isAir() || !GoldItemValue.isGoldRelated(stack.getType())) {
                continue;
            }
            if (SystemGoldPayout.isMarked(stack)) {
                continue;
            }
            logged = true;
            double stackG = GoldItemValue.stackValue(stack) * valueMult;
            totalG += stackG;
            plugin.recordGoldItemEvent(
                    uuid,
                    name,
                    GoldItemEventType.ACQUIRED,
                    source.name(),
                    stack.getType(),
                    stack.getAmount(),
                    stackG,
                    location,
                    null);
        }
        if (!logged || !countTowardFound) {
            return;
        }
        final double aggregateG = totalG;
        if (aggregateG <= 0) {
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                GoldFoundStore store = plugin.goldFoundStore();
                if (store == null) {
                    return;
                }
                store.record(uuid, name, aggregateG, source);
            } catch (Exception ex) {
                plugin.getLogger().warning("Gold found record failed for "
                        + name + " (" + source.name().toLowerCase(Locale.ROOT) + "): " + ex.getMessage());
            }
        });
    }
}
