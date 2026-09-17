package com.rootrecord.minecraft.rootmcshops;

import com.rootrecord.minecraft.common.RootMcEconomyBridge;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.common.RootMcShopListingDto;
import com.rootrecord.minecraft.common.RootMcShopsExporter;
import com.rootrecord.minecraft.common.RootRecordFolders;
import com.rootrecord.minecraft.common.config.RootRecordYamlConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RootMcShopsPlugin extends JavaPlugin implements RootMcShopsExporter {

    private ShopStore store;
    private ShopEconomy economy;
    private RootRecordYamlConfig yamlConfig;
    private ShopInputManager inputManager;
    private double capPercent = 10;
    private boolean requireShopPlot = true;
    private List<String> shopPlotTypeNames = List.of("shop");
    /** Last live chest scan per shop — bulk cloud export reads this instead of loading every chunk. */
    private final ConcurrentHashMap<String, Integer> stockCache = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        yamlConfig = new RootRecordYamlConfig(this, "rootmc-shops.yml", RootRecordFolders.ROOTMC_SHOPS_CONFIG);
        yamlConfig.load();
        reloadLocalConfig();
        store = new ShopStore(this);
        store.load();
        ShopService.pruneStaleListings(this, store);
        inputManager = new ShopInputManager();
        try {
            ShopService.refreshAllSigns(this, store);
        } catch (Exception ex) {
            getLogger().warning("Shop sign refresh failed (shops still load): " + ex.getMessage());
        }

        if (!setupEconomy()) {
            getLogger().severe("No economy provider (Root Essentials or Vault) — disabling RootMC Shops.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new ShopProtectionListener(this, store, economy, inputManager), this);
        getServer().getPluginManager().registerEvents(new ShopChestListener(this, store, inputManager, economy), this);
        getServer().getPluginManager().registerEvents(new ShopSignListener(this, store, inputManager, economy), this);
        getServer().getPluginManager().registerEvents(new ShopChatListener(this, store, inputManager), this);
        ShopStockRefresh stockRefresh = new ShopStockRefresh(this, store);
        getServer().getPluginManager().registerEvents(new ShopStockSignListener(this, store, stockRefresh), this);
        ShopContainers.setStockChangeListener(stockRefresh::refreshNow);
        var cmd = getCommand("buy");
        if (cmd != null) {
            BuyCommand handler = new BuyCommand(this, store, economy);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }
        var sellCmd = getCommand("sell");
        if (sellCmd != null) {
            SellCommand sellHandler = new SellCommand(this, store, economy);
            sellCmd.setExecutor(sellHandler);
            sellCmd.setTabCompleter(sellHandler);
        }
        var admin = getCommand("shop");
        if (admin != null) {
            RootShopsCommand handler = new RootShopsCommand(this, store, inputManager);
            admin.setExecutor(handler);
            admin.setTabCompleter(handler);
        }
        var shopsCmd = getCommand("shops");
        if (shopsCmd != null) {
            ShopsCommand shopsHandler = new ShopsCommand(this, store);
            shopsCmd.setExecutor(shopsHandler);
            shopsCmd.setTabCompleter(shopsHandler);
            var itemsCmd = getCommand("items");
            if (itemsCmd != null) {
                itemsCmd.setExecutor(shopsHandler);
                itemsCmd.setTabCompleter(shopsHandler);
            }
            var marketCmd = getCommand("market");
            if (marketCmd != null) {
                marketCmd.setExecutor(shopsHandler);
                marketCmd.setTabCompleter(shopsHandler);
            }
        }
        getServer().getPluginManager().registerEvents(new PlayerShopsMenuListener(this, store), this);
        getLogger().info("RootMC Shops enabled.");
    }

    @Override
    public void onDisable() {
        ShopContainers.setStockChangeListener(null);
        if (store != null) {
            store.save();
        }
    }

    public void reloadLocalConfig() {
        if (yamlConfig != null) {
            yamlConfig.reload();
            capPercent = yamlConfig.config().getDouble("economy.max-price-percent-over-avg", 10);
            requireShopPlot = yamlConfig.config().getBoolean("towny.require-shop-plot", true);
            shopPlotTypeNames = yamlConfig.config().getStringList("towny.shop-plot-type-names");
            if (shopPlotTypeNames == null || shopPlotTypeNames.isEmpty()) {
                shopPlotTypeNames = List.of("shop");
            }
        }
    }

    public boolean requireShopPlot() {
        return requireShopPlot;
    }

    public List<String> shopPlotTypeNames() {
        return shopPlotTypeNames;
    }

    public double stockFeeAmount() {
        return yamlConfig.config().getDouble("fees.shop-stock", 5.0);
    }

    public ShopEconomy economy() {
        return economy;
    }

    public String msg(String key) {
        return colorize(rawMsg(key));
    }

    public String rawMsg(String key) {
        String prefix = yamlConfig.config().getString("messages.prefix", "");
        String body = yamlConfig.config().getString("messages." + key);
        if (body == null || body.isBlank()) {
            body = "forbidden-item".equals(key) ? "&cForbidden Item" : key;
        }
        return prefix + body;
    }

    public String colorize(String input) {
        return input == null ? "&8[&2RootMC Shops&8]&r " : input.replace('&', '\u00A7');
    }

    public double capPercent() {
        return capPercent;
    }

    public RootMcEconomyBridge economyBridge() {
        var plugin = Bukkit.getPluginManager().getPlugin("RootMC");
        if (plugin instanceof RootMcEconomyBridge bridge) {
            return bridge;
        }
        return new RootMcEconomyBridge() {
            @Override
            public double averagePrice(String itemKey) {
                return 0;
            }

            @Override
            public double maxAllowedPrice(String itemKey, double capPercentOverAvg) {
                return Double.MAX_VALUE;
            }
        };
    }

    public boolean validatePrice(String itemKey, double unitPrice) {
        if (unitPrice <= 0 || !Double.isFinite(unitPrice)) {
            return false;
        }
        if (capPercent <= 0) {
            return true;
        }
        double max = economyBridge().maxAllowedPrice(itemKey, capPercent);
        return unitPrice <= max;
    }

    public void sendPriceTooHigh(Player player, String itemKey, double unitPrice) {
        var bridge = economyBridge();
        double avg = bridge.averagePrice(itemKey);
        double max = bridge.maxAllowedPrice(itemKey, capPercent);
        player.sendMessage(colorize(msg("price-too-high")
                .replace("{item}", itemKey == null ? "?" : itemKey)
                .replace("{price}", String.format(java.util.Locale.US, "%.3f", unitPrice))
                .replace("{max}", String.format(java.util.Locale.US, "%.3f", max))
                .replace("{avg}", String.format(java.util.Locale.US, "%.3f", avg))));
    }

    public ShopInputManager inputManager() {
        return inputManager;
    }

    public ShopStore store() {
        return store;
    }

    private boolean setupEconomy() {
        var rootRsp = getServer().getServicesManager().getRegistration(RootMcEconomyService.class);
        RootMcEconomyService rootService = rootRsp != null ? rootRsp.getProvider() : null;

        net.milkbowl.vault.economy.Economy vault = null;
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            var vaultRsp = getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
            if (vaultRsp != null) {
                vault = vaultRsp.getProvider();
            }
        }
        economy = new ShopEconomy(rootService, vault);
        return economy.available();
    }

    @Override
    public String providerId() {
        return "rootmc-shops";
    }

    @Override
    public double medianInStockSellPrice(String itemKey) {
        if (itemKey == null || itemKey.isBlank()) {
            return 0;
        }
        String key = itemKey.toUpperCase(Locale.ROOT);
        List<Double> prices = new ArrayList<>();
        for (ShopListing shop : store.all()) {
            if (!shop.itemKey().equalsIgnoreCase(key)) {
                continue;
            }
            if (!shop.isSellShop()) {
                continue;
            }
            if (countStock(shop) <= 0) {
                continue;
            }
            double price = shop.price();
            if (price > 0) {
                prices.add(price);
            }
        }
        return medianPrice(prices);
    }

    private static double medianPrice(List<Double> prices) {
        if (prices.isEmpty()) {
            return 0;
        }
        List<Double> sorted = new ArrayList<>(prices);
        Collections.sort(sorted);
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(mid);
        }
        return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
    }

    @Override
    public List<RootMcShopListingDto> collectListings() {
        return store.all().stream()
                .map(shop -> new RootMcShopListingDto(
                        shop.id(),
                        shop.ownerUuid(),
                        shop.ownerName(),
                        shop.world(),
                        shop.x(),
                        shop.y(),
                        shop.z(),
                        shop.itemKey(),
                        shop.price(),
                        shop.normalizedType(),
                        exportedStock(shop)))
                .toList();
    }

    @Override
    public RootMcShopListingDto collectListing(String shopId) {
        ShopListing shop = store.getById(shopId);
        if (shop == null) {
            return null;
        }
        String itemKey = effectiveItemKey(shop);
        return new RootMcShopListingDto(
                shop.id(),
                shop.ownerUuid(),
                shop.ownerName(),
                shop.world(),
                shop.x(),
                shop.y(),
                shop.z(),
                itemKey,
                shop.price(),
                shop.normalizedType(),
                countStock(shop));
    }

    Optional<ShopListing> cheapest(String itemKey, int quantity) {
        String key = itemKey.toUpperCase(Locale.ROOT);
        return store.all().stream()
                .filter(ShopListing::isSellShop)
                .filter(s -> s.itemKey().equalsIgnoreCase(key))
                .filter(s -> countStock(s) > 0)
                .min(Comparator.comparingDouble(ShopListing::price));
    }

    int countStock(ShopListing shop) {
        return countStock(shop, false);
    }

    /** @param loadChunk load the shop chunk when needed (market /buy and /sell commands) */
    int countStock(ShopListing shop, boolean loadChunk) {
        String itemKey = effectiveItemKey(shop, loadChunk);
        int stock;
        if (shop.isBuyShop()) {
            var inv = ShopContainers.shopInventory(shop, loadChunk);
            stock = inv == null ? 0 : ShopContainers.countBuyCapacity(inv, itemKey);
        } else {
            stock = ShopContainers.countMatchingItems(shop, itemKey, loadChunk);
        }
        stockCache.put(shop.id(), stock);
        return stock;
    }

    private int exportedStock(ShopListing shop) {
        return stockCache.getOrDefault(shop.id(), 0);
    }

    /** Resolved item key for stock ops (upgrades generic ENCHANTED_BOOK from chest contents). */
    public String itemKeyForStock(ShopListing shop) {
        return effectiveItemKey(shop);
    }

    private String effectiveItemKey(ShopListing shop) {
        return effectiveItemKey(shop, false);
    }

    private String effectiveItemKey(ShopListing shop, boolean loadChunk) {
        String key = shop.itemKey();
        String resolved = ShopItemKeys.resolveListingKey(ShopContainers.shopInventory(shop, loadChunk), key);
        if (resolved != null && !resolved.equalsIgnoreCase(key)) {
            ShopListing updated = shop.withItemKey(resolved);
            store.put(updated);
            ShopSigns.updateSign(this, updated);
            ShopListingSync.onChanged(this, updated, key);
            return resolved;
        }
        return key;
    }

    private static Material materialForItemKey(String itemKey) {
        return ShopItemKeys.baseMaterial(itemKey);
    }

    boolean withdrawStock(ShopListing shop, int quantity) {
        String itemKey = effectiveItemKey(shop);
        if (itemKey == null || itemKey.isBlank()) {
            return false;
        }
        if (countStock(shop, true) < quantity) {
            return false;
        }
        return ShopContainers.withdrawMatchingItems(shop, itemKey, quantity);
    }

    boolean depositStock(ShopListing shop, int quantity) {
        String itemKey = effectiveItemKey(shop);
        if (itemKey == null || itemKey.isBlank()) {
            return false;
        }
        ItemStack template = ShopContainers.firstMatchingStack(shop, itemKey);
        return ShopContainers.depositMatchingItems(shop, itemKey, quantity, template);
    }

    void depositToOwner(ShopListing shop, double amount) {
        if (shop.ownerUuid() != null) {
            economy.depositToPlayer(java.util.UUID.fromString(shop.ownerUuid()), amount);
        }
    }
}
