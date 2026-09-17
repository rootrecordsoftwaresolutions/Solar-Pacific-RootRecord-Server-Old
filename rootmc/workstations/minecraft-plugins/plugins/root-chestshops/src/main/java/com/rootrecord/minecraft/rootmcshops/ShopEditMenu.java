package com.rootrecord.minecraft.rootmcshops;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Owner sign menu  -  QuickShop-style edits. */
public final class ShopEditMenu {

    private ShopEditMenu() {}

    public static void open(RootMcShopsPlugin plugin, Player player, ShopListing shop) {
        player.sendMessage(plugin.colorize("&7Shop editor &8- &f" + shop.itemKey()));
        int stock = plugin.countStock(shop);
        int qty = Math.max(1, shop.saleQty());
        String priceLabel = qty > 1
                ? String.format(Locale.US, "%.3f G ea", shop.price())
                : String.format(Locale.US, "%.3f G", shop.price());
        player.sendMessage(plugin.colorize(String.format(
                "&7Type: &f%s &7| Price: &f%s &7| Qty per trade: &f%d &7| %s: &f%d",
                shop.isBuyShop() ? "BUY shop" : "SELL shop",
                priceLabel,
                qty,
                shop.isBuyShop() ? "Capacity" : "Stock",
                stock)));

        String typeCmd = shop.isBuyShop()
                ? "/shop edittype sell " + shop.id()
                : "/shop edittype buy " + shop.id();
        Component actions = Component.text()
                .append(button("[Change price]", "/shop editprice " + shop.id(), NamedTextColor.GREEN,
                        "Type a new price in chat"))
                .append(Component.text("  ", NamedTextColor.GRAY))
                .append(button("[Change qty]", "/shop editqty " + shop.id(), NamedTextColor.AQUA,
                        "Items per purchase/sale"))
                .append(Component.text("  ", NamedTextColor.GRAY))
                .append(button(shop.isBuyShop() ? "[Set SELL]" : "[Set BUY]", typeCmd, NamedTextColor.YELLOW,
                        shop.isBuyShop() ? "Shop sells items to players" : "Shop buys items from players"))
                .append(Component.text("  ", NamedTextColor.GRAY))
                .append(button("[Remove shop]", "/shop remove " + shop.id(), NamedTextColor.RED,
                        "Remove this shop and sign"))
                .append(Component.text("  ", NamedTextColor.GRAY))
                .append(button("[Cancel]", "/shop cancel", NamedTextColor.GRAY, "Close editor"))
                .build();
        player.sendMessage(actions);
        player.sendMessage(plugin.colorize(shop.isBuyShop()
                ? "&7Leave empty chest space for stock. Sellers use &f/sell {item}&7 or right-click your sign."
                        .replace("{item}", shop.itemKey().toLowerCase(Locale.ROOT))
                : "&7Right-click the container to open stock. Buyers use &f/buy {item}&7 or right-click your sign."
                        .replace("{item}", shop.itemKey().toLowerCase(Locale.ROOT))));
    }

    private static Component button(String label, String command, NamedTextColor color, String hover) {
        return Component.text(label, color, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(Component.text(hover, NamedTextColor.GRAY));
    }
}
