package com.rootrecord.minecraft.rootmcshops.virtual;

import com.rootrecord.minecraft.rootmcshops.RootMcShopsPlugin;
import com.rootrecord.minecraft.rootmcshops.ShopItemKeys;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Deposit / withdraw helpers between player inventory and virtual bins. */
public final class VirtualStorageService {

    private VirtualStorageService() {}

    public static int depositHand(RootMcShopsPlugin plugin, Player player) {
        VirtualItemStore bins = plugin.virtualItems();
        if (bins == null || !bins.enabled()) {
            player.sendMessage(plugin.msg("virtual-disabled"));
            return 0;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage(plugin.msg("virtual-deposit-empty"));
            return 0;
        }
        if (ShopItemKeys.isForbiddenGoldResource(hand)) {
            player.sendMessage(plugin.msg("forbidden-item"));
            return 0;
        }
        ItemStack copy = hand.clone();
        int amt = bins.deposit(player.getUniqueId(), copy);
        if (amt <= 0) {
            player.sendMessage(plugin.msg("virtual-deposit-failed"));
            return 0;
        }
        hand.setAmount(hand.getAmount() - amt);
        if (hand.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        }
        player.sendMessage(plugin.msg("virtual-deposit-success")
                .replace("{qty}", String.valueOf(amt))
                .replace("{item}", ShopItemKeys.fromItemStack(copy).toLowerCase(Locale.ROOT)));
        return amt;
    }

    public static int depositAll(RootMcShopsPlugin plugin, Player player) {
        VirtualItemStore bins = plugin.virtualItems();
        if (bins == null || !bins.enabled()) {
            player.sendMessage(plugin.msg("virtual-disabled"));
            return 0;
        }
        PlayerInventory inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (ShopItemKeys.isForbiddenGoldResource(stack)) {
                continue;
            }
            ItemStack copy = stack.clone();
            int amt = bins.deposit(player.getUniqueId(), copy);
            if (amt <= 0) {
                continue;
            }
            total += amt;
            int left = stack.getAmount() - amt;
            if (left <= 0) {
                inv.setItem(i, null);
            } else {
                stack.setAmount(left);
            }
        }
        if (total <= 0) {
            player.sendMessage(plugin.msg("virtual-deposit-empty"));
            return 0;
        }
        player.sendMessage(plugin.msg("virtual-deposit-all-success").replace("{qty}", String.valueOf(total)));
        return total;
    }

    public static int withdrawBin(RootMcShopsPlugin plugin, Player player, long binId, int qty) {
        VirtualItemStore bins = plugin.virtualItems();
        if (bins == null || !bins.enabled()) {
            player.sendMessage(plugin.msg("virtual-disabled"));
            return 0;
        }
        List<ItemStack> stacks = bins.withdraw(player.getUniqueId(), binId, qty);
        if (stacks.isEmpty()) {
            player.sendMessage(plugin.msg("virtual-withdraw-failed"));
            return 0;
        }
        int given = 0;
        PlayerInventory inv = player.getInventory();
        for (ItemStack stack : stacks) {
            given += stack.getAmount();
            Map<Integer, ItemStack> leftover = inv.addItem(stack);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
                player.sendMessage(plugin.msg("virtual-overflow-drop"));
            }
        }
        player.sendMessage(plugin.msg("virtual-withdraw-success").replace("{qty}", String.valueOf(given)));
        return given;
    }

    public static int withdrawMatching(RootMcShopsPlugin plugin, Player player, String itemKey, int qty) {
        VirtualItemStore bins = plugin.virtualItems();
        if (bins == null || !bins.enabled()) {
            player.sendMessage(plugin.msg("virtual-disabled"));
            return 0;
        }
        String key = ItemStackCodec.normalizeKey(itemKey);
        int remaining = qty;
        int total = 0;
        for (VirtualBinEntry entry : new ArrayList<>(bins.listBins(player.getUniqueId()))) {
            if (remaining <= 0) {
                break;
            }
            if (!entry.itemKey().equalsIgnoreCase(key)) {
                continue;
            }
            int take = Math.min(remaining, entry.qty());
            int got = withdrawBin(plugin, player, entry.id(), take);
            total += got;
            remaining -= got;
        }
        if (total <= 0) {
            player.sendMessage(plugin.msg("virtual-withdraw-failed"));
        }
        return total;
    }
}
