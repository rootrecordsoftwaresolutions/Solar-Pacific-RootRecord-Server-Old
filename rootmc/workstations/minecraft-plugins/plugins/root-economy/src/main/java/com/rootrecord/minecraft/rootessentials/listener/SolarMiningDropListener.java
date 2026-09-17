package com.rootrecord.minecraft.rootessentials.listener;

import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import com.rootrecord.minecraft.rootessentials.service.GoldSiteRates;
import com.rootrecord.minecraft.rootessentials.service.SolarMiningMultiplierService;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Solar gaming bonus + site taxes/valuation on gold ore and gold-block mine drops.
 */
public final class SolarMiningDropListener implements Listener {

    /** Placed gold blocks only — ores pay wallet Notes via {@link DigitalGoldMineListener}. */
    private static final Set<Material> GOLD_MINE_BLOCKS = Set.of(
            Material.GOLD_BLOCK,
            Material.RAW_GOLD_BLOCK);

    private static final Set<Material> GOLD_DROP_ITEMS = Set.of(
            Material.GOLD_ORE,
            Material.DEEPSLATE_GOLD_ORE,
            Material.NETHER_GOLD_ORE,
            Material.RAW_GOLD,
            Material.RAW_GOLD_BLOCK,
            Material.GOLD_NUGGET,
            Material.GOLD_INGOT,
            Material.GOLD_BLOCK);

    private final RootEconomyPlugin plugin;

    public SolarMiningDropListener(RootEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        if (!GOLD_MINE_BLOCKS.contains(event.getBlockState().getType())) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        SolarMiningMultiplierService solar = plugin.solarMining();
        double solarMult = solar != null ? solar.multiplier() : 1.0d;
        GoldSiteRates.Site site = GoldSiteRates.at(
                event.getBlock().getLocation(),
                event.getBlockState().getType(),
                solarMult);
        double mult = site.dropMultiplier();
        if (!Double.isFinite(mult) || Math.abs(mult - 1.0d) < 1e-9) {
            return;
        }
        Iterator<Item> it = event.getItems().iterator();
        List<ItemStack> extras = new ArrayList<>();
        while (it.hasNext()) {
            Item entity = it.next();
            ItemStack stack = entity.getItemStack();
            if (stack == null || !GOLD_DROP_ITEMS.contains(stack.getType()) || stack.getAmount() <= 0) {
                continue;
            }
            int scaled = SolarMiningMultiplierService.scaleAmount(stack.getAmount(), mult);
            if (scaled <= 0) {
                it.remove();
                continue;
            }
            if (scaled <= stack.getMaxStackSize()) {
                stack.setAmount(scaled);
                entity.setItemStack(stack);
                continue;
            }
            stack.setAmount(stack.getMaxStackSize());
            entity.setItemStack(stack);
            int remaining = scaled - stack.getMaxStackSize();
            while (remaining > 0) {
                ItemStack extra = stack.clone();
                int n = Math.min(remaining, extra.getMaxStackSize());
                extra.setAmount(n);
                extras.add(extra);
                remaining -= n;
            }
        }
        if (extras.isEmpty()) {
            return;
        }
        var loc = event.getBlock().getLocation().add(0.5, 0.25, 0.5);
        for (ItemStack extra : extras) {
            event.getBlock().getWorld().dropItemNaturally(loc, extra);
        }
    }
}
