package com.rootrecord.minecraft.rootmcshops.virtual;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.Base64;
import java.util.Locale;

/** Serialize exact ItemStacks for MySQL blobs (template amount = 1). */
public final class ItemStackCodec {

    private ItemStackCodec() {}

    public static String encode(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "";
        }
        ItemStack one = stack.clone();
        one.setAmount(1);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("i", one);
        return Base64.getEncoder().encodeToString(yaml.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static ItemStack decode(String blob) {
        if (blob == null || blob.isBlank()) {
            return null;
        }
        try {
            String yamlText = new String(Base64.getDecoder().decode(blob), java.nio.charset.StandardCharsets.UTF_8);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(yamlText);
            ItemStack stack = yaml.getItemStack("i");
            if (stack != null) {
                stack.setAmount(1);
            }
            return stack;
        } catch (Exception ex) {
            return null;
        }
    }

    public static ItemStack withAmount(ItemStack template, int amount) {
        if (template == null || amount <= 0) {
            return null;
        }
        ItemStack out = template.clone();
        out.setAmount(Math.min(amount, out.getMaxStackSize()));
        return out;
    }

    public static String fingerprint(String blob) {
        if (blob == null) {
            return "";
        }
        return blob.length() > 64 ? blob.substring(0, 64) : blob;
    }

    public static String normalizeKey(String itemKey) {
        return itemKey == null ? "" : itemKey.toUpperCase(Locale.ROOT);
    }
}
