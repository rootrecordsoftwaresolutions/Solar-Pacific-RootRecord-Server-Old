package com.rootrecord.minecraft.rootbanner.order;

import java.util.List;

public record BannerQuote(List<BannerLine> lines, double total, long expiresAtMs) {

    public int count() {
        return lines.size();
    }

    public boolean expired(long nowMs) {
        return nowMs > expiresAtMs;
    }
}
