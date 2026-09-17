package com.rootrecord.minecraft.rootbanner.catalog;

import org.bukkit.block.banner.PatternType;

/** One loom layer — ink dye or base field color. */
public record BannerLayer(boolean useInk, PatternType type) {

    public static BannerLayer ink(PatternType type) {
        return new BannerLayer(true, type);
    }

    public static BannerLayer base(PatternType type) {
        return new BannerLayer(false, type);
    }
}
