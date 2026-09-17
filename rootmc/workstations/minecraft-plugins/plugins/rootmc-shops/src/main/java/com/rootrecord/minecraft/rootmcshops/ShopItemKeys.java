package com.rootrecord.minecraft.rootmcshops;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Stable item keys for shop listings (enchanted books, potions, etc.). */
public final class ShopItemKeys {

    private static final String BOOK = "ENCHANTED_BOOK";
    private static final String BOOK_PREFIX = BOOK + "_";
    /** Root-Bonds certificate (paper + bond_id PDC). */
    public static final String BONDED_NOTE = "BONDED_NOTE";
    private static final NamespacedKey BOND_ID_KEY = NamespacedKey.fromString("root-bonds:bond_id");

    private static final List<Material> POTION_MATERIALS = List.of(
            Material.SPLASH_POTION,
            Material.LINGERING_POTION,
            Material.TIPPED_ARROW,
            Material.POTION
    );

    private ShopItemKeys() {}

    public record ParsedEnchantedBook(String enchant, int level) {}

    public record ParsedPotion(Material material, String potionType) {}

    public static String fromItemStack(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        if (stack.getType() == Material.ENCHANTED_BOOK) {
            String bookKey = enchantedBookKey(stack);
            if (bookKey != null) {
                return bookKey;
            }
        }
        if (isPotionMaterial(stack.getType())) {
            String potionKey = potionKey(stack);
            if (potionKey != null) {
                return potionKey;
            }
        }
        if (isBondedNote(stack)) {
            return BONDED_NOTE;
        }
        return stack.getType().name();
    }

    public static boolean isBondedNote(ItemStack stack) {
        if (stack == null || stack.getType() != Material.PAPER || BOND_ID_KEY == null || !stack.hasItemMeta()) {
            return false;
        }
        String raw = stack.getItemMeta().getPersistentDataContainer().get(BOND_ID_KEY, PersistentDataType.STRING);
        return raw != null && !raw.isBlank();
    }

    private static boolean isPotionMaterial(Material material) {
        return POTION_MATERIALS.contains(material);
    }

