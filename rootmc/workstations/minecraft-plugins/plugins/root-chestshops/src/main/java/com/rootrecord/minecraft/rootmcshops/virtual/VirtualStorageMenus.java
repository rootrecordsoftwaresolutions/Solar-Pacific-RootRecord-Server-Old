package com.rootrecord.minecraft.rootmcshops.virtual;

import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.rootmcshops.RootMcShopsPlugin;
import com.rootrecord.minecraft.rootmcshops.ShopInputManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VirtualStorageMenus {

    static final int SLOT_LISTINGS = 45;
    static final int SLOT_DEPOSIT_HAND = 48;
    static final int SLOT_DEPOSIT_ALL = 49;
    static final int SLOT_CLOSE = 53;
    static final int PAGE_SIZE = 45;

    private VirtualStorageMenus() {}

    public static void openStorage(RootMcShopsPlugin plugin, Player player) {
        VirtualItemStore bins = plugin.virtualItems();
        if (bins == null || !bins.enabled()) {
            player.sendMessage(plugin.msg("virtual-disabled"));
            return;
        }
        List<VirtualBinEntry> entries = bins.listBins(player.getUniqueId());
        List<Long> ids = new ArrayList<>();
        for (VirtualBinEntry e : entries) {
            ids.add(e.id());
        }
        VirtualStorageHolder holder = VirtualStorageHolder.storage(player.getUniqueId(), ids);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("My Storage", NamedTextColor.DARK_GREEN));
        holder.bind(inv);

        for (int i = 0; i < Math.min(PAGE_SIZE, entries.size()); i++) {
            inv.setItem(i, binIcon(plugin, entries.get(i)));
        }
        inv.setItem(SLOT_LISTINGS, nav(Material.GOLD_INGOT, "My listings", "Click to manage virtual sell listings"));
        inv.setItem(SLOT_DEPOSIT_HAND, nav(Material.HOPPER, "Deposit hand", "/item deposit hand"));
        inv.setItem(SLOT_DEPOSIT_ALL, nav(Material.CHEST, "Deposit all", "/item deposit all"));
        inv.setItem(SLOT_CLOSE, nav(Material.BARRIER, "Close", ""));
        player.openInventory(inv);
    }

    public static void openMyListings(RootMcShopsPlugin plugin, Player player) {
        VirtualListingStore listings = plugin.virtualListings();
        if (listings == null || !listings.enabled()) {
            player.sendMessage(plugin.msg("virtual-disabled"));
            return;
        }
        List<VirtualListing> mine = listings.ownedBy(player.getUniqueId());
        List<String> ids = new ArrayList<>();
        for (VirtualListing l : mine) {
            ids.add(l.id());
        }
        VirtualStorageHolder holder = VirtualStorageHolder.listings(player.getUniqueId(), ids);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("My Listings", NamedTextColor.DARK_GREEN));
        holder.bind(inv);
        for (int i = 0; i < Math.min(PAGE_SIZE, mine.size()); i++) {
            inv.setItem(i, listingIcon(plugin, mine.get(i)));
        }
        inv.setItem(SLOT_CLOSE, nav(Material.BARRIER, "Back to storage", ""));
        player.openInventory(inv);
    }

    public static void beginListPrompt(RootMcShopsPlugin plugin, Player player, long binId, int qty) {
        VirtualBinEntry bin = plugin.virtualItems().getBin(binId);
        if (bin == null || !bin.ownerUuid().equals(player.getUniqueId()) || bin.qty() < qty) {
            player.sendMessage(plugin.msg("virtual-list-failed"));
            return;
        }
        player.closeInventory();
        plugin.inputManager().put(player.getUniqueId(), ShopInputManager.Pending.virtualList(binId, qty, bin.itemKey()));
        player.sendMessage(plugin.msg("virtual-list-prompt")
                .replace("{qty}", String.valueOf(qty))
                .replace("{item}", bin.itemKey().toLowerCase(Locale.ROOT)));
    }

    private static ItemStack binIcon(RootMcShopsPlugin plugin, VirtualBinEntry entry) {
        ItemStack stack = entry.stackSample();
        if (stack == null) {
            stack = new ItemStack(Material.CHEST, 1);
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(entry.itemKey(), NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Qty: " + entry.qty(), NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Left-click: withdraw 1 stack", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Shift-click: withdraw all", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Right-click: list for sale", NamedTextColor.AQUA)
                            .decoration(TextDecoration.ITALIC, false)));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack listingIcon(RootMcShopsPlugin plugin, VirtualListing listing) {
        ItemStack stack = listing.stackSample();
        if (stack == null) {
            stack = new ItemStack(Material.PAPER, 1);
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(listing.itemKey() + " (virtual)", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text(
                                    listing.qty() + " @ " + GoldMoney.format(listing.price()) + " G ea",
                                    NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Click to cancel listing", NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false)));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack nav(Material material, String name, String lore) {
        ItemStack stack = new ItemStack(material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            if (lore != null && !lore.isBlank()) {
                meta.lore(List.of(Component.text(lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
