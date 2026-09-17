package com.rootrecord.minecraft.rootappreciation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Builds and delivers one redeem reward (material / book / potion / tipped / free lotto). */
public final class AppreciationRewardGranter {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final RootAppreciationPlugin plugin;

    public AppreciationRewardGranter(RootAppreciationPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean hasEmptySlot(Player player) {
        PlayerInventory inv = player.getInventory();
        for (ItemStack stack : inv.getStorageContents()) {
            if (stack == null || stack.getType().isAir()) {
                return true;
            }
        }
        ItemStack off = inv.getItemInOffHand();
        return off == null || off.getType().isAir();
    }

    public boolean grant(Player player, AppreciationRewardPool.Reward reward) {
        if (reward == null) {
            return false;
        }
        if ("free_lotto".equalsIgnoreCase(reward.kind())) {
            return grantFreeLotto(player);
        }
        ItemStack stack = buildStack(reward);
        if (stack == null || stack.getType().isAir()) {
            plugin.getLogger().warning("Could not build reward: " + reward.id());
            return false;
        }
        return giveOrOffhand(player, stack);
    }

    private boolean giveOrOffhand(Player player, ItemStack stack) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        if (overflow.isEmpty()) {
            return true;
        }
        ItemStack rem = overflow.values().iterator().next();
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off == null || off.getType().isAir()) {
            player.getInventory().setItemInOffHand(rem);
            return true;
        }
        player.getWorld().dropItemNaturally(player.getLocation(), rem);
        player.sendMessage(plugin.msg("inventory-full"));
        return true;
    }

    private ItemStack buildStack(AppreciationRewardPool.Reward reward) {
        String kind = reward.kind() == null ? "" : reward.kind().toLowerCase(Locale.ROOT);
        int qty = Math.max(1, Math.min(64, reward.qty()));
        return switch (kind) {
            case "enchanted_book" -> enchantedBook(reward.enchant(), qty);
            case "potion" -> potion(Material.POTION, reward.potion(), qty);
            case "tipped_arrow" -> potion(Material.TIPPED_ARROW, reward.potion(), qty);
            case "material" -> material(reward.material(), qty);
            default -> null;
        };
    }

    private ItemStack material(String name, int qty) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Material mat = Material.matchMaterial(name);
        if (mat == null || mat.isAir()) {
            return null;
        }
        return new ItemStack(mat, qty);
    }

    private ItemStack enchantedBook(String enchantKey, int qty) {
        if (enchantKey == null || enchantKey.isBlank()) {
            return null;
        }
        Enchantment enchant = Enchantment.getByKey(NamespacedKey.minecraft(enchantKey.toLowerCase(Locale.ROOT)));
        if (enchant == null) {
            return null;
        }
        ItemStack stack = new ItemStack(Material.ENCHANTED_BOOK, qty);
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof EnchantmentStorageMeta storage)) {
            return null;
        }
        storage.addStoredEnchant(enchant, Math.max(1, enchant.getMaxLevel()), true);
        stack.setItemMeta(storage);
        return stack;
    }

    private ItemStack potion(Material base, String potionKey, int qty) {
        if (potionKey == null || potionKey.isBlank()) {
            return null;
        }
        PotionType type;
        try {
            type = PotionType.valueOf(potionKey.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
        ItemStack stack = new ItemStack(base, qty);
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof PotionMeta potionMeta)) {
            return null;
        }
        potionMeta.setBasePotionType(type);
        stack.setItemMeta(potionMeta);
        return stack;
    }

    private boolean grantFreeLotto(Player player) {
        org.bukkit.plugin.Plugin gamble = Bukkit.getPluginManager().getPlugin("Root-Gamble");
        if (gamble != null && gamble.isEnabled()) {
            try {
                Method grant = gamble.getClass().getMethod("grantFreePlay", UUID.class, String.class);
                grant.invoke(gamble, player.getUniqueId(), "lotto");
                return true;
            } catch (ReflectiveOperationException ex) {
                plugin.getLogger().warning("grantFreePlay failed: " + ex.getMessage());
            }
        }
        ItemStack paper = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = paper.getItemMeta();
        if (meta != null) {
            meta.displayName(LEGACY.deserialize("&6Free Lotto Ticket").decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    LEGACY.deserialize("&7Show staff — Root-Gamble was offline").decoration(TextDecoration.ITALIC, false),
                    LEGACY.deserialize("&8/lotto free-play credit").decoration(TextDecoration.ITALIC, false)));
            paper.setItemMeta(meta);
        }
        return giveOrOffhand(player, paper);
    }
}
