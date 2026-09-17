package com.rootrecord.minecraft.rootappreciation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Sunflower-based Appreciation Tokens with PDC custom id. */
public final class AppreciationTokenItem {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final JavaPlugin plugin;
    private final NamespacedKey tokenKey;
    private final NamespacedKey issueIdKey;

    public AppreciationTokenItem(JavaPlugin plugin) {
        this.plugin = plugin;
        this.tokenKey = new NamespacedKey(plugin, "appreciation_token");
        this.issueIdKey = new NamespacedKey(plugin, "appreciation_issue_id");
    }

    public boolean isAppreciationToken(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(tokenKey, PersistentDataType.BYTE);
    }

    public ItemStack create(AppreciationConfig config, int amount, String issueId) {
        ItemStack stack = new ItemStack(config.tokenMaterial(), Math.max(1, Math.min(64, amount)));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(LEGACY.deserialize(config.displayName()).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : config.lore()) {
            lore.add(LEGACY.deserialize(line == null ? "" : line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(tokenKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(
                issueIdKey,
                PersistentDataType.STRING,
                issueId == null || issueId.isBlank() ? UUID.randomUUID().toString() : issueId);
        stack.setItemMeta(meta);
        return stack;
    }

    public int countInInventory(org.bukkit.entity.Player player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isAppreciationToken(stack)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Remove up to qty Appreciation Tokens. Returns amount removed. */
    public int removeFromInventory(org.bukkit.entity.Player player, int qty) {
        if (qty <= 0) {
            return 0;
        }
        int need = qty;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize() && need > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (!isAppreciationToken(stack)) {
                continue;
            }
            int take = Math.min(need, stack.getAmount());
            int left = stack.getAmount() - take;
            if (left <= 0) {
                inv.setItem(i, null);
            } else {
                stack.setAmount(left);
            }
            need -= take;
        }
        return qty - need;
    }

    /** Refresh display name/lore on existing tokens in inventory (keeps PDC). */
    public void refreshInventoryLore(org.bukkit.entity.Player player, AppreciationConfig config) {
        if (player == null || config == null) {
            return;
        }
        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!isAppreciationToken(stack)) {
                continue;
            }
            ItemMeta meta = stack.getItemMeta();
            if (meta == null) {
                continue;
            }
            meta.displayName(LEGACY.deserialize(config.displayName()).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String line : config.lore()) {
                lore.add(LEGACY.deserialize(line == null ? "" : line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            stack.setItemMeta(meta);
            inv.setItem(i, stack);
        }
    }

    public NamespacedKey tokenKey() {
        return tokenKey;
    }
}
