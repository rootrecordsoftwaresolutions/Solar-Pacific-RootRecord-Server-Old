package com.rootrecord.minecraft.rootmarket;

import com.rootrecord.minecraft.rootmcshops.RootMcShopsPlugin;
import com.rootrecord.minecraft.rootmcshops.ShopItemKeys;
import com.rootrecord.minecraft.rootmcshops.ShopListing;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Live player-shop quotes for one item (BUY = sell shops, SELL = buy shops /sell). */
public final class MarketQuotes {

    private MarketQuotes() {}

    public record Quote(
            String itemKey,
            Double buyUnit,
            int buyShops,
            int buyStock,
            Double sellUnit,
            int sellShops,
            Double sellMultiPct,
            int stackSize) {

        public boolean hasBuy() {
            return buyUnit != null && buyUnit > 0;
        }

        public boolean hasSell() {
            return sellUnit != null && sellUnit > 0;
        }

        public double buyStack() {
            return hasBuy() ? buyUnit * stackSize : 0;
        }

        public double sellStack() {
            return hasSell() ? sellUnit * stackSize : 0;
        }
    }

    /** Precomputed quotes for the whole market (one pass over listings). */
    public static final class Index {
        private final Map<String, Quote> byKey;

        private Index(Map<String, Quote> byKey) {
            this.byKey = byKey;
        }

        public Quote get(String itemKey) {
            if (itemKey == null || itemKey.isBlank()) {
                return null;
            }
            return byKey.get(itemKey.toUpperCase(Locale.ROOT));
        }

        public Map<String, Quote> all() {
            return byKey;
        }
    }

    public static Index build(RootMcShopsPlugin shops) {
        Map<String, Agg> map = new HashMap<>();
        for (ShopListing shop : shops.store().all()) {
            if (shop.itemKey() == null || shop.itemKey().isBlank() || shop.price() <= 0) {
                continue;
            }
            String key = shop.itemKey().toUpperCase(Locale.ROOT);
            int stock = shops.countStock(shop);
            if (stock <= 0) {
                continue;
            }
            Agg agg = map.computeIfAbsent(key, Agg::new);
            if (shop.isBuyShop()) {
                agg.sellShops++;
                agg.bestSell = agg.bestSell == null ? shop.price() : Math.max(agg.bestSell, shop.price());
            } else {
                agg.buyShops++;
                agg.buyStock += stock;
                agg.cheapestBuy = agg.cheapestBuy == null ? shop.price() : Math.min(agg.cheapestBuy, shop.price());
            }
        }

        var virtual = shops.virtualListings();
        if (virtual != null && virtual.enabled()) {
            for (var listing : virtual.allSell()) {
                if (listing.qty() <= 0 || listing.price() <= 0) {
                    continue;
                }
                String key = listing.itemKey().toUpperCase(Locale.ROOT);
                Agg agg = map.computeIfAbsent(key, Agg::new);
                agg.buyShops++;
                agg.buyStock += listing.qty();
                agg.cheapestBuy = agg.cheapestBuy == null
                        ? listing.price()
                        : Math.min(agg.cheapestBuy, listing.price());
            }
            for (var listing : virtual.allBuy()) {
                if (listing.qty() <= 0 || listing.price() <= 0) {
                    continue;
                }
                String key = listing.itemKey().toUpperCase(Locale.ROOT);
                Agg agg = map.computeIfAbsent(key, Agg::new);
                agg.sellShops++;
                agg.bestSell = agg.bestSell == null
                        ? listing.price()
                        : Math.max(agg.bestSell, listing.price());
            }
        }

        Map<String, Quote> out = new HashMap<>();
        for (Map.Entry<String, Agg> e : map.entrySet()) {
            String key = e.getKey();
            Agg a = e.getValue();
            int stack = stackSize(key);
            Double multi = null;
            if (a.bestSell != null && a.bestSell > 0) {
                Double baseline = worthFor(key);
                if (baseline != null && baseline > 0) {
                    multi = (a.bestSell / baseline - 1.0) * 100.0;
                } else if (a.cheapestBuy != null && a.cheapestBuy > 0) {
                    multi = (a.bestSell / a.cheapestBuy - 1.0) * 100.0;
                }
            }
            out.put(key, new Quote(
                    key,
                    a.cheapestBuy,
                    a.buyShops,
                    a.buyStock,
                    a.bestSell,
                    a.sellShops,
                    multi,
                    stack));
        }
        return new Index(out);
    }

    public static String formatUnitG(double unit) {
        if (unit >= 1_000_000) {
            return String.format(Locale.US, "%.2fM G/ea", unit / 1_000_000.0);
        }
        if (unit >= 1_000) {
            return String.format(Locale.US, "%,.0f G/ea", unit);
        }
        if (unit >= 100 || Math.abs(unit - Math.rint(unit)) < 0.001) {
            return String.format(Locale.US, "%,.0f G/ea", unit);
        }
        if (unit >= 10) {
            return String.format(Locale.US, "%.1f G/ea", unit);
        }
        return String.format(Locale.US, "%.2f G/ea", unit);
    }

    public static String formatStackG(double stack) {
        return MarketTrending.formatStackG(stack);
    }

    public static String formatDynamicSell(Double multiPct) {
        if (multiPct == null) {
            return "◆ Dynamic Sell Value: n/a";
        }
        return "◆ Dynamic Sell Value: " + MarketTrending.formatPct(multiPct);
    }

    private static final class Agg {
        Double cheapestBuy;
        Double bestSell;
        int buyShops;
        int sellShops;
        int buyStock;

        Agg(String ignored) {}
    }

    private static int stackSize(String itemKey) {
        Material mat = ShopItemKeys.baseMaterial(itemKey);
        if (mat == null || mat.isAir()) {
            return 64;
        }
        try {
            return Math.max(1, new ItemStack(mat, 1).getMaxStackSize());
        } catch (Exception e) {
            return 64;
        }
    }

    private static Double worthFor(String itemKey) {
        Material mat = ShopItemKeys.baseMaterial(itemKey);
        if (mat == null || mat.isAir()) {
            return null;
        }
        Plugin eco = Bukkit.getPluginManager().getPlugin("Root-Economy");
        if (eco == null || !eco.isEnabled()) {
            return null;
        }
        try {
            Method worth = eco.getClass().getMethod("worth", Material.class);
            Object result = worth.invoke(eco, mat);
            if (result instanceof Number n && n.doubleValue() > 0) {
                return n.doubleValue();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }
}
