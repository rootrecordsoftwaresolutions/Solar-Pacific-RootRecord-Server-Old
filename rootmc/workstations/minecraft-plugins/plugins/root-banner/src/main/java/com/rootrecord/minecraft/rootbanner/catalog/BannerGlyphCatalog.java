package com.rootrecord.minecraft.rootbanner.catalog;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.rootrecord.minecraft.rootbanner.catalog.BannerLayer.base;
import static com.rootrecord.minecraft.rootbanner.catalog.BannerLayer.ink;
import static org.bukkit.block.banner.PatternType.BORDER;
import static org.bukkit.block.banner.PatternType.CIRCLE;
import static org.bukkit.block.banner.PatternType.CROSS;
import static org.bukkit.block.banner.PatternType.DIAGONAL_RIGHT;
import static org.bukkit.block.banner.PatternType.DIAGONAL_UP_RIGHT;
import static org.bukkit.block.banner.PatternType.HALF_HORIZONTAL;
import static org.bukkit.block.banner.PatternType.HALF_HORIZONTAL_BOTTOM;
import static org.bukkit.block.banner.PatternType.HALF_VERTICAL;
import static org.bukkit.block.banner.PatternType.HALF_VERTICAL_RIGHT;
import static org.bukkit.block.banner.PatternType.RHOMBUS;
import static org.bukkit.block.banner.PatternType.SMALL_STRIPES;
import static org.bukkit.block.banner.PatternType.SQUARE_BOTTOM_LEFT;
import static org.bukkit.block.banner.PatternType.SQUARE_BOTTOM_RIGHT;
import static org.bukkit.block.banner.PatternType.SQUARE_TOP_LEFT;
import static org.bukkit.block.banner.PatternType.SQUARE_TOP_RIGHT;
import static org.bukkit.block.banner.PatternType.STRAIGHT_CROSS;
import static org.bukkit.block.banner.PatternType.STRIPE_BOTTOM;
import static org.bukkit.block.banner.PatternType.STRIPE_CENTER;
import static org.bukkit.block.banner.PatternType.STRIPE_DOWNLEFT;
import static org.bukkit.block.banner.PatternType.STRIPE_DOWNRIGHT;
import static org.bukkit.block.banner.PatternType.STRIPE_LEFT;
import static org.bukkit.block.banner.PatternType.STRIPE_MIDDLE;
import static org.bukkit.block.banner.PatternType.STRIPE_RIGHT;
import static org.bukkit.block.banner.PatternType.STRIPE_TOP;
import static org.bukkit.block.banner.PatternType.TRIANGLES_BOTTOM;
import static org.bukkit.block.banner.PatternType.TRIANGLES_TOP;
import static org.bukkit.block.banner.PatternType.TRIANGLE_BOTTOM;
import static org.bukkit.block.banner.PatternType.TRIANGLE_TOP;

/**
 * Loom pattern recipes for letters, digits, and common symbols.
 * Layouts follow widely published Minecraft alphabet banner crafts.
 */
public final class BannerGlyphCatalog {

    private static final Map<Character, BannerGlyph> GLYPHS = new HashMap<>();
    private static final Map<String, DyeColor> COLOR_ALIASES = new HashMap<>();

