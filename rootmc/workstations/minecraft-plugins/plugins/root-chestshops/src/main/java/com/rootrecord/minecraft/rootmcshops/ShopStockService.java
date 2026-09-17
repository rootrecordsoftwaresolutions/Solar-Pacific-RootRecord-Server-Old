package com.rootrecord.minecraft.rootmcshops;

import com.rootrecord.minecraft.common.ChatLinks;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Pays a merchant fee to move matching items from inventory into owned sell-shop chests. */
public final class ShopStockService {

    private ShopStockService() {}

    public static double stockFee(RootMcShopsPlugin plugin) {
        Plugin essentials = Bukkit.getPluginManager().getPlugin("Root-Essentials");
        double fallback = plugin.stockFeeAmount();
        if (essentials != null) {
            try {
                Object value = essentials.getClass()
                        .getMethod("serviceFee", String.class, double.class)
                        .invoke(essentials, "shop-stock", fallback);
                return ((Number) value).doubleValue();
            } catch (ReflectiveOperationException ignored) {
                // use shops.yml
            }
        }
        return fallback;
    }

    public static void offerStock(Player player, RootMcShopsPlugin plugin, ShopEconomy economy) {
        StockPlan plan = buildPlan(player, plugin);
        if (plan.lines().isEmpty()) {
            player.sendMessage(plugin.msg("stock-nothing"));
            return;
        }
        double fee = stockFee(plugin);
        player.sendMessage(plugin.msg("stock-confirm-prompt")
                .replace("{fee}", ShopBuyService.formatGold(fee)));
        for (String line : plan.lines()) {
            player.sendMessage(plugin.colorize(line));
        }
        if (fee > 0 && !economy.has(player, fee)) {
            player.sendMessage(plugin.msg("stock-insufficient")
                    .replace("{fee}", ShopBuyService.formatGold(fee))
                    .replace("{balance}", ShopBuyService.formatGold(economy.balance(player))));
            return;
        }
        player.sendMessage(ChatLinks.confirmCancel("/shop stock confirm", "/shop stock cancel"));
    }

    public static boolean executeStock(Player player, RootMcShopsPlugin plugin, ShopEconomy economy) {
        StockPlan plan = buildPlan(player, plugin);
        if (plan.lines().isEmpty()) {
            player.sendMessage(plugin.msg("stock-nothing"));
            return true;
        }
        double fee = stockFee(plugin);
        if (fee > 0) {
            if (!economy.has(player, fee)) {
                player.sendMessage(plugin.msg("stock-insufficient")
                        .replace("{fee}", ShopBuyService.formatGold(fee))
                        .replace("{balance}", ShopBuyService.formatGold(economy.balance(player))));
                return true;
            }
            if (!economy.withdraw(player, fee)) {
                player.sendMessage(plugin.msg("stock-insufficient")
                        .replace("{fee}", ShopBuyService.formatGold(fee))
                        .replace("{balance}", ShopBuyService.formatGold(economy.balance(player))));
                return true;
            }
        }
        int shopsStocked = 0;
        int itemsMoved = 0;
        for (PlannedLine line : plan.entries()) {
            if (!withdrawPlayerItems(player, line.itemKey(), line.quantity())) {
                continue;
            }
            ItemStack template = firstPlayerStack(player, line.itemKey());
            if (!ShopContainers.depositMatchingItems(line.shop(), line.itemKey(), line.quantity(), template)) {
                giveBack(player, line.itemKey(), line.quantity(), template);
                continue;
            }
            ShopSigns.refreshListingSign(plugin, plugin.store(), line.shop());
            ShopListingSync.onChanged(plugin, line.shop());
            shopsStocked++;
            itemsMoved += line.quantity();
        }
        if (shopsStocked == 0) {
            if (fee > 0) {
                economy.depositToPlayer(player.getUniqueId(), fee);
            }
            player.sendMessage(plugin.msg("stock-nothing"));
            return true;
        }
        if (fee > 0) {
            sinkMerchantFee(player, fee);
        }
        player.sendMessage(plugin.msg("stock-success")
                .replace("{shops}", String.valueOf(shopsStocked))
                .replace("{items}", String.valueOf(itemsMoved))
                .replace("{fee}", ShopBuyService.formatGold(fee)));
        return true;
    }

    private static void sinkMerchantFee(Player player, double fee) {
        Plugin essentials = Bukkit.getPluginManager().getPlugin("Root-Essentials");
        if (essentials == null) {
            return;
        }
        try {
            essentials.getClass()
                    .getMethod("sinkServiceFee", UUID.class, String.class, double.class, String.class)
                    .invoke(essentials, player.getUniqueId(), player.getName(), fee, "shop-stock");
        } catch (ReflectiveOperationException ignored) {
            // fee already withdrawn from wallet
        }
    }

    private static StockPlan buildPlan(Player player, RootMcShopsPlugin plugin) {
        List<PlannedLine> entries = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        java.util.Map<String, Integer> remainingByItem = new java.util.HashMap<>();
        for (ShopListing shop : plugin.store().all()) {
            if (!shop.isSellShop() || !ShopService.isOwner(shop, player)) {
                continue;
            }
            if (ShopContainers.liveShopBlock(shop) == null) {
                continue;
            }
            String itemKey = plugin.itemKeyForStock(shop);
            int playerQty = remainingByItem.computeIfAbsent(
                    itemKey, key -> countPlayerItems(player, key));
            if (playerQty <= 0) {
                continue;
            }
            Inventory inv = ShopContainers.shopInventory(shop);
            int room = ShopContainers.countBuyCapacity(inv, itemKey);
            int qty = Math.min(playerQty, room);
            if (qty <= 0) {
                continue;
            }
            remainingByItem.put(itemKey, playerQty - qty);
            entries.add(new PlannedLine(shop, itemKey, qty));
            lines.add("&8  &7" + itemKey.toUpperCase(Locale.ROOT)
                    + " &8×&f" + qty
                    + " &7→ shop &f" + shop.id());
        }
        return new StockPlan(entries, lines);
    }

    private static int countPlayerItems(Player player, String itemKey) {
        if (itemKey == null || itemKey.isBlank()) {
            return 0;
        }
        int count = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (ShopItemKeys.matches(stack, itemKey)) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private static ItemStack firstPlayerStack(Player player, String itemKey) {
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (ShopItemKeys.matches(stack, itemKey)) {
                return stack.clone();
            }
        }
        return null;
    }

    private static boolean withdrawPlayerItems(Player player, String itemKey, int quantity) {
        int remaining = quantity;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack stack = contents[slot];
            if (!ShopItemKeys.matches(stack, itemKey)) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) {
                contents[slot] = null;
            }
            remaining -= take;
        }
        player.getInventory().setStorageContents(contents);
        return remaining == 0;
    }

    private static void giveBack(Player player, String itemKey, int quantity, ItemStack template) {
        var mat = ShopItemKeys.baseMaterial(itemKey);
        if (mat == null || mat.isAir()) {
            return;
        }
        int remaining = quantity;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, mat.getMaxStackSize());
            ItemStack stack = template != null ? template.clone() : new ItemStack(mat, stackSize);
            stack.setAmount(stackSize);
            var leftover = player.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                leftover.values().forEach(item ->
                        player.getWorld().dropItemNaturally(player.getLocation(), item));
            }
            remaining -= stackSize;
        }
    }

    private record PlannedLine(ShopListing shop, String itemKey, int quantity) {}

    private record StockPlan(List<PlannedLine> entries, List<String> lines) {}
}
