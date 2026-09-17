package com.rootrecord.minecraft.rootbanner.command;

import com.rootrecord.minecraft.common.ChatLinks;
import com.rootrecord.minecraft.common.RootMcEconomyResolver;
import com.rootrecord.minecraft.common.RootMcEconomyService;
import com.rootrecord.minecraft.rootbanner.RootBannerPlugin;
import com.rootrecord.minecraft.rootbanner.catalog.BannerGlyphCatalog;
import com.rootrecord.minecraft.rootbanner.order.BannerLine;
import com.rootrecord.minecraft.rootbanner.order.BannerOrderParser;
import com.rootrecord.minecraft.rootbanner.order.BannerQuote;
import com.rootrecord.minecraft.rootbanner.order.BannerShopService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class BannerCommand implements CommandExecutor, TabCompleter {

    private final RootBannerPlugin plugin;
    private final BannerShopService shop;

    public BannerCommand(RootBannerPlugin plugin, BannerShopService shop) {
        this.plugin = plugin;
        this.shop = shop;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rootbanner.use")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        if (!plugin.enabled()) {
            sender.sendMessage(plugin.msg("disabled"));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "help", "?" -> {
                sendHelp(sender);
                yield true;
            }
            case "colors", "colour", "colours" -> {
                sendColors(sender);
                yield true;
            }
            case "prices", "price" -> {
                sendPrices(sender);
                yield true;
            }
            case "reload" -> handleReload(sender);
            case "cancel" -> handleCancel(sender);
            case "confirm", "yes" -> handleConfirm(sender);
            default -> handleQuote(sender, String.join(" ", args));
        };
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("rootbanner.reload")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        plugin.reloadLocalConfig();
        sender.sendMessage(plugin.msg("reload-done"));
        return true;
    }

    private boolean handleCancel(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.colorize("&cPlayers only."));
            return true;
        }
        if (shop.pending(player.getUniqueId()) == null) {
            player.sendMessage(plugin.msg("nothing-pending"));
            return true;
        }
        shop.clear(player.getUniqueId());
        player.sendMessage(plugin.msg("cancelled"));
        return true;
    }

    private boolean handleConfirm(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.colorize("&cPlayers only."));
            return true;
        }
        boolean free = player.hasPermission("rootbanner.free");
        BannerShopService.FulfillResult result = shop.fulfill(player, free);
        if (result instanceof BannerShopService.FulfillResult.None) {
            player.sendMessage(plugin.msg("nothing-pending"));
            return true;
        }
        if (result instanceof BannerShopService.FulfillResult.Expired) {
            player.sendMessage(plugin.msg("expired"));
            return true;
        }
        if (result instanceof BannerShopService.FulfillResult.NoEconomy) {
            player.sendMessage(plugin.msg("economy-missing"));
            return true;
        }
        if (result instanceof BannerShopService.FulfillResult.CannotAfford) {
            BannerQuote quote = shop.pending(player.getUniqueId());
            double bal = balanceOf(player);
            player.sendMessage(plugin.colorize(plugin.rawMsg("cannot-afford")
                    .replace("{total}", BannerOrderParser.money(quote != null ? quote.total() : 0))
                    .replace("{balance}", BannerOrderParser.money(bal))));
            return true;
        }
        if (result instanceof BannerShopService.FulfillResult.ChargeFailed) {
            player.sendMessage(plugin.msg("charge-failed"));
            return true;
        }
        if (result instanceof BannerShopService.FulfillResult.Ok ok) {
            if (ok.droppedStacks() > 0) {
                player.sendMessage(plugin.colorize(plugin.rawMsg("success-dropped")
                        .replace("{total}", BannerOrderParser.money(ok.total()))
                        .replace("{kept}", String.valueOf(ok.kept()))
                        .replace("{dropped}", String.valueOf(ok.droppedStacks()))));
            } else {
                player.sendMessage(plugin.colorize(plugin.rawMsg("success")
                        .replace("{total}", BannerOrderParser.money(ok.total()))
                        .replace("{count}", String.valueOf(ok.count()))));
            }
        }
        return true;
    }

    private boolean handleQuote(CommandSender sender, String text) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.colorize("&cPlayers only."));
            return true;
        }

        BannerOrderParser parser = new BannerOrderParser(
                plugin.blankPrice(), plugin.letterPrice(), plugin.symbolPrice());
        BannerOrderParser.Result parsed = parser.parse(text);
        if (parsed instanceof BannerOrderParser.Result.Empty) {
            player.sendMessage(plugin.msg("empty-text"));
            return true;
        }
        if (parsed instanceof BannerOrderParser.Result.Unsupported unsupported) {
            player.sendMessage(plugin.colorize(plugin.rawMsg("unsupported")
                    .replace("{chars}", BannerOrderParser.formatUnsupported(unsupported.chars()))));
            return true;
        }
        BannerOrderParser.Result.Ok ok = (BannerOrderParser.Result.Ok) parsed;
        if (ok.lines().size() > plugin.maxBanners()) {
            player.sendMessage(plugin.colorize(plugin.rawMsg("too-many")
                    .replace("{max}", String.valueOf(plugin.maxBanners()))));
            return true;
        }

        BannerQuote quote = shop.putQuote(player.getUniqueId(), ok.lines());
        player.sendMessage(plugin.colorize(plugin.rawMsg("quote-header")
                .replace("{count}", String.valueOf(quote.count()))
                .replace("{total}", BannerOrderParser.money(quote.total()))));

        int index = 1;
        for (BannerLine line : quote.lines()) {
            player.sendMessage(plugin.colorize(plugin.rawMsg("quote-line")
                    .replace("{index}", String.valueOf(index++))
                    .replace("{label}", line.label())
                    .replace("{price}", BannerOrderParser.money(line.price()))));
        }

        double bal = balanceOf(player);
        player.sendMessage(plugin.colorize(plugin.rawMsg("quote-total")
                .replace("{total}", BannerOrderParser.money(quote.total()))
                .replace("{balance}", BannerOrderParser.money(bal))));

        int freeSlots = BannerShopService.emptyStorageSlots(player.getInventory());
        int need = quote.count();
        if (freeSlots < need) {
            player.sendMessage(plugin.colorize(plugin.rawMsg("inventory-warn")
                    .replace("{free}", String.valueOf(freeSlots))
                    .replace("{need}", String.valueOf(need))));
        } else {
            player.sendMessage(plugin.colorize(plugin.rawMsg("inventory-ok")
                    .replace("{free}", String.valueOf(freeSlots))
                    .replace("{need}", String.valueOf(need))));
        }

        player.sendMessage(plugin.msg("confirm-prompt"));
        player.sendMessage(ChatLinks.confirmCancel("/banner confirm", "/banner cancel"));
        return true;
    }

    private void sendHelp(CommandSender sender) {
        for (String line : plugin.helpLines()) {
            sender.sendMessage(plugin.colorize(line));
        }
    }

    private void sendColors(CommandSender sender) {
        sender.sendMessage(plugin.msg("colors-header"));
        sender.sendMessage(plugin.colorize("&f" + String.join("&7, &f", shop.sortedColorLabels())));
    }

    private void sendPrices(CommandSender sender) {
        sender.sendMessage(plugin.colorize(plugin.rawMsg("prices-line")
                .replace("{blank}", BannerOrderParser.money(plugin.blankPrice()))
                .replace("{letter}", BannerOrderParser.money(plugin.letterPrice()))
                .replace("{symbol}", BannerOrderParser.money(plugin.symbolPrice()))));
    }

    private double balanceOf(Player player) {
        RootMcEconomyService economy = RootMcEconomyResolver.resolve(plugin);
        if (economy == null) {
            return 0;
        }
        return economy.balance(player.getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("rootbanner.use")) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of(
                    "confirm", "cancel", "colors", "prices", "help", "red", "white", "black", "Hello"));
            if (sender.hasPermission("rootbanner.reload")) {
                options.add("reload");
            }
            BannerGlyphCatalog.colorNames().stream()
                    .filter(n -> !n.contains("_") || n.equals("light_blue") || n.equals("light_gray"))
                    .forEach(options::add);
            return StringUtil.copyPartialMatches(args[0], options, new ArrayList<>());
        }
        if (args.length >= 2) {
            String partial = args[args.length - 1].toLowerCase(Locale.ROOT);
            return DyeColorTabs.names().stream()
                    .filter(n -> n.startsWith(partial))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        return List.of();
    }

    private static final class DyeColorTabs {
        static List<String> names() {
            return Arrays.stream(org.bukkit.DyeColor.values())
                    .map(c -> c.name().toLowerCase(Locale.ROOT))
                    .toList();
        }
    }
}
