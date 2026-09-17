package com.rootrecord.minecraft.rootbanner.catalog;

import java.util.List;

/** Letter / number / symbol banner definition. */
public record BannerGlyph(char character, boolean invertField, List<BannerLayer> layers) {

    public BannerGlyph(char character, boolean invertField, BannerLayer... layers) {
        this(character, invertField, List.of(layers));
    }
}
