package com.rootrecord.minecraft.rootmcshops;

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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Browse GUI: all sell-shop listings owned by a player. */
public final class PlayerShopsMenu {

    static final int PAGE_SIZE = 45;
    static final int SLOT_PREV = 45;
    static final int SLOT_INFO = 49;
    static final int SLOT_NEXT = 53;

    private PlayerShopsMenu() {}

    public static void open(RootMcShopsPlugin plugin, Player viewer, String ownerQuery) {
        OwnerMatch match = resolveOwner(plugin.store(), ownerQuery);
        if (match == null) {
            viewer.sendMessage(plugin.colorize(plugin.rawMsg("shops-player-not-found")
                    .replace("{player}", ownerQuery)));
            return;
        }

        List<ShopListing> listings = listingsForOwner(plugin.store(), match.ownerUuid(), match.ownerName());
        if (listings.isEmpty()) {
            viewer.sendMessage(plugin.colorize(plugin.rawMsg("shops-player-empty")
                    .replace("{player}", match.ownerName())));
            return;
        }

        openPage(plugin, viewer, match, listings, 0);
    }

    public static void openPage(
            RootMcShopsPlugin plugin,
            Player viewer,
            OwnerMatch match,
            List<ShopListing> listings,
            int page) {
        int totalPages = Math.max(1, (int) Math.ceil(listings.size() / (double) PAGE_SIZE));
        int safePage = Math.min(Math.max(0, page), totalPages - 1);
        int from = safePage * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, listings.size());
        List<ShopListing> pageListings = listings.subList(from, to);
        List<String> shopIds = pageListings.stream().map(ShopListing::id).toList();

        String titleRaw = plugin.rawMsg("shops-gui-title")
                .replace("{player}", match.ownerName())
                .replace("{page}", String.valueOf(safePage + 1))
                .replace("{pages}", String.valueOf(totalPages));
        Component title = Component.text(stripColorCodes(titleRaw), NamedTextColor.DARK_GREEN);

        PlayerShopsMenuHolder holder = new PlayerShopsMenuHolder(
                viewer.getUniqueId(),
                match.ownerUuid(),
                match.ownerName(),
                safePage,
                shopIds);
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.bind(inv);

        for (int i = 0; i < pageListings.size(); i++) {
            inv.setItem(i, displayItem(plugin, pageListings.get(i)));
        }

        if (safePage > 0) {
            inv.setItem(SLOT_PREV, navItem(Material.ARROW, plugin.rawMsg("shops-gui-prev")));
        }
        inv.setItem(SLOT_INFO, infoItem(plugin, match.ownerName(), listings.size(), safePage + 1, totalPages));
        if (safePage < totalPages - 1) {
            inv.setItem(SLOT_NEXT, navItem(Material.ARROW, plugin.rawMsg("shops-gui-next")));
        }

        viewer.openInventory(inv);
    }

    public static List<ShopListing> listingsForOwner(ShopStore store, String ownerUuid, String ownerName) {
        List<ShopListing> out = new ArrayList<>();
        for (ShopListing shop : store.all()) {
            if (!shop.isSellShop()) {
                continue;
            }
            if (matchesOwner(shop, ownerUuid, ownerName)) {
                out.add(shop);
            }
        }
        out.sort(Comparator
                .comparing((ShopListing s) -> s.itemKey(), String.CASE_INSENSITIVE_ORDER)
                .thenComparingDouble(ShopListing::price));
        return out;
    }

    public static OwnerMatch resolveOwner(ShopStore store, String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String q = query.trim();

        Player online = Bukkit.getPlayerExact(q);
        if (online == null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().equalsIgnoreCase(q)) {
                    online = p;
                    break;
                }
            }
        }
        if (online != null) {
            return new OwnerMatch(online.getUniqueId().toString(), online.getName());
        }

        String uuidHit = null;
        String nameHit = null;
        for (ShopListing shop : store.all()) {
            if (shop.ownerName() != null && shop.ownerName().equalsIgnoreCase(q)) {
                uuidHit = shop.ownerUuid();
                nameHit = shop.ownerName();
                break;
            }
        }
        if (nameHit != null) {
            return new OwnerMatch(uuidHit != null ? uuidHit : "", nameHit);
        }

        try {
            @SuppressWarnings("deprecation")
            var offline = Bukkit.getOfflinePlayer(q);
            if (offline != null && (offline.hasPlayedBefore() || offline.isOnline())) {
                UUID id = offline.getUniqueId();
                String name = offline.getName() != null ? offline.getName() : q;
                for (ShopListing shop : store.all()) {
                    if (shop.ownerUuid() != null && shop.ownerUuid().equalsIgnoreCase(id.toString())) {
                        return new OwnerMatch(id.toString(), shop.ownerName() != null ? shop.ownerName() : name);
                    }
                }
            }
        } catch (Exception ignored) {
            // Offline lookup best-effort.
        }
        return null;
    }

    private static boolean matchesOwner(ShopListing shop, String ownerUuid, String ownerName) {
        if (ownerUuid != null && !ownerUuid.isBlank()
                && shop.ownerUuid() != null
                && shop.ownerUuid().equalsIgnoreCase(ownerUuid)) {
            return true;
        }
        return ownerName != null
                && shop.ownerName() != null
                && shop.ownerName().equalsIgnoreCase(ownerName);
    }

    private static ItemStack displayItem(RootMcShopsPlugin plugin, ShopListing shop) {
        String itemKey = plugin.itemKeyForStock(shop);
        ItemStack stack = ShopContainers.firstMatchingStack(shop, itemKey);
        if (stack == null) {
            stack = ShopItemKeys.stackForKey(itemKey);
        }
        if (stack == null) {
            Material mat = ShopItemKeys.baseMaterial(itemKey);
            stack = new ItemStack(mat != null ? mat : Material.CHEST, 1);
        } else {
            stack = stack.clone();
            stack.setAmount(1);
        }

        int stock = plugin.countStock(shop);
        int qty = Math.max(1, shop.saleQty());
        String priceLine = qty > 1
                ? String.format(Locale.US, "%.3f G each (%d per trade)", shop.price(), qty)
                : String.format(Locale.US, "%.3f G", shop.price());

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(prettyItemName(itemKey), NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Price: " + priceLine, NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Stock: " + stock, stock > 0 ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(
                            shop.world() + " " + shop.x() + ", " + shop.y() + ", " + shop.z(),
                            NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Click for coords in chat", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack navItem(Material material, String name) {
        ItemStack stack = new ItemStack(material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(stripColorCodes(name), NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack infoItem(RootMcShopsPlugin plugin, String owner, int count, int page, int pages) {
        ItemStack stack = new ItemStack(Material.BOOK, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(owner + "'s shops", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text(count + " listing(s) for sale", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Page " + page + " / " + pages, NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text(stripColorCodes(plugin.rawMsg("shops-gui-hint")), NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static String prettyItemName(String itemKey) {
        if (itemKey == null || itemKey.isBlank()) {
            return "Unknown";
        }
        String pretty = itemKey.toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] parts = pretty.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static String stripColorCodes(String input) {
        if (input == null) {
            return "";
        }
        return input.replace('&', '§').replaceAll("§[0-9a-fk-or]", "");
    }

    public record OwnerMatch(String ownerUuid, String ownerName) {}
}
