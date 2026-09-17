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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class BondCertificate {

    private static final ZoneId HST = ZoneId.of("Pacific/Honolulu");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z", Locale.US).withZone(HST);

    /** Standalone Root-Bonds + brief Essentials host before Economy absorb. */
    private static final String[] LEGACY_NAMESPACES = {"root-bonds", "root-essentials"};

    private final NamespacedKey bondIdKey;
    private final NamespacedKey principalKey;
    private final NamespacedKey typeKey;
    private final List<NamespacedKey> bondIdKeys;
    private final List<NamespacedKey> principalKeys;
    private final List<NamespacedKey> typeKeys;
    private final NamespacedKey legacyBondIdKey;

    public BondCertificate(RootBondsPlugin plugin) {
        this.bondIdKey = new NamespacedKey(plugin.host(), "bond_id");
        this.principalKey = new NamespacedKey(plugin.host(), "bond_principal");
        this.typeKey = new NamespacedKey(plugin.host(), "bond_type");
        this.legacyBondIdKey = NamespacedKey.fromString("root-bonds:bond_id");

        this.bondIdKeys = buildKeyList(bondIdKey, "bond_id");
        this.principalKeys = buildKeyList(principalKey, "bond_principal");
        this.typeKeys = buildKeyList(typeKey, "bond_type");
    }

    private static List<NamespacedKey> buildKeyList(NamespacedKey primary, String key) {
        Set<NamespacedKey> keys = new LinkedHashSet<>();
        keys.add(primary);
        for (String ns : LEGACY_NAMESPACES) {
            NamespacedKey legacy = NamespacedKey.fromString(ns + ":" + key);
            if (legacy != null) {
                keys.add(legacy);
            }
        }
        return List.copyOf(keys);
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
        lore.add(Component.text("Earnings compound into this note", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Redeem in full at /bonds", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        writeKeys(meta.getPersistentDataContainer(), bondId, principal, "note");
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
        lore.add(Component.text("No principal redemption", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Issued " + DATE_FMT.format(issuedAt), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        writeKeys(meta.getPersistentDataContainer(), bondId, principal, "root");
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
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        for (NamespacedKey key : principalKeys) {
            Double value = pdc.get(key, PersistentDataType.DOUBLE);
            if (value != null && value > 0) {
                return value;
            }
        }
        return null;
    }

    public UUID readBondId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        UUID id = null;
        for (NamespacedKey key : bondIdKeys) {
            String raw = pdc.get(key, PersistentDataType.STRING);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                id = UUID.fromString(raw);
                break;
            } catch (IllegalArgumentException ignored) {
                // try next key
            }
        }
        if (id == null) {
            return null;
        }
        // Migrate legacy Root-Bonds / Essentials PDC onto Root-Economy (+ keep root-bonds for shops).
        if (!pdc.has(bondIdKey, PersistentDataType.STRING)) {
            Double principal = readPrincipal(stack);
            String type = readTypeRaw(pdc);
            writeKeys(pdc, id, principal != null ? principal : 0.0, type != null ? type : "note");
            stack.setItemMeta(meta);
        }
        return id;
    }

    public boolean isBondedRoot(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        String type = readTypeRaw(pdc);
        return "root".equalsIgnoreCase(type);
    }

    private String readTypeRaw(PersistentDataContainer pdc) {
        for (NamespacedKey key : typeKeys) {
            String type = pdc.get(key, PersistentDataType.STRING);
            if (type != null && !type.isBlank()) {
                return type;
            }
        }
        return null;
    }

    private void writeKeys(PersistentDataContainer pdc, UUID bondId, double principal, String type) {
        String id = bondId.toString();
        pdc.set(bondIdKey, PersistentDataType.STRING, id);
        pdc.set(principalKey, PersistentDataType.DOUBLE, principal);
        pdc.set(typeKey, PersistentDataType.STRING, type);
        // Keep legacy Root-Bonds keys so shop/chest scanners still recognize notes.
        if (legacyBondIdKey != null) {
            pdc.set(legacyBondIdKey, PersistentDataType.STRING, id);
            NamespacedKey legacyPrincipal = NamespacedKey.fromString("root-bonds:bond_principal");
            NamespacedKey legacyType = NamespacedKey.fromString("root-bonds:bond_type");
            if (legacyPrincipal != null) {
                pdc.set(legacyPrincipal, PersistentDataType.DOUBLE, principal);
            }
            if (legacyType != null) {
                pdc.set(legacyType, PersistentDataType.STRING, type);
            }
        }
    }
}
