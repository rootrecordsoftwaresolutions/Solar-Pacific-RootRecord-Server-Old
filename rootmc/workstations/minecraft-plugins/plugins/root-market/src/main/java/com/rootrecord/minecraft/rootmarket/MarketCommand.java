package com.rootrecord.minecraft.rootmarket;

import com.rootrecord.minecraft.rootmcshops.RootMcShopsPlugin;
import com.rootrecord.minecraft.rootmcshops.ShopListing;
import com.rootrecord.minecraft.rootmcshops.ShopStore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * /market [/items] — hub, item, or player browse.
 * /shops &lt;player&gt; — player browse only.
 */
public final class MarketCommand implements CommandExecutor, TabCompleter {

    private final RootMarketPlugin plugin;

    public MarketCommand(RootMarketPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("rootmarket.use")) {
            player.sendMessage(plugin.colorize("&cNo permission."));
            return true;
        }
        RootMcShopsPlugin shops = RootMcShopsPlugin.get();
        if (shops == null) {
            player.sendMessage(plugin.msg("need-chestshops"));
            return true;
        }

        boolean shopsOnly = "shops".equalsIgnoreCase(command.getName());
        if (shopsOnly) {
            if (args.length < 1) {
                player.sendMessage(plugin.msg("usage-shops"));
                return true;
            }
            MarketMenus.openPlayer(plugin, shops, player, args[0]);
            return true;
        }

        if (args.length < 1) {
            MarketMenus.openHub(plugin, shops, player);
            return true;
        }

        String query = args[0].trim();
        String itemKey = resolveItemKey(query);
        if (itemKey != null) {
            MarketMenus.openItem(plugin, shops, player, itemKey);
            return true;
        }
        MarketMenus.openPlayer(plugin, shops, player, query);
        return true;
    }

    /** Prefer material/item-key when the arg looks like an item; else null (treat as player). */
    static String resolveItemKey(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String raw = query.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (raw.startsWith("MINECRAFT:")) {
            raw = raw.substring("MINECRAFT:".length());
        }
        Material mat = Material.matchMaterial(raw);
        if (mat != null && mat.isItem() && !mat.isAir()) {
            return mat.name();
        }
        // Allow known listing keys that aren't plain materials (e.g. enchanted book variants)
        RootMcShopsPlugin shops = RootMcShopsPlugin.get();
        if (shops != null) {
            for (ShopListing shop : shops.store().all()) {
                if (shop.isSellShop() && shop.itemKey() != null && shop.itemKey().equalsIgnoreCase(raw)) {
                    return shop.itemKey().toUpperCase(Locale.ROOT);
                }
            }
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        boolean shopsOnly = "shops".equalsIgnoreCase(command.getName());
        Set<String> out = new LinkedHashSet<>();

        RootMcShopsPlugin shops = RootMcShopsPlugin.get();
        ShopStore store = shops != null ? shops.store() : null;

        if (!shopsOnly && store != null) {
            for (ShopListing shop : store.all()) {
                if (!shop.isSellShop() || shop.itemKey() == null) {
                    continue;
                }
                String key = shop.itemKey().toLowerCase(Locale.ROOT);
                if (key.startsWith(prefix)) {
                    out.add(key);
                }
            }
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(online.getName());
            }
        }
        if (store != null) {
            for (ShopListing shop : store.all()) {
                if (shop.isSellShop() && shop.ownerName() != null
                        && shop.ownerName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(shop.ownerName());
                }
            }
        }

        List<String> list = new ArrayList<>(out);
        list.sort(String.CASE_INSENSITIVE_ORDER);
        if (list.size() > 40) {
            return list.subList(0, 40);
        }
        return list;
    }
}
