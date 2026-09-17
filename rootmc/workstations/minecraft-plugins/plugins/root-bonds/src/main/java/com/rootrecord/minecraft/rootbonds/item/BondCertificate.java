package com.rootrecord.minecraft.rootbonds.item;

import com.rootrecord.minecraft.common.GoldMoney;
import com.rootrecord.minecraft.rootbonds.RootBondsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class BondCertificate {

    private static final ZoneId HST = ZoneId.of("Pacific/Honolulu");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z", Locale.US).withZone(HST);

    private final NamespacedKey bondIdKey;
    private final NamespacedKey principalKey;
    private final NamespacedKey typeKey;

    public BondCertificate(RootBondsPlugin plugin) {
        this.bondIdKey = new NamespacedKey(plugin, "bond_id");
        this.principalKey = new NamespacedKey(plugin, "bond_principal");
        this.typeKey = new NamespacedKey(plugin, "bond_type");
    }

    public ItemStack create(UUID bondId, double principal, Instant issuedAt) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(Component.text("Bonded note", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(GoldMoney.format(principal) + " G in Server Reserve", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Issued " + DATE_FMT.format(issuedAt), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Right-click for details", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Redeem at /bonds", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(bondIdKey, PersistentDataType.STRING, bondId.toString());
        meta.getPersistentDataContainer().set(principalKey, PersistentDataType.DOUBLE, principal);
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, "note");
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack createRoot(UUID bondId, double principal, Instant issuedAt) {
        ItemStack stack = new ItemStack(Material.GOLDEN_CARROT);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(Component.text("Bonded Root", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Bonded Root", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(GoldMoney.format(principal) + " G unredeemable bond principal", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Earns bond coupons in Gen2 only", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("No physical gold redemption", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Issued " + DATE_FMT.format(issuedAt), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(bondIdKey, PersistentDataType.STRING, bondId.toString());
        meta.getPersistentDataContainer().set(principalKey, PersistentDataType.DOUBLE, principal);
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, "root");
        stack.setItemMeta(meta);
        return stack;
    }

    /** @deprecated use {@link #create(UUID, double, Instant)} */
    public ItemStack create(UUID bondId, String displayName, double principal, Instant issuedAt) {
        return create(bondId, principal, issuedAt);
    }

    public Double readPrincipal(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        Double value = stack.getItemMeta().getPersistentDataContainer().get(principalKey, PersistentDataType.DOUBLE);
        return value == null || value <= 0 ? null : value;
    }

    public UUID readBondId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        String raw = stack.getItemMeta().getPersistentDataContainer().get(bondIdKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean isBondedRoot(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        String type = stack.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        return "root".equalsIgnoreCase(type);
    }
}