    static {
        put('A', g(false, ink(STRIPE_LEFT), ink(STRIPE_RIGHT), ink(STRIPE_TOP), ink(STRIPE_MIDDLE)));
        BannerGlyph b8 = g(false, ink(STRIPE_LEFT), ink(STRIPE_RIGHT), ink(STRIPE_TOP), ink(STRIPE_MIDDLE), ink(STRIPE_BOTTOM));
        put('B', b8);
        put('8', b8);
        put('C', g(false, ink(STRIPE_LEFT), ink(STRIPE_TOP), ink(STRIPE_BOTTOM)));
        put('D', g(false, ink(STRIPE_LEFT), ink(STRIPE_RIGHT), ink(STRIPE_TOP), ink(STRIPE_BOTTOM)));
        put('E', g(false, ink(STRIPE_LEFT), ink(STRIPE_TOP), ink(STRIPE_MIDDLE), ink(STRIPE_BOTTOM)));
        put('F', g(false, ink(STRIPE_LEFT), ink(STRIPE_TOP), ink(STRIPE_MIDDLE)));
        put('G', g(false, ink(STRIPE_LEFT), ink(STRIPE_TOP), ink(STRIPE_BOTTOM), ink(STRIPE_RIGHT), base(HALF_HORIZONTAL)));
        put('H', g(false, ink(STRIPE_LEFT), ink(STRIPE_RIGHT), ink(STRIPE_MIDDLE)));
        put('I', g(false, ink(STRIPE_TOP), ink(STRIPE_BOTTOM), ink(STRIPE_CENTER)));
        put('J', g(false, ink(STRIPE_TOP), ink(STRIPE_RIGHT), ink(STRIPE_BOTTOM), base(HALF_HORIZONTAL)));
        put('K', g(false, ink(STRIPE_LEFT), ink(STRIPE_DOWNLEFT), ink(STRIPE_DOWNRIGHT), ink(STRIPE_MIDDLE)));
        put('L', g(false, ink(STRIPE_LEFT), ink(STRIPE_BOTTOM)));
        put('M', g(false, ink(STRIPE_LEFT), ink(STRIPE_RIGHT), ink(TRIANGLE_TOP), base(TRIANGLES_TOP)));
        put('N', g(false, ink(STRIPE_LEFT), ink(STRIPE_RIGHT), ink(STRIPE_DOWNRIGHT)));
        put('O', g(false, ink(STRIPE_TOP), ink(STRIPE_RIGHT), ink(STRIPE_BOTTOM), ink(STRIPE_LEFT)));
        put('P', g(false, ink(STRIPE_LEFT), ink(STRIPE_RIGHT), ink(STRIPE_TOP), ink(STRIPE_MIDDLE), base(HALF_HORIZONTAL_BOTTOM)));
        put('Q', g(false, ink(STRIPE_DOWNRIGHT), base(HALF_HORIZONTAL), ink(STRIPE_LEFT), ink(STRIPE_BOTTOM), ink(STRIPE_RIGHT), ink(STRIPE_TOP)));
        put('R', g(false, ink(STRIPE_LEFT), ink(STRIPE_RIGHT), ink(STRIPE_TOP), ink(STRIPE_MIDDLE), ink(STRIPE_DOWNRIGHT)));
        put('S', g(false, ink(TRIANGLE_TOP), ink(TRIANGLE_BOTTOM), ink(SQUARE_TOP_RIGHT), ink(SQUARE_BOTTOM_LEFT), base(RHOMBUS), ink(STRIPE_DOWNRIGHT)));
        put('T', g(false, ink(STRIPE_CENTER), ink(STRIPE_TOP)));
        put('U', g(false, ink(STRIPE_BOTTOM), ink(STRIPE_RIGHT), ink(STRIPE_LEFT)));
        put('V', g(false, ink(STRIPE_DOWNLEFT), ink(STRIPE_DOWNRIGHT)));
        put('W', g(false, ink(STRIPE_LEFT), ink(STRIPE_RIGHT), ink(TRIANGLE_BOTTOM), base(TRIANGLES_BOTTOM)));
        put('X', g(false, ink(STRIPE_DOWNLEFT), ink(STRIPE_DOWNRIGHT)));
        put('Y', g(false, ink(STRIPE_DOWNRIGHT), base(HALF_HORIZONTAL_BOTTOM), ink(STRIPE_DOWNLEFT)));
        BannerGlyph z2 = g(false, ink(TRIANGLE_TOP), ink(TRIANGLE_BOTTOM), ink(SQUARE_TOP_LEFT), ink(SQUARE_BOTTOM_RIGHT), base(RHOMBUS), ink(STRIPE_DOWNLEFT));
        put('Z', z2);
        put('2', z2);
        put('0', g(false, ink(STRIPE_TOP), ink(STRIPE_RIGHT), ink(STRIPE_BOTTOM), ink(STRIPE_LEFT), ink(STRIPE_DOWNLEFT)));
        put('1', g(false, ink(SQUARE_TOP_LEFT), base(BORDER), ink(STRIPE_CENTER)));
        put('3', g(false, ink(STRIPE_MIDDLE), base(STRIPE_LEFT), ink(STRIPE_BOTTOM), ink(STRIPE_RIGHT), ink(STRIPE_TOP)));
        put('4', g(true, base(HALF_HORIZONTAL), ink(STRIPE_LEFT), base(STRIPE_BOTTOM), ink(STRIPE_RIGHT), ink(STRIPE_MIDDLE)));
        put('5', g(true, base(HALF_VERTICAL_RIGHT), base(HALF_HORIZONTAL_BOTTOM), ink(STRIPE_BOTTOM), base(DIAGONAL_UP_RIGHT), ink(STRIPE_DOWNRIGHT), ink(STRIPE_TOP)));
        put('6', g(false, ink(STRIPE_RIGHT), base(HALF_HORIZONTAL), ink(STRIPE_BOTTOM), ink(STRIPE_MIDDLE), ink(STRIPE_LEFT), ink(STRIPE_TOP)));
        put('7', g(false, ink(STRIPE_TOP), base(DIAGONAL_RIGHT), ink(STRIPE_DOWNLEFT)));
        put('9', g(false, ink(STRIPE_LEFT), base(HALF_HORIZONTAL_BOTTOM), ink(STRIPE_MIDDLE), ink(STRIPE_TOP), ink(STRIPE_RIGHT)));
        put('?', g(false, ink(STRIPE_RIGHT), base(HALF_HORIZONTAL_BOTTOM), ink(STRIPE_TOP), ink(STRIPE_MIDDLE), ink(SQUARE_BOTTOM_LEFT)));
        put('!', g(false, ink(HALF_HORIZONTAL), ink(STRIPE_MIDDLE), ink(SQUARE_BOTTOM_LEFT), base(HALF_VERTICAL_RIGHT)));
        put('.', g(false, ink(SQUARE_BOTTOM_LEFT)));
        put('-', g(false, ink(STRIPE_MIDDLE)));
        put('_', g(false, ink(STRIPE_BOTTOM)));
        put('/', g(false, ink(STRIPE_DOWNLEFT)));
        put('\\', g(false, ink(STRIPE_DOWNRIGHT)));
        put('+', g(false, ink(STRAIGHT_CROSS)));
        put('=', g(false, ink(STRIPE_TOP), ink(STRIPE_BOTTOM), base(BORDER)));
        put(':', g(false, ink(SQUARE_TOP_LEFT), ink(SQUARE_BOTTOM_LEFT), base(HALF_VERTICAL_RIGHT)));
        put(';', g(false, ink(SQUARE_TOP_LEFT), ink(SQUARE_BOTTOM_LEFT), base(HALF_VERTICAL_RIGHT), ink(STRIPE_DOWNLEFT)));
        put(',', g(false, ink(SQUARE_BOTTOM_LEFT), base(HALF_VERTICAL_RIGHT)));
        put('\'', g(false, ink(SQUARE_TOP_LEFT)));
        put('"', g(false, ink(SQUARE_TOP_LEFT), ink(SQUARE_TOP_RIGHT)));
        put('*', g(false, ink(CROSS), ink(STRAIGHT_CROSS)));
        put('#', g(false, ink(SMALL_STRIPES), ink(STRIPE_MIDDLE), ink(STRIPE_CENTER)));
        put('@', g(false, ink(CIRCLE), ink(STRIPE_MIDDLE), base(BORDER)));
        put('&', g(false, ink(CROSS), ink(STRIPE_LEFT), ink(STRIPE_BOTTOM), ink(STRIPE_MIDDLE)));
        put('%', g(false, ink(STRIPE_DOWNLEFT), ink(CIRCLE), ink(SQUARE_TOP_RIGHT), ink(SQUARE_BOTTOM_LEFT)));
        put('(', g(false, ink(STRIPE_LEFT), ink(STRIPE_TOP), ink(STRIPE_BOTTOM), base(STRIPE_RIGHT)));
        put(')', g(false, ink(STRIPE_RIGHT), ink(STRIPE_TOP), ink(STRIPE_BOTTOM), base(STRIPE_LEFT)));

        for (DyeColor color : DyeColor.values()) {
            COLOR_ALIASES.put(normalizeColorKey(color.name()), color);
        }
        COLOR_ALIASES.put("grey", DyeColor.GRAY);
        COLOR_ALIASES.put("lightgrey", DyeColor.LIGHT_GRAY);
        COLOR_ALIASES.put("light_grey", DyeColor.LIGHT_GRAY);
        COLOR_ALIASES.put("darkgrey", DyeColor.GRAY);
        COLOR_ALIASES.put("dark_grey", DyeColor.GRAY);
        COLOR_ALIASES.put("darkgray", DyeColor.GRAY);
        COLOR_ALIASES.put("silver", DyeColor.LIGHT_GRAY);
        COLOR_ALIASES.put("aqua", DyeColor.CYAN);
        COLOR_ALIASES.put("lightblue", DyeColor.LIGHT_BLUE);
    }

