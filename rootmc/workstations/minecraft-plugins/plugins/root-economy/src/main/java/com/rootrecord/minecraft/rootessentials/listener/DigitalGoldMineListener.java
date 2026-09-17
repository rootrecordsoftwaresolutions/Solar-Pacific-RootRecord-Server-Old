package com.rootrecord.minecraft.rootessentials.listener;

import com.rootrecord.minecraft.common.RootMcIncomeSweepResult;
import com.rootrecord.minecraft.rooteconomy.RootEconomyPlugin;
import com.rootrecord.minecraft.rootessentials.data.GoldFoundStore;
import com.rootrecord.minecraft.rootessentials.goldfound.GoldFoundSource;
import com.rootrecord.minecraft.rootessentials.goldfound.GoldItemEventType;
import com.rootrecord.minecraft.rootessentials.service.GoldSiteRates;
import com.rootrecord.minecraft.rootessentials.util.GoldItemValue;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * Gold ore breaks pay wallet Notes (solar/site multipliers, including .xx)
 * and count as mint backing. Redeem physical via {@code /mint gold}.
 */
public final class DigitalGoldMineListener implements Listener {

    private static final Set<Material> GOLD_ORES = Set.of(
            Material.GOLD_ORE,
            Material.DEEPSLATE_GOLD_ORE,
            Material.NETHER_GOLD_ORE);

    private final RootEconomyPlugin plugin;

    public DigitalGoldMineListener(RootEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (!GOLD_ORES.contains(type)) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        Collection<ItemStack> drops = block.getDrops(tool, player);
        double peg = GoldItemValue.stacksOreDropValue(drops);
        if (peg <= 1e-9) {
            // Silk / no drops → 1 G per ore (deepslate 1.5× via site multiplier).
            peg = 1.0d;
        }
        double solar = plugin.solarMining() != null ? plugin.solarMining().multiplier() : 1.0d;
        GoldSiteRates.Site site = GoldSiteRates.at(block.getLocation(), type, solar);
        double gross = peg * site.dropMultiplier();
        event.setDropItems(false);
        if (gross <= 1e-9) {
            return;
        }
        try {
            RootMcIncomeSweepResult sweep = plugin.mineNotesAfterTax(
                    player.getUniqueId(), player.getName(), gross);
            recordFound(player, type, gross, block.getLocation());
            String cur = plugin.currency();
            StringBuilder msg = new StringBuilder("&6+").append(plugin.money(sweep.toWallet()))
                    .append(" ").append(cur).append(" notes");
            if (sweep.toLoanRepaid() > 1e-9) {
                msg.append(" &8· &e").append(plugin.money(sweep.toLoanRepaid())).append(" ").append(cur)
                        .append(" → loan");
            }
            if (sweep.toWallet() <= 1e-9 && solar > 1.0000001d) {
                msg.append(" &8· &7").append(String.format(Locale.US, "%.2f× solar · %.3f× site",
                        solar, site.dropMultiplier()));
            } else {
                msg.append(" &8· &7").append(String.format(Locale.US, "%.3fx", site.dropMultiplier()));
            }
            msg.append(" · /mint gold to redeem");
            player.sendMessage(plugin.colorize(msg.toString()));
        } catch (Exception ex) {
            plugin.getLogger().warning("Digital gold mine failed for " + player.getName() + ": " + ex.getMessage());
            player.sendMessage(plugin.colorize("&cMine pay failed: &f" + ex.getMessage()));
        }
    }

    private void recordFound(Player player, Material ore, double grossG, Location loc) {
        plugin.recordGoldItemEvent(
                player.getUniqueId(),
                player.getName(),
                GoldItemEventType.ACQUIRED,
                GoldFoundSource.MINED_ORE.name(),
                ore,
                1,
                grossG,
                loc,
                "{\"digital\":true}");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                GoldFoundStore store = plugin.goldFoundStore();
                if (store != null && grossG > 0) {
                    store.record(player.getUniqueId(), player.getName(), grossG, GoldFoundSource.MINED_ORE);
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Gold found record failed for " + player.getName() + ": " + ex.getMessage());
            }
        });
    }
}
