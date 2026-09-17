package com.rootrecord.rootmc.data.repository

data class StockMarketItemRow(
    val itemKey: String,
    val totalQuantity: Int,
    val shopCount: Int,
    val minPrice: Double,
    val maxPrice: Double,
    val marketAvg: Double,
    val marketSamples: Int,
    val change24hPct: Double?,
    val buyCapacity: Int = 0,
    val buyShopCount: Int = 0,
    val maxBuyPrice: Double = 0.0,
) {
    fun displayPrice(): Double = when {
        marketAvg > 0 -> marketAvg
        minPrice > 0 -> minPrice
        else -> 0.0
    }
}

data class StockMarketItemsPage(
    val items: List<StockMarketItemRow>,
    val total: Int,
    val page: Int,
    val totalPages: Int,
)

data class StockPricePoint(
    val avgPrice: Double,
    val sampleCount: Int,
    val recordedAt: String,
)

data class StockMarketSummary(
    val totalServerItems: Int,
    val syncedAt: String?,
)

data class ChartPoint(
    val label: String,
    val value: Double,
)

data class FlowDayRow(
    val day: String,
    val inflow: Double,
    val outflow: Double,
)

data class TreasuryReserveSnapshot(
    val balance: Double,
    val syncedAt: String?,
    val viewMonth: String?,
    val monthInflow: Double,
    val monthOutflow: Double,
    val monthNet: Double,
    val playerNotesG: Double?,
    val goldMinedG: Double?,
    val totalNotesG: Double?,
    val backingPct: Double?,
    val overIssueG: Double?,
    val noteSupplySummary: String?,
    val goldFoundSinceJuly: Double?,
    val marketStacks: Int = 0,
    val balanceHistory: List<ChartPoint>,
    val holderSupplyDaily: List<ChartPoint>,
    val flowDaily: List<FlowDayRow>,
)

data class EconomyOverview(
    val reserve: TreasuryReserveSnapshot,
    val marketSummary: StockMarketSummary,
    val marketMovers: List<StockMarketItemRow>,
    val netWorthTop: List<NetWorthLeaderboardEntry>,
    val mintTop: List<GoldMintLeaderboardEntry>,
    val goldFoundTop: List<GoldFoundLeaderboardEntry>,
)

data class CirculatingBalanceRow(
    val rank: Int,
    val displayName: String,
    val minecraftUsername: String?,
    val notesG: Double,
)

data class CirculatingTownRow(
    val rank: Int,
    val displayName: String,
    val nationName: String?,
    val mayorName: String?,
    val notesG: Double,
    val bondedG: Double,
    val bondEarningsG: Double,
)

data class CirculatingNationRow(
    val rank: Int,
    val displayName: String,
    val leaderName: String?,
    val townCount: Int,
    val notesG: Double,
    val bondedG: Double,
    val bondEarningsG: Double,
)

data class CirculatingBalancesTotals(
    val playerNotesG: Double,
    val townNotesG: Double,
    val nationNotesG: Double,
    val circulatingNotesG: Double,
    val playerCount: Int,
    val townCount: Int,
    val nationCount: Int,
)

data class CirculatingBalancesReport(
    val serverId: String,
    val syncedAt: String?,
    val totals: CirculatingBalancesTotals,
    val players: List<CirculatingBalanceRow>,
    val towns: List<CirculatingTownRow>,
    val nations: List<CirculatingNationRow>,
)
