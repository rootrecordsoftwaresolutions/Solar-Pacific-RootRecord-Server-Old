package com.rootrecord.minecraft.rootmcshops;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;

/** Stable item keys for shop listings (enchanted books, potions, fireworks, etc.). */
public final class ShopItemKeys {

    private static final String BOOK = "ENCHANTED_BOOK";
    private static final String BOOK_PREFIX = BOOK + "_";
    private static final String FIREWORK_ROCKET = "FIREWORK_ROCKET";
    private static final String FIREWORK_STAR = "FIREWORK_STAR";
    /** Root-Bonds certificate (paper + bond_id PDC). Check Economy + legacy namespaces. */
    public static final String BONDED_NOTE = "BONDED_NOTE";
    private static final NamespacedKey[] BOND_ID_KEYS = {
        NamespacedKey.fromString("root-economy:bond_id"),
        NamespacedKey.fromString("root-bonds:bond_id"),
        NamespacedKey.fromString("root-essentials:bond_id"),
    };
    /** Root-Appreciation sunflower token (PDC mark). Distinct from plain SUNFLOWER. */
    public static final String APPRECIATION_TOKEN = "APPRECIATION_TOKEN";
    private static final NamespacedKey APPRECIATION_TOKEN_KEY =
            NamespacedKey.fromString("root-appreciation:appreciation_token");
    private static final NamespacedKey APPRECIATION_ISSUE_KEY =
            NamespacedKey.fromString("root-appreciation:appreciation_issue_id");
    /** Vote Shard forms (PDC form key). Distinct from plain amethyst. */
    public static final String VOTE_SHARD = "VOTE_SHARD";
    public static final String VOTE_SHARD_BLOCK = "VOTE_SHARD_BLOCK";
    public static final String VOTE_GEODE = "VOTE_GEODE";
    private static final NamespacedKey VOTE_SHARD_FORM_KEY =
            NamespacedKey.fromString("root-appreciation:vote_shard_form");

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
        if (stack.getType() == Material.FIREWORK_ROCKET) {
            return fireworkRocketKey(stack);
        }
        if (stack.getType() == Material.FIREWORK_STAR) {
            return fireworkStarKey(stack);
        }
        if (isBondedNote(stack)) {
            return BONDED_NOTE;
        }
        if (isAppreciationToken(stack)) {
            return APPRECIATION_TOKEN;
        }
        String voteKey = voteShardKey(stack);
        if (voteKey != null) {
            return voteKey;
        }
        return stack.getType().name();
    }

    public static boolean isAppreciationToken(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || APPRECIATION_TOKEN_KEY == null) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(APPRECIATION_TOKEN_KEY, PersistentDataType.BYTE);
    }

    public static String voteShardKey(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || VOTE_SHARD_FORM_KEY == null) {
            return null;
        }
        String form = stack.getItemMeta().getPersistentDataContainer().get(VOTE_SHARD_FORM_KEY, PersistentDataType.STRING);
        if (form == null || form.isBlank()) {
            return null;
        }
        return switch (form.toLowerCase(Locale.ROOT)) {
            case "vote_shard" -> VOTE_SHARD;
            case "vote_shard_block" -> VOTE_SHARD_BLOCK;
            case "vote_geode" -> VOTE_GEODE;
            default -> null;
        };
    }

    public static boolean isVoteShardItem(ItemStack stack) {
        return voteShardKey(stack) != null;
    }

    public static boolean isBondedNote(ItemStack stack) {
        if (stack == null || stack.getType() != Material.PAPER || !stack.hasItemMeta()) {
            return false;
        }
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        for (NamespacedKey key : BOND_ID_KEYS) {
            if (key == null) {
                continue;
            }
            String raw = pdc.get(key, PersistentDataType.STRING);
            if (raw != null && !raw.isBlank()) {
                return true;
            }
        }
        return false;
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

    private static String fireworkRocketKey(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof FireworkMeta firework)) {
            return FIREWORK_ROCKET;
        }
        int power = Math.max(0, firework.getPower());
        String effects = fireworkEffectsHash(firework.getEffects());
        if (effects.isEmpty()) {
            return FIREWORK_ROCKET + "_P" + power;
        }
        return FIREWORK_ROCKET + "_P" + power + "_" + effects;
    }

    private static String fireworkStarKey(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof FireworkEffectMeta star) || !star.hasEffect()) {
            return FIREWORK_STAR;
        }
        FireworkEffect effect = star.getEffect();
        if (effect == null) {
            return FIREWORK_STAR;
        }
        return FIREWORK_STAR + "_" + shortHash(effectCanonical(effect));
    }

    private static String fireworkEffectsHash(List<FireworkEffect> effects) {
        if (effects == null || effects.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (FireworkEffect effect : effects) {
            if (effect == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('+');
            }
            sb.append(effectCanonical(effect));
        }
        return sb.isEmpty() ? "" : shortHash(sb.toString());
    }

    private static String effectCanonical(FireworkEffect effect) {
        StringBuilder sb = new StringBuilder();
        sb.append(effect.getType().name());
        if (effect.hasFlicker()) {
            sb.append("|F");
        }
        if (effect.hasTrail()) {
            sb.append("|T");
        }
        sb.append('|');
        appendColors(sb, effect.getColors());
        sb.append('>');
        appendColors(sb, effect.getFadeColors());
        return sb.toString();
    }

    private static void appendColors(StringBuilder sb, List<Color> colors) {
        if (colors == null || colors.isEmpty()) {
            sb.append('-');
            return;
        }
        for (int i = 0; i < colors.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            Color color = colors.get(i);
            sb.append(color == null ? 0 : color.asRGB());
        }
    }

    private static String shortHash(String raw) {
        CRC32 crc = new CRC32();
        crc.update(raw.getBytes(StandardCharsets.UTF_8));
        return String.format(Locale.ROOT, "%08X", crc.getValue());
    }

    private static boolean isFireworkRocketKey(String itemKey) {
        if (itemKey == null || itemKey.isBlank()) {
            return false;
        }
        String upper = itemKey.toUpperCase(Locale.ROOT);
        return upper.equals(FIREWORK_ROCKET) || upper.startsWith(FIREWORK_ROCKET + "_");
    }

    private static boolean isFireworkStarKey(String itemKey) {
        if (itemKey == null || itemKey.isBlank()) {
            return false;
        }
        String upper = itemKey.toUpperCase(Locale.ROOT);
        return upper.equals(FIREWORK_STAR) || upper.startsWith(FIREWORK_STAR + "_");
    }

    private static Integer fireworkPowerFromKey(String itemKey) {
        if (!isFireworkRocketKey(itemKey)) {
            return null;
        }
        String upper = itemKey.toUpperCase(Locale.ROOT);
        if (upper.equals(FIREWORK_ROCKET)) {
            return null;
        }
        // FIREWORK_ROCKET_P{n} or FIREWORK_ROCKET_P{n}_{hash}
        String rest = upper.substring((FIREWORK_ROCKET + "_").length());
        if (!rest.startsWith("P") || rest.length() < 2) {
            return null;
        }
        int end = 1;
        while (end < rest.length() && Character.isDigit(rest.charAt(end))) {
            end++;
        }
        if (end == 1) {
            return null;
        }
        try {
            return Integer.parseInt(rest.substring(1, end));
        } catch (NumberFormatException ex) {
            return null;
        }
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
        if (APPRECIATION_TOKEN.equalsIgnoreCase(itemKey)) {
            return Material.SUNFLOWER;
        }
        if (VOTE_SHARD.equalsIgnoreCase(itemKey)) {
            return Material.AMETHYST_SHARD;
        }
        if (VOTE_SHARD_BLOCK.equalsIgnoreCase(itemKey)) {
            return Material.AMETHYST_BLOCK;
        }
        if (VOTE_GEODE.equalsIgnoreCase(itemKey)) {
            return Material.BUDDING_AMETHYST;
        }
        if (parseEnchantedBookKey(itemKey) != null || BOOK.equalsIgnoreCase(itemKey)) {
            base = BOOK;
        }
        if (isFireworkRocketKey(itemKey)) {
            return Material.FIREWORK_ROCKET;
        }
        if (isFireworkStarKey(itemKey)) {
            return Material.FIREWORK_STAR;
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
        // Firework variants need chest/bin templates — cannot rebuild from hash alone.
        if (isFireworkRocketKey(itemKey) && FIREWORK_ROCKET.equalsIgnoreCase(itemKey)) {
            return new ItemStack(Material.FIREWORK_ROCKET, 1);
        }
        if (isFireworkStarKey(itemKey) && FIREWORK_STAR.equalsIgnoreCase(itemKey)) {
            return new ItemStack(Material.FIREWORK_STAR, 1);
        }
        if (APPRECIATION_TOKEN.equalsIgnoreCase(itemKey)) {
            return createAppreciationTokenStack();
        }
        if (VOTE_SHARD.equalsIgnoreCase(itemKey)
                || VOTE_SHARD_BLOCK.equalsIgnoreCase(itemKey)
                || VOTE_GEODE.equalsIgnoreCase(itemKey)) {
            return createVoteShardStack(itemKey);
        }
        return null;
    }

    private static ItemStack createVoteShardStack(String itemKey) {
        Material mat = baseMaterial(itemKey);
        if (mat == null) {
            return null;
        }
        ItemStack stack = new ItemStack(mat, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || VOTE_SHARD_FORM_KEY == null) {
            return stack;
        }
        String form = VOTE_SHARD.equalsIgnoreCase(itemKey)
                ? "vote_shard"
                : VOTE_SHARD_BLOCK.equalsIgnoreCase(itemKey) ? "vote_shard_block" : "vote_geode";
        meta.getPersistentDataContainer().set(VOTE_SHARD_FORM_KEY, PersistentDataType.STRING, form);
        NamespacedKey weightKey = NamespacedKey.fromString("root-appreciation:vote_shard_weight");
        if (weightKey != null) {
            meta.getPersistentDataContainer().set(weightKey, PersistentDataType.INTEGER, 1);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack createAppreciationTokenStack() {
        ItemStack stack = new ItemStack(Material.SUNFLOWER, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || APPRECIATION_TOKEN_KEY == null) {
            return stack;
        }
        meta.getPersistentDataContainer().set(APPRECIATION_TOKEN_KEY, PersistentDataType.BYTE, (byte) 1);
        if (APPRECIATION_ISSUE_KEY != null) {
            meta.getPersistentDataContainer().set(APPRECIATION_ISSUE_KEY, PersistentDataType.STRING, "rootmc-appreciation");
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public static boolean matches(ItemStack stack, String itemKey) {
        if (stack == null || stack.getType().isAir() || itemKey == null || itemKey.isBlank()) {
            return false;
        }
        if (BONDED_NOTE.equalsIgnoreCase(itemKey)) {
            return isBondedNote(stack);
        }
        if (APPRECIATION_TOKEN.equalsIgnoreCase(itemKey)) {
            return isAppreciationToken(stack);
        }
        if (VOTE_SHARD.equalsIgnoreCase(itemKey)
                || VOTE_SHARD_BLOCK.equalsIgnoreCase(itemKey)
                || VOTE_GEODE.equalsIgnoreCase(itemKey)) {
            String key = voteShardKey(stack);
            return key != null && key.equalsIgnoreCase(itemKey);
        }
        if (BOOK.equalsIgnoreCase(itemKey)) {
            return stack.getType() == Material.ENCHANTED_BOOK;
        }
        // Legacy generic firework listings still accept any rocket/star.
        if (FIREWORK_ROCKET.equalsIgnoreCase(itemKey)) {
            return stack.getType() == Material.FIREWORK_ROCKET;
        }
        if (FIREWORK_STAR.equalsIgnoreCase(itemKey)) {
            return stack.getType() == Material.FIREWORK_STAR;
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
        if (currentKey.equalsIgnoreCase("SUNFLOWER") || currentKey.equalsIgnoreCase(APPRECIATION_TOKEN)) {
            return resolveAppreciationTokenKey(inv, currentKey);
        }
        if (currentKey.equalsIgnoreCase(FIREWORK_ROCKET)) {
            return resolveFromInventory(inv, Material.FIREWORK_ROCKET, FIREWORK_ROCKET);
        }
        if (currentKey.equalsIgnoreCase(FIREWORK_STAR)) {
            return resolveFromInventory(inv, Material.FIREWORK_STAR, FIREWORK_STAR);
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

    private static String resolveAppreciationTokenKey(Inventory inv, String currentKey) {
        if (inv == null) {
            return currentKey;
        }
        boolean anyToken = false;
        boolean anyPlainSunflower = false;
        for (ItemStack stack : inv.getContents()) {
            if (stack == null || stack.getType() != Material.SUNFLOWER) {
                continue;
            }
            if (isAppreciationToken(stack)) {
                anyToken = true;
            } else {
                anyPlainSunflower = true;
            }
        }
        if (anyToken && !anyPlainSunflower) {
            return APPRECIATION_TOKEN;
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
        if (APPRECIATION_TOKEN.equalsIgnoreCase(itemKey)) {
            return "Appreciation Token";
        }
        ParsedEnchantedBook parsedBook = parseEnchantedBookKey(itemKey);
        if (parsedBook != null) {
            return titleCase(parsedBook.enchant.replace('_', ' ')) + " " + roman(parsedBook.level);
        }
        ParsedPotion parsedPotion = parsePotionKey(itemKey);
        if (parsedPotion != null) {
            return potionPrettyName(parsedPotion);
        }
        if (isFireworkRocketKey(itemKey)) {
            Integer power = fireworkPowerFromKey(itemKey);
            if (power == null) {
                return "Firework Rocket";
            }
            String upper = itemKey.toUpperCase(Locale.ROOT);
            boolean custom = upper.length() > (FIREWORK_ROCKET + "_P" + power).length();
            return custom
                    ? "Firework Rocket Flight " + power + " (custom)"
                    : "Firework Rocket Flight " + power;
        }
        if (isFireworkStarKey(itemKey)) {
            return FIREWORK_STAR.equalsIgnoreCase(itemKey) ? "Firework Star" : "Firework Star (custom)";
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
        if (APPRECIATION_TOKEN.equals(s)
                || "APPRECIATION".equals(s)
                || "THANKS_TOKEN".equals(s)
                || "THANKS".equals(s)
                || "APPTOKEN".equals(s)) {
            return APPRECIATION_TOKEN;
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
