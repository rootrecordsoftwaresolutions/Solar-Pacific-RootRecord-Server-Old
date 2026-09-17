package com.rootrecord.minecraft.rootbanner.order;

import com.rootrecord.minecraft.rootbanner.catalog.BannerGlyph;
import org.bukkit.DyeColor;
import org.bukkit.inventory.ItemStack;

/** One billable banner in a quote. */
public sealed interface BannerLine permits BannerLine.Blank, BannerLine.Glyph {

    String label();

    double price();

    ItemStack createItem(DyeColor defaultBase, DyeColor defaultInk);

    record Blank(DyeColor color, double price) implements BannerLine {
        @Override
        public String label() {
            return "Blank "
                    + com.rootrecord.minecraft.rootbanner.catalog.BannerGlyphCatalog.prettyColor(color);
        }

        @Override
        public ItemStack createItem(DyeColor defaultBase, DyeColor defaultInk) {
            return com.rootrecord.minecraft.rootbanner.catalog.BannerGlyphCatalog.blankBanner(color);
        }
    }

    record Glyph(BannerGlyph glyph, Kind kind, double price) implements BannerLine {
        public enum Kind {
            LETTER,
            NUMBER_OR_SYMBOL
        }

        @Override
        public String label() {
            String kindLabel = kind == Kind.LETTER ? "Letter" : "Symbol";
            if (Character.isDigit(glyph.character())) {
                kindLabel = "Number";
            }
            return kindLabel + " " + glyph.character();
        }

        @Override
        public ItemStack createItem(DyeColor defaultBase, DyeColor defaultInk) {
            return com.rootrecord.minecraft.rootbanner.catalog.BannerGlyphCatalog.glyphBanner(
                    glyph, defaultBase, defaultInk);
        }
    }
}
