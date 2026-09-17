package com.rootrecord.minecraft.rootgamble.gui;

import com.rootrecord.minecraft.rootgamble.GambleEconomy;
import com.rootrecord.minecraft.rootgamble.RootGamblePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class GambleMenus {

    private GambleMenus() {}

    public static void openHub(RootGamblePlugin plugin, Player player) {
        GambleMenuHolder holder = new GambleMenuHolder(GambleMenuHolder.Kind.HUB);
        String title = plugin.config().guiTitle();
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text(strip(title), NamedTextColor.GOLD));
        holder.inventory(inv);

        inv.setItem(10, tile(plugin, player, Material.RED_WOOL, "Roulette", "roulette",
                List.of("vs Server Reserve", "European 0–36", "House edge ~2.7%")));
        inv.setItem(12, tile(plugin, player, Material.COMPASS, "HiLo", "hilo",
                List.of("vs Server Reserve", "Next card higher or lower?")));
        inv.setItem(14, tile(plugin, player, Material.GOLD_NUGGET, "Lotto", "lotto",
                List.of("vs Server Reserve",
                        "Jackpot: " + GambleEconomy.money(plugin.store().jackpot()) + " "
                                + plugin.config().currency(),
                        "Draws each Minecraft day",
                        "Ticket: " + GambleEconomy.money(plugin.config().lottoTicketPrice()) + " "
                                + plugin.config().currency())));
        inv.setItem(16, tile(plugin, player, Material.GOLD_INGOT, "Coin", "coin",
                List.of("vs player or Ava", "Ava always accepts if reserve 0+", "50/50 coin flip")));
        inv.setItem(22, tile(plugin, player, Material.QUARTZ, "Dice", "dice",
                List.of("vs player", "Highest roll wins")));

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.displayName(Component.text("Close", NamedTextColor.RED));
        close.setItemMeta(cm);
        inv.setItem(26, close);

        player.openInventory(inv);
    }

    private static ItemStack tile(
            RootGamblePlugin plugin,
            Player player,
            Material mat,
            String name,
            String gameId,
            List<String> baseLore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
        List<Component> lore = new ArrayList<>();
        for (String line : baseLore) {
            lore.add(Component.text(line, NamedTextColor.GRAY));
        }
        boolean free = plugin.store().hasFreePlay(player.getUniqueId(), gameId);
        lore.add(Component.empty());
        lore.add(Component.text(
                free ? "Free play available" : "Free play used",
                free ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
        lore.add(Component.text("Click for full help", NamedTextColor.YELLOW));
        meta.lore(lore);
        // Encode game id in localized name key via PDC would be better — use display for listener map by slot
        stack.setItemMeta(meta);
        return stack;
    }

    private static String strip(String raw) {
        if (raw == null) {
            return "Gamble";
        }
        return raw.replaceAll("(?i)§[0-9a-fk-or]", "").replaceAll("(?i)&[0-9a-fk-or]", "");
    }
}
