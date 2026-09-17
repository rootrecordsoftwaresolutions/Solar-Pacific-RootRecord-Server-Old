package com.rootrecord.minecraft.common;

/** Exposed by RootMC for rootmc-shops price-cap checks. */
public interface RootMcEconomyBridge {

    double averagePrice(String itemKey);

    double maxAllowedPrice(String itemKey, double capPercentOverAvg);
}
