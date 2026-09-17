package com.rootrecord.minecraft.rootmarket;

import com.rootrecord.minecraft.rootmcshops.RootMcShopsPlugin;
import com.rootrecord.minecraft.rootmcshops.ShopContainers;
import com.rootrecord.minecraft.rootmcshops.ShopItemKeys;
import com.rootrecord.minecraft.rootmcshops.ShopListing;
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

public final class MarketMenus {

    static final int PAGE_SIZE = 45;
    static final int SLOT_PREV = 45;
    static final int SLOT_INFO = 49;
    static final int SLOT_NEXT = 53;
    static final int SLOT_BACK = 48;

    private MarketMenus() {}

    public static void openHub(RootMarketPlugin plugin, RootMcShopsPlugin shops, Player viewer) {
        if (plugin.yaml().config().getBoolean("browse.categories", true)) {
            openCategories(plugin, shops, viewer);
            return;
        }
        openCategory(plugin, shops, viewer, MarketCategory.ALL);
    }

    public static void openCategories(RootMarketPlugin plugin, RootMcShopsPlugin shops, Player viewer) {
        MarketQuotes.Index quotes = MarketQuotes.build(shops);
        if (quotes.all().isEmpty()) {
            viewer.sendMessage(plugin.msg("hub-empty"));
            return;
        }
        List<String> ids = new ArrayList<>();
        for (MarketCategory cat : MarketCategory.values()) {
            ids.add(cat.id());
        }
        MarketMenuHolder holder = MarketMenuHolder.categories(viewer.getUniqueId(), ids);
        Inventory inv = Bukkit.createInventory(
                holder, 27, Component.text(strip(plugin.rawMsg("categories-title")), NamedTextColor.DARK_GREEN));
        holder.bind(inv);

        int slot = 0;
        for (MarketCategory cat : MarketCategory.values()) {
            int count = 0;
            for (String key : quotes.all().keySet()) {
                if (cat.matches(key)) {
                    count++;
                }
            }
            inv.setItem(slot++, categoryItem(plugin, cat, count));
        }
        inv.setItem(22, trendingInfo(plugin, shops, quotes.all().size(), 0, 1));
        viewer.openInventory(inv);
    }

    public static void openCategory(
            RootMarketPlugin plugin, RootMcShopsPlugin shops, Player viewer, MarketCategory category) {
        List<HubRow> rows = buildHubRows(shops, category, plugin);
        if (rows.isEmpty()) {
            viewer.sendMessage(plugin.msg("category-empty").replace("{category}", category.display()));
            return;
        }
        openHubPage(plugin, shops, viewer, rows, 0, category);
    }

    public static void openHubPage(
            RootMarketPlugin plugin,
            RootMcShopsPlugin shops,
            Player viewer,
            List<HubRow> rows,
            int page) {
        openHubPage(plugin, shops, viewer, rows, page, MarketCategory.ALL);
    }

    public static void openHubPage(
            RootMarketPlugin plugin,
            RootMcShopsPlugin shops,
            Player viewer,
            List<HubRow> rows,
            int page,
            MarketCategory category) {
        int totalPages = Math.max(1, (int) Math.ceil(rows.size() / (double) PAGE_SIZE));
        int safePage = Math.min(Math.max(0, page), totalPages - 1);
        int from = safePage * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, rows.size());
        List<HubRow> pageRows = rows.subList(from, to);
        List<String> keys = pageRows.stream().map(HubRow::itemKey).toList();

