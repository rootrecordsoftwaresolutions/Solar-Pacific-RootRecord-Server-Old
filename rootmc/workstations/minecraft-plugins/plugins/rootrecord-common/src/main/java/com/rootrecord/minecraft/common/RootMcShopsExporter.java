package com.rootrecord.minecraft.common;

import java.util.List;

/** Implemented by rootmc-shops; consumed by RootMC ShopListingService. */
public interface RootMcShopsExporter {

    String providerId();

    List<RootMcShopListingDto> collectListings();

    /** Collect one listing for incremental cloud sync; null when the shop no longer exists. */
    RootMcShopListingDto collectListing(String shopId);

    /** Live median sell price from in-stock shops; 0 when none. */
    default double medianInStockSellPrice(String itemKey) {
        return 0;
    }
}