    private BannerGlyphCatalog() {}

    public static Optional<BannerGlyph> glyph(char ch) {
        return Optional.ofNullable(GLYPHS.get(Character.toUpperCase(ch)));
    }

    public static boolean supports(char ch) {
        return GLYPHS.containsKey(Character.toUpperCase(ch));
    }

    public static Optional<DyeColor> colorNamed(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(COLOR_ALIASES.get(normalizeColorKey(token)));
    }

    public static Set<String> colorNames() {
        return Collections.unmodifiableSet(COLOR_ALIASES.keySet());
    }

    public static ItemStack blankBanner(DyeColor color) {
        ItemStack stack = new ItemStack(bannerMaterial(color));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(prettyColor(color) + " Banner");
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static ItemStack glyphBanner(BannerGlyph glyph, DyeColor base, DyeColor ink) {
        boolean invert = glyph.invertField();
        ItemStack stack = new ItemStack(bannerMaterial(invert ? ink : base));
        BannerMeta meta = (BannerMeta) stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        ArrayList<Pattern> patterns = new ArrayList<>(glyph.layers().size());
        for (BannerLayer layer : glyph.layers()) {
            DyeColor dye = layer.useInk() ? ink : base;
            patterns.add(new Pattern(dye, layer.type()));
        }
        meta.setPatterns(patterns);
        meta.setDisplayName("Banner (" + glyph.character() + ")");
        stack.setItemMeta(meta);
        return stack;
    }

    public static Material bannerMaterial(DyeColor color) {
        return Material.valueOf(color.name() + "_BANNER");
    }

    public static String prettyColor(DyeColor color) {
        String raw = color.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder sb = new StringBuilder(raw.length());
        boolean cap = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == ' ') {
                sb.append(c);
                cap = true;
                continue;
            }
            sb.append(cap ? Character.toUpperCase(c) : c);
            cap = false;
        }
        return sb.toString();
    }

    private static String normalizeColorKey(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static BannerGlyph g(boolean invert, BannerLayer... layers) {
        return new BannerGlyph('?', invert, layers);
    }

    private static void put(char ch, BannerGlyph template) {
        GLYPHS.put(Character.toUpperCase(ch), new BannerGlyph(Character.toUpperCase(ch), template.invertField(), template.layers()));
    }
}