        String titleRaw = plugin.rawMsg("hub-title")
                .replace("{category}", category.display())
                .replace("{page}", String.valueOf(safePage + 1))
                .replace("{pages}", String.valueOf(totalPages));
        MarketMenuHolder holder = MarketMenuHolder.hub(viewer.getUniqueId(), safePage, keys, category.id());
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text(strip(titleRaw), NamedTextColor.DARK_GREEN));
        holder.bind(inv);

        MarketQuotes.Index quotes = MarketQuotes.build(shops);
        for (int i = 0; i < pageRows.size(); i++) {
            inv.setItem(i, hubItem(pageRows.get(i), quotes.get(pageRows.get(i).itemKey())));
        }
        if (safePage > 0) {
            inv.setItem(SLOT_PREV, nav(Material.ARROW, plugin.rawMsg("hub-prev")));
        }
        if (plugin.yaml().config().getBoolean("browse.categories", true)) {
            inv.setItem(SLOT_BACK, nav(Material.BARRIER, plugin.rawMsg("hub-back-categories")));
        }
        inv.setItem(SLOT_INFO, trendingInfo(plugin, shops, rows.size(), safePage, totalPages));
        if (safePage < totalPages - 1) {
            inv.setItem(SLOT_NEXT, nav(Material.ARROW, plugin.rawMsg("hub-next")));
        }
        viewer.openInventory(inv);
    }

    public static void openItem(RootMarketPlugin plugin, RootMcShopsPlugin shops, Player viewer, String itemKey) {
        openItem(plugin, shops, viewer, itemKey, MarketCategory.ALL);
    }

    public static void openItem(
            RootMarketPlugin plugin,
            RootMcShopsPlugin shops,
            Player viewer,
            String itemKey,
            MarketCategory category) {
        List<MixedOffer> offers = mixedOffersForItem(plugin, shops, itemKey);
        if (offers.isEmpty()) {
            viewer.sendMessage(plugin.msg("item-empty").replace("{item}", itemKey.toLowerCase(Locale.ROOT)));
            return;
        }
        openItemPage(plugin, shops, viewer, itemKey, offers, 0, category);
    }

    public static void openItemPage(
            RootMarketPlugin plugin,
            RootMcShopsPlugin shops,
            Player viewer,
            String itemKey,
            List<MixedOffer> offers,
            int page,
            MarketCategory category) {
        int totalPages = Math.max(1, (int) Math.ceil(offers.size() / (double) PAGE_SIZE));
        int safePage = Math.min(Math.max(0, page), totalPages - 1);
        int from = safePage * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, offers.size());
        List<MixedOffer> pageList = offers.subList(from, to);
        List<String> ids = pageList.stream().map(MixedOffer::id).toList();

        double cheapest = offers.stream().mapToDouble(MixedOffer::price).min().orElse(0);
        String titleRaw = plugin.rawMsg("item-title")
                .replace("{item}", pretty(itemKey))
                .replace("{page}", String.valueOf(safePage + 1))
                .replace("{pages}", String.valueOf(totalPages));
        MarketMenuHolder holder =
                MarketMenuHolder.item(viewer.getUniqueId(), itemKey, safePage, ids, category.id());
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text(strip(titleRaw), NamedTextColor.DARK_GREEN));
        holder.bind(inv);

        for (int i = 0; i < pageList.size(); i++) {
            MixedOffer offer = pageList.get(i);
            if (offer.virtual()) {
                inv.setItem(i, virtualListingItem(offer));
            } else {
                ShopListing shop = shops.store().getById(offer.id());
                if (shop != null) {
                    inv.setItem(i, listingItem(shops, shop, true));
                }
            }
        }
        if (safePage > 0) {
            inv.setItem(SLOT_PREV, nav(Material.ARROW, plugin.rawMsg("item-prev")));
        }
        inv.setItem(SLOT_BACK, nav(Material.BARRIER, plugin.rawMsg("item-back")));
        MarketQuotes.Quote quote = MarketQuotes.build(shops).get(itemKey);
        inv.setItem(SLOT_INFO, itemQuoteBook(plugin, itemKey, offers.size(), cheapest, quote, safePage, totalPages));
        if (safePage < totalPages - 1) {
            inv.setItem(SLOT_NEXT, nav(Material.ARROW, plugin.rawMsg("item-next")));
        }
        viewer.openInventory(inv);
    }

    public static List<MixedOffer> mixedOffersForItem(
            RootMarketPlugin plugin, RootMcShopsPlugin shops, String itemKey) {
        List<MixedOffer> out = new ArrayList<>();
        for (ShopListing shop : listingsForItem(shops, itemKey)) {
            out.add(new MixedOffer(shop.id(), false, shop.price(), shop.ownerName(), shops.countStock(shop), null));
        }
        boolean includeVirtual = plugin.yaml().config().getBoolean("browse.include-virtual", true);
        var vstore = shops.virtualListings();
        if (includeVirtual && vstore != null && vstore.enabled()) {
            for (var v : vstore.sellForItem(itemKey)) {
                out.add(new MixedOffer("v:" + v.id(), true, v.price(), v.ownerName(), v.qty(), v));
            }
        }
        out.sort(Comparator.comparingDouble(MixedOffer::price).thenComparing(MixedOffer::id));
        return out;
    }

    public static void openPlayer(RootMarketPlugin plugin, RootMcShopsPlugin shops, Player viewer, String ownerQuery) {
        OwnerMatch match = resolveOwner(shops, ownerQuery);
        if (match == null) {
            viewer.sendMessage(plugin.msg("player-not-found").replace("{player}", ownerQuery));
            return;
        }
        List<ShopListing> listings = listingsForOwner(shops, match.ownerUuid(), match.ownerName());
        if (listings.isEmpty()) {
            viewer.sendMessage(plugin.msg("player-empty").replace("{player}", match.ownerName()));
            return;
        }
        openPlayerPage(plugin, shops, viewer, match, listings, 0);
    }

    public static void openPlayerPage(
            RootMarketPlugin plugin,
            RootMcShopsPlugin shops,
            Player viewer,
            OwnerMatch match,
            List<ShopListing> listings,
            int page) {
        int totalPages = Math.max(1, (int) Math.ceil(listings.size() / (double) PAGE_SIZE));
        int safePage = Math.min(Math.max(0, page), totalPages - 1);
        int from = safePage * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, listings.size());
        List<ShopListing> pageList = listings.subList(from, to);
        List<String> ids = pageList.stream().map(ShopListing::id).toList();

        String titleRaw = plugin.rawMsg("player-title")
                .replace("{player}", match.ownerName())
                .replace("{page}", String.valueOf(safePage + 1))
                .replace("{pages}", String.valueOf(totalPages));
        MarketMenuHolder holder = MarketMenuHolder.player(
                viewer.getUniqueId(), match.ownerUuid(), match.ownerName(), safePage, ids);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text(strip(titleRaw), NamedTextColor.DARK_GREEN));
        holder.bind(inv);

        for (int i = 0; i < pageList.size(); i++) {
            inv.setItem(i, listingItem(shops, pageList.get(i), false));
        }
        if (safePage > 0) {
            inv.setItem(SLOT_PREV, nav(Material.ARROW, plugin.rawMsg("player-prev")));
        }
        inv.setItem(SLOT_INFO, infoBook(
                match.ownerName() + "'s shops",
                List.of(
                        strip(plugin.rawMsg("player-info").replace("{count}", String.valueOf(listings.size()))),
                        "Page " + (safePage + 1) + " / " + totalPages,
                        strip(plugin.rawMsg("player-hint")))));
        if (safePage < totalPages - 1) {
            inv.setItem(SLOT_NEXT, nav(Material.ARROW, plugin.rawMsg("player-next")));
        }
        viewer.openInventory(inv);
    }

    public static List<HubRow> buildHubRows(RootMcShopsPlugin shops) {
        return buildHubRows(shops, MarketCategory.ALL, null);
    }

    public static List<HubRow> buildHubRows(
            RootMcShopsPlugin shops, MarketCategory category, RootMarketPlugin plugin) {
        boolean showBuyOnly = plugin == null
                || plugin.yaml().config().getBoolean("browse.show-buy-only-items", true);
        MarketQuotes.Index quotes = MarketQuotes.build(shops);
        List<HubRow> rows = new ArrayList<>();
        for (MarketQuotes.Quote q : quotes.all().values()) {
            if (!category.matches(q.itemKey())) {
                continue;
            }
            if (!q.hasBuy() && !(showBuyOnly && q.hasSell())) {
                continue;
            }
            rows.add(new HubRow(
                    q.itemKey(),
                    q.hasBuy() ? q.buyUnit() : (q.hasSell() ? q.sellUnit() : 0),
                    q.buyStock(),
                    q.buyShops(),
                    q.hasBuy() ? q.buyShops() : 0));
        }
        rows.sort(Comparator
                .comparingInt(HubRow::inStockCount).reversed()
                .thenComparing(HubRow::itemKey, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    public static List<ShopListing> listingsForItem(RootMcShopsPlugin shops, String itemKey) {
        List<ShopListing> out = new ArrayList<>();
        for (ShopListing shop : shops.store().all()) {
            if (shop.isSellShop() && shop.itemKey() != null && shop.itemKey().equalsIgnoreCase(itemKey)) {
                out.add(shop);
            }
        }
        out.sort(Comparator
                .comparingInt((ShopListing s) -> shops.countStock(s) > 0 ? 0 : 1)
                .thenComparingDouble(ShopListing::price)
                .thenComparing(s -> s.ownerName() == null ? "" : s.ownerName(), String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public static List<ShopListing> listingsForOwner(RootMcShopsPlugin shops, String ownerUuid, String ownerName) {
        List<ShopListing> out = new ArrayList<>();
        for (ShopListing shop : shops.store().all()) {
            if (!shop.isSellShop()) {
                continue;
            }
            if (ownerUuid != null && !ownerUuid.isBlank()
                    && shop.ownerUuid() != null
                    && shop.ownerUuid().equalsIgnoreCase(ownerUuid)) {
                out.add(shop);
                continue;
            }
            if (ownerName != null && shop.ownerName() != null && shop.ownerName().equalsIgnoreCase(ownerName)) {
                out.add(shop);
            }
        }
        out.sort(Comparator
                .comparing((ShopListing s) -> s.itemKey(), String.CASE_INSENSITIVE_ORDER)
                .thenComparingDouble(ShopListing::price));
        return out;
    }

    public static OwnerMatch resolveOwner(RootMcShopsPlugin shops, String query) {
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
        for (ShopListing shop : shops.store().all()) {
            if (shop.ownerName() != null && shop.ownerName().equalsIgnoreCase(q)) {
                return new OwnerMatch(
                        shop.ownerUuid() != null ? shop.ownerUuid() : "",
                        shop.ownerName());
            }
        }
        try {
            @SuppressWarnings("deprecation")
            var offline = Bukkit.getOfflinePlayer(q);
            if (offline != null && (offline.hasPlayedBefore() || offline.isOnline())) {
                UUID id = offline.getUniqueId();
                String name = offline.getName() != null ? offline.getName() : q;
                for (ShopListing shop : shops.store().all()) {
                    if (shop.ownerUuid() != null && shop.ownerUuid().equalsIgnoreCase(id.toString())) {
                        return new OwnerMatch(id.toString(), shop.ownerName() != null ? shop.ownerName() : name);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static ItemStack categoryItem(RootMarketPlugin plugin, MarketCategory cat, int count) {
        ItemStack stack = new ItemStack(cat.icon(), 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(cat.display(), NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text(count + " item type(s) with live shops", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text(strip(plugin.rawMsg("category-hint")), NamedTextColor.AQUA)
                            .decoration(TextDecoration.ITALIC, false)));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack hubItem(HubRow row, MarketQuotes.Quote quote) {
        ItemStack stack = ShopItemKeys.stackForKey(row.itemKey());
        if (stack == null) {
            Material mat = ShopItemKeys.baseMaterial(row.itemKey());
            stack = new ItemStack(mat != null ? mat : Material.CHEST, 1);
        } else {
            stack = stack.clone();
            stack.setAmount(1);
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(pretty(row.itemKey()), NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Click to shop.", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("BUY", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
                    .decorate(TextDecoration.BOLD));
            if (quote != null && quote.hasBuy()) {
                lore.add(Component.text(MarketQuotes.formatUnitG(quote.buyUnit()), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text(MarketQuotes.formatStackG(quote.buyStack()), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("No sell shops in stock", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            lore.add(Component.text("SELL", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false)
                    .decorate(TextDecoration.BOLD));
            if (quote != null && quote.hasSell()) {
                lore.add(Component.text(MarketQuotes.formatUnitG(quote.sellUnit()), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text(MarketQuotes.formatStackG(quote.sellStack()), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("No buy shops with capacity", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            if (quote != null && quote.sellMultiPct() != null) {
                lore.add(Component.empty());
                NamedTextColor dyn = quote.sellMultiPct() >= 0 ? NamedTextColor.GREEN : NamedTextColor.LIGHT_PURPLE;
                lore.add(Component.text(MarketQuotes.formatDynamicSell(quote.sellMultiPct()), dyn)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack itemQuoteBook(
            RootMarketPlugin plugin,
            String itemKey,
            int listingCount,
            double cheapest,
            MarketQuotes.Quote quote,
            int page,
            int totalPages) {
        ItemStack stack = new ItemStack(Material.BOOK, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(Component.text(pretty(itemKey), NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(
                        strip(plugin.rawMsg("item-info")
                                .replace("{count}", String.valueOf(listingCount))
                                .replace("{price}", String.format(Locale.US, "%.3f", cheapest))),
                        NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        if (quote != null && quote.hasSell()) {
            lore.add(Component.text(
                            "Best /sell: " + MarketQuotes.formatUnitG(quote.sellUnit()),
                            NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            if (quote.sellMultiPct() != null) {
                lore.add(Component.text(MarketQuotes.formatDynamicSell(quote.sellMultiPct()), NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }
        lore.add(Component.text("Page " + (page + 1) + " / " + totalPages, NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(
                        strip(plugin.rawMsg("item-hint").replace("{item}", itemKey.toLowerCase(Locale.ROOT))),
                        NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack virtualListingItem(MixedOffer offer) {
        ItemStack stack = null;
        if (offer.virtualListing() != null) {
            stack = offer.virtualListing().stackSample();
        }
        if (stack == null) {
            stack = new ItemStack(Material.ENDER_CHEST, 1);
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            String seller = offer.ownerName() != null ? offer.ownerName() : "Seller";
            meta.displayName(Component.text(seller + " (virtual)", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text(String.format(Locale.US, "%.3f G ea · qty %d", offer.price(), offer.qty()), NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Click to buy into My Storage (/item)", NamedTextColor.AQUA)
                            .decoration(TextDecoration.ITALIC, false)));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack listingItem(RootMcShopsPlugin shops, ShopListing shop, boolean showOwner) {
        String itemKey = shops.itemKeyForStock(shop);
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
        int stock = shops.countStock(shop);
        int qty = Math.max(1, shop.saleQty());
        String priceLine = qty > 1
                ? String.format(Locale.US, "%.3f G each (%d per trade)", shop.price(), qty)
                : String.format(Locale.US, "%.3f G", shop.price());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(
                            showOwner
                                    ? (shop.ownerName() != null ? shop.ownerName() : "Seller")
                                    : pretty(itemKey),
                            NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            if (showOwner) {
                lore.add(Component.text(pretty(itemKey), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.text("Price: " + priceLine, NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Stock: " + stock, stock > 0 ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Click to buy", NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack nav(Material material, String name) {
        ItemStack stack = new ItemStack(material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(strip(name), NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack trendingInfo(
            RootMarketPlugin plugin,
            RootMcShopsPlugin shops,
            int listedTypes,
            int page,
            int totalPages) {
        boolean enabled = plugin.yaml().config().getBoolean("trending.enabled", true);
        if (!enabled) {
            return infoBook(
                    strip(plugin.rawMsg("hub-info")),
                    List.of(
                            listedTypes + " item type(s) listed",
                            "Page " + (page + 1) + " / " + totalPages,
                            strip(plugin.rawMsg("hub-hint"))));
        }

        MarketTrending.Snapshot snap = MarketTrending.compute(plugin, shops);
        ItemStack stack = new ItemStack(Material.SPYGLASS, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(Component.text(strip(plugin.rawMsg("trending-title")), NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();

        String mode = plugin.yaml().config().getString("trending.mode", "sell").trim().toLowerCase(Locale.ROOT);
        boolean showSell = !"buy".equals(mode);
        boolean showBuy = "buy".equals(mode) || "both".equals(mode);

        if (showSell) {
            appendTrendingSection(
                    lore,
                    strip(plugin.rawMsg("trending-sell-high")),
                    snap.highestSell(),
                    true);
            appendTrendingSection(
                    lore,
                    strip(plugin.rawMsg("trending-sell-low")),
                    snap.lowestSell(),
                    false);
        }
        if (showBuy) {
            appendTrendingSection(
                    lore,
                    strip(plugin.rawMsg("trending-buy-high")),
                    snap.highestBuy(),
                    true);
            appendTrendingSection(
                    lore,
                    strip(plugin.rawMsg("trending-buy-low")),
                    snap.lowestBuy(),
                    false);
        }

        if (lore.isEmpty()) {
            lore.add(Component.text(strip(plugin.rawMsg("trending-empty")), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(Component.text(
                        listedTypes + " listed · page " + (page + 1) + "/" + totalPages,
                        NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(strip(plugin.rawMsg("hub-hint")), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static void appendTrendingSection(
            List<Component> lore,
            String header,
            List<MarketTrending.Entry> entries,
            boolean high) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        if (!lore.isEmpty()) {
            lore.add(Component.empty());
        }
        NamedTextColor headerColor = high ? NamedTextColor.GREEN : NamedTextColor.RED;
        lore.add(Component.text(header, headerColor).decoration(TextDecoration.ITALIC, false));
        int rank = 1;
        for (MarketTrending.Entry e : entries) {
            NamedTextColor pctColor = e.multiPct() >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED;
            lore.add(Component.text("#" + rank + " ", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(pretty(e.itemKey()), NamedTextColor.WHITE)
                            .decoration(TextDecoration.ITALIC, false))
                    .append(Component.text(" " + MarketTrending.formatPct(e.multiPct()), pctColor)
                            .decoration(TextDecoration.ITALIC, false))
                    .append(Component.text(
                                    " (" + MarketTrending.formatStackG(e.stackPrice()) + ")",
                                    NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)));
            rank++;
        }
    }

    private static ItemStack infoBook(String title, List<String> loreLines) {
        ItemStack stack = new ItemStack(Material.BOOK, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(title, NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    static String pretty(String itemKey) {
        if (itemKey == null || itemKey.isBlank()) {
            return "Unknown";
        }
        String pretty = itemKey.toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String part : pretty.split(" ")) {
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

    static String strip(String input) {
        if (input == null) {
            return "";
        }
        return input.replace('&', '§').replaceAll("§[0-9a-fk-or]", "");
    }

    public record HubRow(String itemKey, double cheapestPrice, int totalStock, int sellerCount, int inStockCount) {}

    public record OwnerMatch(String ownerUuid, String ownerName) {}

    public record MixedOffer(
            String id,
            boolean virtual,
            double price,
            String ownerName,
            int qty,
            com.rootrecord.minecraft.rootmcshops.virtual.VirtualListing virtualListing) {}
}
