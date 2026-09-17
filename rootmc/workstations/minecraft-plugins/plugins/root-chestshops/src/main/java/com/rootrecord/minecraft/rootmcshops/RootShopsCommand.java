package com.rootrecord.minecraft.rootmcshops;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.List;
import java.util.Locale;

public final class RootShopsCommand implements CommandExecutor, TabCompleter {

    private final RootMcShopsPlugin plugin;
    private final ShopStore store;
    private final ShopInputManager inputs;

    public RootShopsCommand(RootMcShopsPlugin plugin, ShopStore store, ShopInputManager inputs) {
        this.plugin = plugin;
        this.store = store;
        this.inputs = inputs;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.colorize("&eUsage: /" + label + " <create|stock|editprice|editqty|edittype|remove|cancel|reload|purge|avg>"));
            sender.sendMessage(plugin.colorize("&7Create: hold the item, look at a chest/barrel, &f/" + label + " create [price]&7 - price optional (total Gold per purchase)."));
            sender.sendMessage(plugin.colorize("&7Toggle buy/sell: &f/" + label + " edittype buy|sell&7 while looking at your shop sign."));
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> handleCreate(sender, args);
            case "stock" -> handleStock(sender, args);
            case "editprice" -> handleEditPrice(sender, label, args);
            case "editqty", "editquantity" -> handleEditQty(sender, label, args);
            case "edittype", "type" -> handleEditType(sender, label, args);
            case "remove", "delete" -> handleRemove(sender, args);
            case "cancel" -> handleCancel(sender);
            case "reload" -> {
                plugin.reloadLocalConfig();
                store.load();
                ShopService.refreshAllSigns(plugin, store);
                sender.sendMessage(plugin.colorize("&aRootMC Shops reloaded."));
                yield true;
            }
            case "purge" -> handlePurge(sender);
            case "avg" -> handleAvg(sender, label, args);
            default -> {
                sender.sendMessage(plugin.colorize("&eUsage: /" + label + " <create|stock|editprice|editqty|edittype|remove|cancel|reload|purge|avg>"));
                yield true;
            }
        };
    }

    private boolean handleEditType(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        ShopListing shop = null;
        String requested = null;
        if (args.length >= 3) {
            requested = args[1];
            shop = store.getById(args[2]);
        } else if (args.length == 2 && args[1].contains(":")) {
            shop = store.getById(args[1]);
        } else {
            if (args.length >= 2) {
                requested = args[1];
            }
            Block sign = targetSign(player);
            if (sign != null) {
                shop = store.getBySignBlock(sign);
            }
            if (shop == null) {
                Block chest = targetChest(player);
                if (chest != null) {
                    shop = ShopProtection.shopAnchorAt(store, chest);
                    if (shop == null) {
                        shop = ShopProtection.shopForContainerBlock(store, chest);
                    }
                }
            }
        }
        if (shop == null) {
            player.sendMessage(plugin.colorize("&eLook at your shop sign or use &f/" + label + " edittype buy|sell [shop-id]"));
            return true;
        }
        if (!ShopService.isOwner(shop, player) && !player.hasPermission("rootshops.admin")) {
            player.sendMessage(plugin.colorize("&cYou don't own this shop."));
            return true;
        }
        return ShopService.setListingType(plugin, store, player, shop, requested);
    }

    private boolean handleStock(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length >= 2 && "cancel".equalsIgnoreCase(args[1])) {
            player.sendMessage(plugin.msg("stock-cancelled"));
            return true;
        }
        if (args.length >= 2 && "confirm".equalsIgnoreCase(args[1])) {
            return ShopStockService.executeStock(player, plugin, plugin.economy());
        }
        ShopStockService.offerStock(player, plugin, plugin.economy());
        return true;
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        Double immediatePrice = null;
        if (args.length >= 2) {
            try {
                immediatePrice = Double.parseDouble(args[1]);
            } catch (NumberFormatException ex) {
                player.sendMessage(plugin.msg("create-invalid-price"));
                return true;
            }
        }
        RayTraceResult trace = player.rayTraceBlocks(5);
        Block chest = trace == null ? null : trace.getHitBlock();
        if (chest == null || !ShopContainers.isShopContainer(chest)) {
            player.sendMessage(plugin.msg("create-look-container"));
            return true;
        }
        BlockFace face = trace.getHitBlockFace() != null ? trace.getHitBlockFace() : BlockFace.NORTH;
        ShopService.beginCreate(plugin, store, inputs, player, chest, face, immediatePrice);
        return true;
    }

    private boolean handleEditPrice(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        ShopListing shop = resolveShop(player, args, 1);
        if (shop == null) {
            player.sendMessage(plugin.colorize("&eLook at your shop sign or use &f/" + label + " editprice <shop-id>"));
            return true;
        }
        if (!ShopService.isOwner(shop, player) && !player.hasPermission("rootshops.admin")) {
            player.sendMessage(plugin.colorize("&cYou don't own this shop."));
            return true;
        }
        inputs.put(player.getUniqueId(), ShopInputManager.Pending.editPrice(shop));
        int qty = Math.max(1, shop.saleQty());
        double unit = shop.price();
        player.sendMessage(plugin.msg("edit-enter-price")
                .replace("{price}", String.format(Locale.US, "%.3f", unit))
                .replace("{total}", String.format(Locale.US, "%.3f", unit * qty))
                .replace("{qty}", String.valueOf(qty))
                .replace("{item}", shop.itemKey().toLowerCase(Locale.ROOT).replace('_', ' ')));
        return true;
    }

    private boolean handleEditQty(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        ShopListing shop = resolveShop(player, args, 1);
        if (shop == null) {
            player.sendMessage(plugin.colorize("&eLook at your shop sign or use &f/" + label + " editqty <shop-id>"));
            return true;
        }
        if (!ShopService.isOwner(shop, player) && !player.hasPermission("rootshops.admin")) {
            player.sendMessage(plugin.colorize("&cYou don't own this shop."));
            return true;
        }
        inputs.put(player.getUniqueId(), ShopInputManager.Pending.editSaleQty(shop));
        player.sendMessage(plugin.msg("edit-enter-qty")
                .replace("{qty}", String.valueOf(shop.saleQty())));
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        ShopListing shop = resolveShop(player, args, 1);
        if (shop == null) {
            player.sendMessage(plugin.colorize("&eNo shop found."));
            return true;
        }
        if (!ShopService.isOwner(shop, player) && !player.hasPermission("rootshops.admin")) {
            player.sendMessage(plugin.colorize("&cYou don't own this shop."));
            return true;
        }
        ShopService.removeShop(plugin, store, shop, player);
        inputs.clear(player.getUniqueId());
        return true;
    }

    private boolean handleCancel(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        ShopInputManager.Pending pending = inputs.remove(player.getUniqueId());
        if (pending == null) {
            player.sendMessage(plugin.msg("edit-cancelled"));
            return true;
        }
        if (pending.kind() == ShopInputManager.Kind.CREATE_PRICE) {
            ShopService.abortCreate(plugin, store, player, pending);
        } else {
            player.sendMessage(plugin.msg("edit-cancelled"));
        }
        return true;
    }

    private boolean handlePurge(CommandSender sender) {
        if (!sender.hasPermission("rootshops.admin")) {
            sender.sendMessage(plugin.colorize("&cRequires rootshops.admin"));
            return true;
        }
        int count = 0;
        for (ShopListing shop : java.util.List.copyOf(store.all())) {
            ShopService.removeShop(plugin, store, shop, sender instanceof Player p ? p : null);
            count++;
        }
        sender.sendMessage(plugin.colorize("&aCleared &f" + count + "&a shop listing(s) from shops.yml (signs removed)."));
        sender.sendMessage(plugin.colorize("&7Web/API listings refresh when shop stock or price changes."));
        return true;
    }

    private boolean handleAvg(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.colorize("&eUsage: /" + label + " avg <item>"));
            return true;
        }
        String itemKey = args[1].toUpperCase(Locale.ROOT);
        var bridge = plugin.economyBridge();
        sender.sendMessage(plugin.colorize(String.format(Locale.US,
                "&7Avg &f%s&7: &f%.3f &7(max &f%.3f&7)",
                itemKey,
                bridge.averagePrice(itemKey),
                bridge.maxAllowedPrice(itemKey, plugin.capPercent()))));
        return true;
    }

    private ShopListing resolveShop(Player player, String[] args, int idIndex) {
        if (args.length > idIndex) {
            return store.getById(args[idIndex]);
        }
        Block sign = targetSign(player);
        if (sign != null) {
            ShopListing bySign = store.getBySignBlock(sign);
            if (bySign != null) {
                return bySign;
            }
        }
        Block chest = targetChest(player);
        if (chest != null) {
            ShopListing anchor = ShopProtection.shopAnchorAt(store, chest);
            if (anchor != null) {
                return anchor;
            }
            return ShopProtection.shopForContainerBlock(store, chest);
        }
        return null;
    }

    private static Block targetChest(Player player) {
        RayTraceResult trace = player.rayTraceBlocks(5);
        Block block = trace == null ? null : trace.getHitBlock();
        if (block != null && ShopContainers.isShopContainer(block)) {
            return block;
        }
        return null;
    }

    private static Block targetSign(Player player) {
        RayTraceResult trace = player.rayTraceBlocks(5);
        Block block = trace == null ? null : trace.getHitBlock();
        if (block != null && ShopSigns.isSignMaterial(block.getType())) {
            return block;
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("stock", "editprice", "editqty", "edittype", "remove", "cancel", "reload", "purge", "avg", "create").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("stock")) {
            return List.of("confirm", "cancel").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("edittype")) {
            return List.of("buy", "sell").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
