package com.rootrecord.minecraft.rootmarket;

import com.rootrecord.minecraft.rootmcshops.RootMcShopsPlugin;
import com.rootrecord.minecraft.rootmcshops.ShopItemKeys;
import com.rootrecord.minecraft.rootmcshops.ShopListing;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Top-N sell/buy multipliers vs worth (or peer median), for the market hub "Trending Items" panel.
 * Sell multi = best active buy-shop payout (/sell). Buy multi = cheapest in-stock sell listing.
 */
public final class MarketTrending {

    private MarketTrending() {}

    public record Entry(String itemKey, double multiPct, double unitPrice, double stackPrice) {}

    public record Snapshot(List<Entry> highestSell, List<Entry> lowestSell,
                           List<Entry> highestBuy, List<Entry> lowestBuy) {
        public boolean hasSell() {
            return !highestSell.isEmpty() || !lowestSell.isEmpty();
        }

        public boolean hasBuy() {
            return !highestBuy.isEmpty() || !lowestBuy.isEmpty();
        }
    }

    public static Snapshot compute(RootMarketPlugin plugin, RootMcShopsPlugin shops) {
        var cfg = plugin.yaml().config();
        if (!cfg.getBoolean("trending.enabled", true)) {
            return empty();
        }
        int topN = Math.max(1, Math.min(15, cfg.getInt("trending.top-n", 5)));
        int minShops = Math.max(1, cfg.getInt("trending.min-shops", 1));
        String baselineMode = cfg.getString("trending.baseline", "worth").trim().toLowerCase(Locale.ROOT);
        boolean skipWithoutBaseline = cfg.getBoolean("trending.skip-without-baseline", false);
        String mode = cfg.getString("trending.mode", "sell").trim().toLowerCase(Locale.ROOT);
        boolean wantSell = !"buy".equals(mode);
        boolean wantBuy = "buy".equals(mode) || "both".equals(mode);

        Map<String, Double> bestBuy = new HashMap<>();
        Map<String, Integer> buyCount = new HashMap<>();
        Map<String, Double> cheapestSell = new HashMap<>();
        Map<String, Integer> sellCount = new HashMap<>();
        Map<String, List<Double>> sellPrices = new HashMap<>();
        Map<String, List<Double>> buyPrices = new HashMap<>();

        for (ShopListing shop : shops.store().all()) {
            if (shop.itemKey() == null || shop.itemKey().isBlank() || shop.price() <= 0) {
                continue;
            }
            String key = shop.itemKey().toUpperCase(Locale.ROOT);
            int stock = shops.countStock(shop);
            if (stock <= 0) {
                continue;
            }
            if (shop.isBuyShop()) {
                buyCount.merge(key, 1, Integer::sum);
                bestBuy.merge(key, shop.price(), Math::max);
                buyPrices.computeIfAbsent(key, k -> new ArrayList<>()).add(shop.price());
            } else if (shop.isSellShop()) {
                sellCount.merge(key, 1, Integer::sum);
                cheapestSell.merge(key, shop.price(), Math::min);
                sellPrices.computeIfAbsent(key, k -> new ArrayList<>()).add(shop.price());
            }
        }

        List<Entry> sellEntries = new ArrayList<>();
        List<Entry> buyEntries = new ArrayList<>();

        if (wantSell) {
            for (Map.Entry<String, Double> e : bestBuy.entrySet()) {
                String key = e.getKey();
                if (buyCount.getOrDefault(key, 0) < minShops) {
                    continue;
                }
                double price = e.getValue();
                Double baseline = resolveBaseline(key, baselineMode, buyPrices.get(key), sellPrices.get(key), skipWithoutBaseline);
                if (baseline == null || baseline <= 0) {
                    continue;
                }
                double multi = (price / baseline - 1.0) * 100.0;
                sellEntries.add(new Entry(key, multi, price, price * stackSize(key)));
            }
        }

        if (wantBuy) {
            for (Map.Entry<String, Double> e : cheapestSell.entrySet()) {
                String key = e.getKey();
                if (sellCount.getOrDefault(key, 0) < minShops) {
                    continue;
                }
                double price = e.getValue();
                Double baseline = resolveBaseline(key, baselineMode, sellPrices.get(key), buyPrices.get(key), skipWithoutBaseline);
                if (baseline == null || baseline <= 0) {
                    continue;
                }
                double multi = (price / baseline - 1.0) * 100.0;
                buyEntries.add(new Entry(key, multi, price, price * stackSize(key)));
            }
        }

        sellEntries.sort(Comparator.comparingDouble(Entry::multiPct).reversed());
        buyEntries.sort(Comparator.comparingDouble(Entry::multiPct).reversed());

        return new Snapshot(
                takeTop(sellEntries, topN, true),
                takeTop(sellEntries, topN, false),
                takeTop(buyEntries, topN, true),
                takeTop(buyEntries, topN, false));
    }

    private static Snapshot empty() {
        return new Snapshot(List.of(), List.of(), List.of(), List.of());
    }

    private static List<Entry> takeTop(List<Entry> sortedHighToLow, int n, boolean highest) {
        if (sortedHighToLow.isEmpty()) {
            return List.of();
        }
        List<Entry> out = new ArrayList<>(n);
        if (highest) {
            for (int i = 0; i < Math.min(n, sortedHighToLow.size()); i++) {
                out.add(sortedHighToLow.get(i));
            }
        } else {
            for (int i = sortedHighToLow.size() - 1; i >= 0 && out.size() < n; i--) {
                out.add(sortedHighToLow.get(i));
            }
        }
        return out;
    }

    /**
     * @param peerPrices prices of the same side (for median-peer)
     * @param otherSidePrices opposite side (fallback when worth missing)
     */
    private static Double resolveBaseline(
            String itemKey,
            String baselineMode,
            List<Double> peerPrices,
            List<Double> otherSidePrices,
            boolean skipWithoutBaseline) {
        if ("median-peer".equals(baselineMode) || "median".equals(baselineMode)) {
            Double med = median(peerPrices);
            if (med != null && med > 0) {
                return med;
            }
            if (skipWithoutBaseline) {
                return null;
            }
        }
        Double worth = worthFor(itemKey);
        if (worth != null && worth > 0) {
            return worth;
        }
        if (skipWithoutBaseline) {
            return null;
        }
        Double other = median(otherSidePrices);
        if (other != null && other > 0) {
            return other;
        }
        return median(peerPrices);
    }

    private static Double median(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
        }
        return sorted.get(mid);
    }

    private static int stackSize(String itemKey) {
        Material mat = ShopItemKeys.baseMaterial(itemKey);
        if (mat == null || mat.isAir()) {
            return 64;
        }
        try {
            ItemStack probe = new ItemStack(mat, 1);
            return Math.max(1, probe.getMaxStackSize());
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

    public static String formatPct(double multiPct) {
        String sign = multiPct >= 0 ? "+" : "";
        return String.format(Locale.US, "%s%.0f%%", sign, multiPct);
    }

    public static String formatStackG(double stackPrice) {
        if (stackPrice >= 1_000_000) {
            return String.format(Locale.US, "%.2fM G/stack", stackPrice / 1_000_000.0);
        }
        if (stackPrice >= 1_000) {
            return String.format(Locale.US, "%.2fK G/stack", stackPrice / 1_000.0);
        }
        if (stackPrice >= 100 || Math.abs(stackPrice - Math.rint(stackPrice)) < 0.001) {
            return String.format(Locale.US, "%.0f G/stack", stackPrice);
        }
        return String.format(Locale.US, "%.2f G/stack", stackPrice);
    }
}