    private static String potionKey(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof PotionMeta potion)) {
            return stack.getType().name();
        }
        PotionType type = potion.getBasePotionType();
        if (type == null) {
            return stack.getType().name();
        }
        return stack.getType().name() + "_" + type.name();
    }

    private static String enchantedBookKey(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof EnchantmentStorageMeta storage)) {
            return null;
        }
        Map<Enchantment, Integer> stored = storage.getStoredEnchants();
        if (stored.isEmpty()) {
            return BOOK;
        }
        if (stored.size() != 1) {
            return BOOK;
        }
        Map.Entry<Enchantment, Integer> entry = stored.entrySet().iterator().next();
        return bookKey(entry.getKey(), entry.getValue());
    }

    private static String bookKey(Enchantment enchantment, int level) {
        return BOOK_PREFIX + enchantPart(enchantment) + "_" + level;
    }

    private static String enchantPart(Enchantment enchantment) {
        return enchantment.getKey().getKey().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    public static ParsedEnchantedBook parseEnchantedBookKey(String itemKey) {
        if (itemKey == null) {
            return null;
        }
        String upper = itemKey.toUpperCase(Locale.ROOT);
        if (!upper.startsWith(BOOK_PREFIX)) {
            return null;
        }
        String rest = upper.substring(BOOK_PREFIX.length());
        int last = rest.lastIndexOf('_');
        if (last <= 0) {
            return null;
        }
        try {
            int level = Integer.parseInt(rest.substring(last + 1));
            if (level < 1) {
                return null;
            }
            String enchant = rest.substring(0, last);
            if (enchant.isBlank()) {
                return null;
            }
            return new ParsedEnchantedBook(enchant, level);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static ParsedPotion parsePotionKey(String itemKey) {
        if (itemKey == null || itemKey.isBlank()) {
            return null;
        }
        String upper = itemKey.toUpperCase(Locale.ROOT);
        for (Material material : POTION_MATERIALS) {
            String prefix = material.name() + "_";
            if (!upper.startsWith(prefix)) {
                continue;
            }
            String potionType = upper.substring(prefix.length());
            if (potionType.isBlank()) {
                return null;
            }
            try {
                PotionType.valueOf(potionType);
            } catch (IllegalArgumentException ex) {
                return null;
            }
            return new ParsedPotion(material, potionType);
        }
        return null;
    }

    public static Material baseMaterial(String itemKey) {
        if (itemKey == null || itemKey.isBlank()) {
            return null;
        }
        ParsedPotion potion = parsePotionKey(itemKey);
        if (potion != null) {
            return potion.material();
        }
        String base = itemKey;
        if (BONDED_NOTE.equalsIgnoreCase(itemKey)) {
            return Material.PAPER;
        }
        if (parseEnchantedBookKey(itemKey) != null || BOOK.equalsIgnoreCase(itemKey)) {
            base = BOOK;
        }
        Material mat = Material.matchMaterial(base);
        if (mat == null) {
            mat = Material.matchMaterial(base.toUpperCase(Locale.ROOT));
        }
        return mat;
    }

    /**
     * Mint-peg / ore gold resources — not tradeable in shops or the marketplace.
     * Tools, armor, apples, and other crafted gold items are allowed.
     */
    public static boolean isForbiddenGoldResource(Material material) {
        if (material == null) {
            return false;
        }
        return switch (material) {
            case GOLD_NUGGET, GOLD_INGOT, GOLD_BLOCK, RAW_GOLD, RAW_GOLD_BLOCK,
                    GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> true;
            default -> false;
        };
    }

    public static boolean isForbiddenGoldResource(ItemStack stack) {
        return stack != null && isForbiddenGoldResource(stack.getType());
    }

    public static boolean isForbiddenGoldResourceKey(String itemKey) {
        return isForbiddenGoldResource(baseMaterial(itemKey));
    }

    /** Build a single item for delivery when no chest template is available. */
    public static ItemStack stackForKey(String itemKey) {
        ParsedEnchantedBook parsedBook = parseEnchantedBookKey(itemKey);
        if (parsedBook != null) {
            ItemStack stack = new ItemStack(Material.ENCHANTED_BOOK, 1);
            ItemMeta meta = stack.getItemMeta();
            if (!(meta instanceof EnchantmentStorageMeta storage)) {
                return stack;
            }
            Enchantment enchant = Enchantment.getByKey(
                    org.bukkit.NamespacedKey.minecraft(parsedBook.enchant().toLowerCase(Locale.ROOT)));
            if (enchant == null) {
                return stack;
            }
            storage.addStoredEnchant(enchant, parsedBook.level(), true);
            stack.setItemMeta(storage);
            return stack;
        }
        ParsedPotion parsedPotion = parsePotionKey(itemKey);
        if (parsedPotion != null) {
            ItemStack stack = new ItemStack(parsedPotion.material(), 1);
            ItemMeta meta = stack.getItemMeta();
            if (!(meta instanceof PotionMeta potionMeta)) {
                return stack;
            }
            potionMeta.setBasePotionType(PotionType.valueOf(parsedPotion.potionType()));
            stack.setItemMeta(potionMeta);
            return stack;
        }
        return null;
    }

    public static boolean matches(ItemStack stack, String itemKey) {
        if (stack == null || stack.getType().isAir() || itemKey == null || itemKey.isBlank()) {
            return false;
        }
        if (BONDED_NOTE.equalsIgnoreCase(itemKey)) {
            return isBondedNote(stack);
        }
        if (BOOK.equalsIgnoreCase(itemKey)) {
            return stack.getType() == Material.ENCHANTED_BOOK;
        }
        String stackKey = fromItemStack(stack);
        return stackKey != null && itemKey.equalsIgnoreCase(stackKey);
    }

    /** Upgrade generic keys when chest stock is a single specific variant. */
    public static String resolveListingKey(Inventory inv, String currentKey) {
        if (currentKey == null || inv == null) {
            return currentKey;
        }
        if (currentKey.equalsIgnoreCase(BOOK)) {
            return resolveFromInventory(inv, Material.ENCHANTED_BOOK, BOOK);
        }
        if (currentKey.equalsIgnoreCase("PAPER") || currentKey.equalsIgnoreCase(BONDED_NOTE)) {
            return resolveBondedNoteKey(inv, currentKey);
        }
        for (Material material : POTION_MATERIALS) {
            if (currentKey.equalsIgnoreCase(material.name())) {
                return resolveFromInventory(inv, material, material.name());
            }
        }
        return currentKey;
    }

    private static String resolveFromInventory(Inventory inv, Material material, String genericKey) {
        String inferred = null;
        for (ItemStack stack : inv.getContents()) {
            if (stack == null || stack.getType() != material) {
                continue;
            }
            String key = fromItemStack(stack);
            if (key == null || key.equalsIgnoreCase(genericKey)) {
                continue;
            }
            if (inferred == null) {
                inferred = key;
            } else if (!inferred.equalsIgnoreCase(key)) {
                return genericKey;
            }
        }
        return inferred != null ? inferred : genericKey;
    }

    private static String resolveBondedNoteKey(Inventory inv, String currentKey) {
        if (inv == null) {
            return currentKey;
        }
        boolean anyBond = false;
        boolean anyPlainPaper = false;
        for (ItemStack stack : inv.getContents()) {
            if (stack == null || stack.getType() != Material.PAPER) {
                continue;
            }
            if (isBondedNote(stack)) {
                anyBond = true;
            } else {
                anyPlainPaper = true;
            }
        }
        if (anyBond && !anyPlainPaper) {
            return BONDED_NOTE;
        }
        return currentKey;
    }

    public static String prettyName(String itemKey) {
        if (itemKey == null || itemKey.isBlank()) {
            return "";
        }
        if (BONDED_NOTE.equalsIgnoreCase(itemKey)) {
            return "Bonded note";
        }
        ParsedEnchantedBook parsedBook = parseEnchantedBookKey(itemKey);
        if (parsedBook != null) {
            return titleCase(parsedBook.enchant.replace('_', ' ')) + " " + roman(parsedBook.level);
        }
        ParsedPotion parsedPotion = parsePotionKey(itemKey);
        if (parsedPotion != null) {
            return potionPrettyName(parsedPotion);
        }
        return titleCase(itemKey.toLowerCase(Locale.ROOT).replace('_', ' '));
    }

    private static String potionPrettyName(ParsedPotion parsed) {
        String effect = titleCase(parsed.potionType().toLowerCase(Locale.ROOT).replace('_', ' '));
        return switch (parsed.material()) {
            case SPLASH_POTION -> "Splash " + effect;
            case LINGERING_POTION -> "Lingering " + effect;
            case TIPPED_ARROW -> "Arrow of " + effect;
            default -> effect;
        };
    }

    public static String normalizeQuery(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (BONDED_NOTE.equals(s) || "BOND".equals(s) || "BONDS".equals(s) || "BONDEDNOTE".equals(s)) {
            return BONDED_NOTE;
        }
        ParsedPotion parsed = parsePotionKey(s);
        if (parsed != null) {
            return parsed.material().name() + "_" + parsed.potionType();
        }
        Material mat = Material.matchMaterial(s);
        if (mat != null && mat.isItem()) {
            return mat.name();
        }
        return s;
    }

    private static String titleCase(String text) {
        StringBuilder out = new StringBuilder();
        boolean cap = true;
        for (char c : text.toCharArray()) {
            if (Character.isWhitespace(c)) {
                cap = true;
                out.append(c);
            } else if (cap) {
                out.append(Character.toUpperCase(c));
                cap = false;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }
}
