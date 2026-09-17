package com.rootrecord.minecraft.rootmemberships;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Tradeable Pro membership voucher (PDC). */
public final class ProVoucherItem {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final NamespacedKey voucherKey;
    private final NamespacedKey voucherIdKey;
    private final NamespacedKey tierKey;

    public ProVoucherItem(JavaPlugin plugin) {
        this.voucherKey = new NamespacedKey(plugin, "pro_voucher");
        this.voucherIdKey = new NamespacedKey(plugin, "pro_voucher_id");
        this.tierKey = new NamespacedKey(plugin, "pro_voucher_tier");
    }

    public boolean isProVoucher(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(voucherKey, PersistentDataType.BYTE);
    }

    public String voucherIdOf(ItemStack stack) {
        if (!isProVoucher(stack)) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(voucherIdKey, PersistentDataType.STRING);
    }

    public String tierOf(ItemStack stack) {
        if (!isProVoucher(stack)) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
    }

    public ItemStack create(MembershipsConfig config, String tier, String voucherId) {
        String t = tier == null ? "one_month" : tier.toLowerCase(Locale.ROOT);
        boolean life = "lifetime".equals(t) || "life".equals(t);
        Material mat = life ? config.voucherLifeMaterial() : config.voucherMonthMaterial();
        String name = life ? config.voucherLifeName() : config.voucherMonthName();
        List<String> loreSrc = life ? config.voucherLifeLore() : config.voucherMonthLore();

        ItemStack stack = new ItemStack(mat, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(LEGACY.deserialize(name).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : loreSrc) {
            lore.add(LEGACY.deserialize(line == null ? "" : line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        String id = voucherId == null || voucherId.isBlank() ? UUID.randomUUID().toString() : voucherId;
        meta.getPersistentDataContainer().set(voucherKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(voucherIdKey, PersistentDataType.STRING, id);
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, life ? "lifetime" : "one_month");
        stack.setItemMeta(meta);
        return stack;
    }
}
