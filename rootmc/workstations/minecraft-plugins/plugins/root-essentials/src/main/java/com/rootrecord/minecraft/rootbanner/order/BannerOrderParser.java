package com.rootrecord.minecraft.rootbanner.order;

import com.rootrecord.minecraft.rootbanner.catalog.BannerGlyph;
import com.rootrecord.minecraft.rootbanner.catalog.BannerGlyphCatalog;
import org.bukkit.DyeColor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Parses /banner text into billable lines (color words → blank; other chars → glyphs). */
public final class BannerOrderParser {

    public sealed interface Result permits Result.Ok, Result.Unsupported, Result.Empty {
        record Ok(List<BannerLine> lines) implements Result {}

        record Unsupported(Set<Character> chars) implements Result {}

        record Empty() implements Result {}
    }

    private final double blankPrice;
    private final double letterPrice;
    private final double symbolPrice;

    public BannerOrderParser(double blankPrice, double letterPrice, double symbolPrice) {
        this.blankPrice = blankPrice;
        this.letterPrice = letterPrice;
        this.symbolPrice = symbolPrice;
    }

    public Result parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return new Result.Empty();
        }
        String[] tokens = rawText.trim().split("\\s+");
        List<BannerLine> lines = new ArrayList<>();
        Set<Character> bad = new LinkedHashSet<>();

        for (String token : tokens) {
            Optional<DyeColor> color = BannerGlyphCatalog.colorNamed(token);
            if (color.isPresent()) {
                lines.add(new BannerLine.Blank(color.get(), blankPrice));
                continue;
            }
            for (int i = 0; i < token.length(); i++) {
                char ch = token.charAt(i);
                if (Character.isWhitespace(ch)) {
                    continue;
                }
                Optional<BannerGlyph> glyph = BannerGlyphCatalog.glyph(ch);
                if (glyph.isEmpty()) {
                    bad.add(ch);
                    continue;
                }
                BannerGlyph g = glyph.get();
                boolean letter = Character.isLetter(g.character());
                double price = letter ? letterPrice : symbolPrice;
                BannerLine.Glyph.Kind kind = letter
                        ? BannerLine.Glyph.Kind.LETTER
                        : BannerLine.Glyph.Kind.NUMBER_OR_SYMBOL;
                lines.add(new BannerLine.Glyph(g, kind, price));
            }
        }

        if (!bad.isEmpty()) {
            return new Result.Unsupported(bad);
        }
        if (lines.isEmpty()) {
            return new Result.Empty();
        }
        return new Result.Ok(List.copyOf(lines));
    }

    public static String formatUnsupported(Set<Character> chars) {
        StringBuilder sb = new StringBuilder();
        for (Character ch : chars) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    public static String money(double amount) {
        if (Math.rint(amount) == amount) {
            return String.valueOf((long) amount);
        }
        return String.format(Locale.US, "%.2f", amount);
    }
}
