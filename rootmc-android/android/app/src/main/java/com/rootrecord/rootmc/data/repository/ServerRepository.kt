package com.rootrecord.rootmc.data.repository

import com.rootrecord.rootmc.BuildConfig
import com.rootrecord.rootmc.data.repository.BLOCKNOTES_APP_ID
import com.rootrecord.rootmc.data.remote.AppJson
import com.rootrecord.rootmc.di.IoDispatcher
import com.rootrecord.rootmc.di.ROOTRECORD_BLOCKNOTES_BASE
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

data class McMMOStats(
    val powerLevel: Int,
    val skills: Map<String, Int>,
    val syncedAt: String?,
    val minecraftUsername: String?,
)

data class PlaytimeStats(
    val totalSeconds: Long,
    val firstJoinAt: String?,
    val lastLoginAt: String?,
)

data class NetWorthStats(
    val balanceValue: Double,
    val inventoryValue: Double,
    val chestValue: Double,
    val shopStockValue: Double,
    val totalValue: Double,
    val syncedAt: String?,
    val rank: Int? = null,
)

data class NetWorthLeaderboardEntry(
    val rank: Int,
    val minecraftUsername: String?,
    val totalValue: Double,
    val balanceValue: Double,
    val inventoryValue: Double,
)

data class PlaytimeLeaderboardEntry(
    val rank: Int,
    val minecraftUsername: String?,
    val totalSeconds: Long,
)

data class McmmoLeaderboardEntry(
    val rank: Int,
    val minecraftUsername: String?,
    val powerLevel: Int,
)

data class GoldMintLeaderboardEntry(
    val rank: Int,
    val minecraftUsername: String?,
    val grossMintedG: Double,
    val mintEvents: Int,
)

data class GoldFoundLeaderboardEntry(
    val rank: Int,
    val minecraftUsername: String?,
    val minedSinceJulyG: Double,
    val physicalMinedSinceJulyG: Double,
)

data class ServerItemTotal(
    val itemKey: String,
    val totalQuantity: Int,
    val avgPrice: Double = 0.0,
)

data class ShopPriceRow(
    val itemKey: String,
    val avgPrice: Double,
    val sampleCount: Int,
)

data class ShopListingRow(
    val shopId: String,
    val ownerUsername: String?,
    val itemKey: String,
    val price: Double,
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val listingType: String,
)

data class RootShopsSnapshot(
    val shareUrl: String?,
    val prices: List<ShopPriceRow>,
    val listings: List<ShopListingRow>,
)

data class IngameEventRow(
    val id: Long,
    val eventType: String,
    val worldName: String,
    val dimension: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val label: String?,
    val body: String?,
    val createdAt: String?,
)

data class StockMarketRow(
    val itemKey: String,
    val avgPrice: Double,
    val sampleCount: Int,
    val recordedAt: String?,
)

data class VaultOrderRow(
    val id: Long,
    val itemKey: String,
    val quantity: Int,
    val pricePaid: Double,
    val status: String,
    val createdAt: String?,
)

data class BuyOrderResult(
    val orderId: Long?,
    val itemKey: String,
    val quantity: Int,
    val pricePaid: Double,
    val message: String?,
)

data class ShopPriceAlertRow(
    val id: String,
    val itemKey: String,
    val alertType: String,
    val thresholdValue: Double,
    val enabled: Boolean,
    val lastSeenPrice: Double?,
    val lastNotifiedAt: String?,
)

data class MayorTownSnapshot(
    val isMayor: Boolean,
    val minecraftLinked: Boolean = true,
    val townName: String? = null,
    val residentCount: Int = 0,
    val nationName: String? = null,
    val isCapital: Boolean = false,
    val discordInviteUrl: String? = null,
    val shopListingCount: Int = 0,
    val shopListings: List<ShopListingRow> = emptyList(),
    val syncedAt: String? = null,
)

data class FeaturedServerConfig(
    val serverId: String,
    val name: String,
    val address: String,
    val defaultWorldName: String,
    val gameVersion: String,
    val mapUrl: String?,
    val verifyUrl: String,
    val realmUrl: String,
    val featured: Boolean = false,
    val connected: Boolean = true,
    val rootmcPluginInstalled: Boolean,
    val rootmcPluginVersion: String?,
    val rootmcSyncActive: Boolean = false,
    val mcmmo: McMMOStats? = null,
    val playtime: PlaytimeStats? = null,
    val netWorth: NetWorthStats? = null,
    val belongsToServer: Boolean = false,
    val autoAddWorld: Boolean = false,
)

data class DailyReportCategory(
    val category: String,
    val title: String,
    val postedAt: String?,
    val summary: String,
    val reportText: String,
    val unchangedFromPrior: Boolean,
)

data class DailyReportDay(
    val dayKey: String,
    val postedAt: String?,
    val summary: String,
    val reportText: String,
    val unchangedFromPrior: Boolean,
    val categories: List<DailyReportCategory>,
)

data class DailyReportPage(
    val serverId: String,
    val reports: List<DailyReportDay>,
)

data class ServerMembership(
    val accountId: String,
    val minecraftLinked: Boolean,
    val minecraftUsername: String?,
    val minecraftUuid: String?,
    val verifiedAt: String?,
    val servers: List<FeaturedServerConfig>,
    val featuredServer: FeaturedServerConfig?,
)

@Singleton
class ServerRepository @Inject constructor(
    @param:Named("rootrecord") private val http: OkHttpClient,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun fetchFeaturedServers(): Result<List<FeaturedServerConfig>> = withContext(io) {
        runCatching {
            val text = get("${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/server/featured")
            val servers = AppJson.parseToJsonElement(text).jsonObject["servers"]?.jsonArray
                ?: throw IllegalStateException("missing_servers")
            servers.map { parseFeaturedServer(it.jsonObject) }
        }.recoverCatching {
            listOf(fallbackFeaturedServer())
        }
    }

    suspend fun fetchFeaturedServerConfig(): Result<FeaturedServerConfig> = withContext(io) {
        runCatching {
            val text = get("${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/server/config")
            val server = AppJson.parseToJsonElement(text).jsonObject["featured_server"]?.jsonObject
                ?: throw IllegalStateException("missing_featured_server")
            parseFeaturedServer(server)
        }.recoverCatching {
            fallbackFeaturedServer()
        }
    }

    suspend fun fetchMembership(): Result<ServerMembership> = withContext(io) {
        runCatching {
            val text = get("${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/server/membership")
            parseMembership(AppJson.parseToJsonElement(text).jsonObject)
        }
    }

    suspend fun fetchNetWorthLeaderboard(
        serverId: String,
        limit: Int = 10,
    ): Result<List<NetWorthLeaderboardEntry>> = withContext(io) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(serverId, Charsets.UTF_8.name())
            val text = get(
                "${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/server/$encoded/economy/net-worth?limit=$limit",
            )
            val root = AppJson.parseToJsonElement(text).jsonObject
            root["leaderboard"]?.jsonArray?.map { row ->
                val o = row.jsonObject
                NetWorthLeaderboardEntry(
                    rank = o["rank"]?.jsonPrimitive?.intOrNull ?: 0,
                    minecraftUsername = o.stringOrNull("minecraft_username"),
                    totalValue = o["total_value"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    balanceValue = o["balance_value"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    inventoryValue = o["inventory_value"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                )
            }.orEmpty()
        }
    }

    suspend fun fetchMyNetWorthOnServer(serverId: String): Result<NetWorthStats?> = withContext(io) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(serverId, Charsets.UTF_8.name())
            val text = get("${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/server/$encoded/economy/me")
            val root = AppJson.parseToJsonElement(text).jsonObject
            val netWorth = parseNetWorth(root["net_worth"]?.jsonObject)
            netWorth?.copy(rank = root["rank"]?.jsonPrimitive?.intOrNull)
        }
    }

    suspend fun fetchServerItemTotals(
        serverId: String,
        limit: Int = 8,
    ): Result<List<ServerItemTotal>> = withContext(io) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(serverId, Charsets.UTF_8.name())
            val text = get(
                "${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/server/$encoded/economy/totals?limit=$limit",
            )
            val root = AppJson.parseToJsonElement(text).jsonObject
            root["items"]?.jsonArray?.map { row ->
                val o = row.jsonObject
                ServerItemTotal(
                    itemKey = o.string("item_key"),
                    totalQuantity = o["total_quantity"]?.jsonPrimitive?.intOrNull ?: 0,
                    avgPrice = o["avg_price"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                )
            }.orEmpty()
        }
    }

    suspend fun fetchRootShops(serverId: String, limit: Int = 40): Result<RootShopsSnapshot> = withContext(io) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(serverId, Charsets.UTF_8.name())
            val text = get(
                "${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/server/$encoded/shops?limit=$limit",
            )
            val root = AppJson.parseToJsonElement(text).jsonObject
            val prices = root["prices"]?.jsonArray?.map { row ->
                val o = row.jsonObject
                ShopPriceRow(
                    itemKey = o.string("item_key"),
                    avgPrice = o["avg_price"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    sampleCount = o["sample_count"]?.jsonPrimitive?.intOrNull ?: 0,
                )
            }.orEmpty()
            val listings = root["listings"]?.jsonArray?.map { row ->
                val o = row.jsonObject
                ShopListingRow(
                    shopId = o.string("shop_id"),
                    ownerUsername = o.stringOrNull("owner_username"),
                    itemKey = o.string("item_key"),
                    price = o["price"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    world = o.string("world"),
                    x = o["x"]?.jsonPrimitive?.intOrNull ?: 0,
                    y = o["y"]?.jsonPrimitive?.intOrNull ?: 0,
                    z = o["z"]?.jsonPrimitive?.intOrNull ?: 0,
                    listingType = o.stringOrNull("listing_type") ?: "sell",
                )
            }.orEmpty()
            RootShopsSnapshot(
                shareUrl = root.stringOrNull("share_url"),
                prices = prices,
                listings = listings,
            )
        }
    }

    suspend fun fetchMyMcmmoOnServer(serverId: String): Result<McMMOStats?> = withContext(io) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(serverId, Charsets.UTF_8.name())
            val text = get("${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/server/$encoded/mcmmo/me")
            val root = AppJson.parseToJsonElement(text).jsonObject
            parseMcmmo(root["mcmmo"]?.jsonObject)
        }
    }

    suspend fun fetchMyPlaytimeOnServer(serverId: String): Result<PlaytimeStats?> = withContext(io) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(serverId, Charsets.UTF_8.name())
            val text = get("${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/server/$encoded/playtime/me")
            val root = AppJson.parseToJsonElement(text).jsonObject
            parsePlaytime(root["playtime"]?.jsonObject)
        }
    }

    suspend fun fetchPlaytimeLeaderboard(
        serverId: String,
        limit: Int = 10,
    ): Result<List<PlaytimeLeaderboardEntry>> = withContext(io) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(serverId, Charsets.UTF_8.name())
            val text = get(
                "${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/server/$encoded/playtime/leaderboard?limit=$limit",
            )
            val root = AppJson.parseToJsonElement(text).jsonObject
            root["leaderboard"]?.jsonArray?.map { row ->
                val o = row.jsonObject
                PlaytimeLeaderboardEntry(
                    rank = o["rank"]?.jsonPrimitive?.intOrNull ?: 0,
                    minecraftUsername = o.stringOrNull("minecraft_username"),
                    totalSeconds = o["total_playtime_seconds"]?.jsonPrimitive?.longOrNull ?: 0L,
                )
            }.orEmpty()
        }
    }

    suspend fun fetchMcmmoLeaderboard(
        serverId: String,
        limit: Int = 25,
    ): Result<List<McmmoLeaderboardEntry>> = withContext(io) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(serverId, Charsets.UTF_8.name())
            val text = get(
                "${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/server/$encoded/mcmmo/leaderboard?limit=$limit",
            )
            val root = AppJson.parseToJsonElement(text).jsonObject
            root["leaderboard"]?.jsonArray?.map { row ->
                val o = row.jsonObject
                McmmoLeaderboardEntry(
                    rank = o["rank"]?.jsonPrimitive?.intOrNull ?: 0,
                    minecraftUsername = o.stringOrNull("minecraft_username"),
                    powerLevel = o["power_level"]?.jsonPrimitive?.intOrNull ?: 0,
                )
            }.orEmpty()
        }
    }

    suspend fun fetchGoldMintLeaderboard(
        treasuryId: String = "rootmc",
        limit: Int = 25,
    ): Result<List<GoldMintLeaderboardEntry>> = withContext(io) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(treasuryId, Charsets.UTF_8.name())
            val text = get(
                "${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/treasury/$encoded/mint/leaderboard?limit=$limit",
            )
            val root = AppJson.parseToJsonElement(text).jsonObject
            root["leaderboard"]?.jsonArray?.map { row ->
                val o = row.jsonObject
                GoldMintLeaderboardEntry(
                    rank = o["rank"]?.jsonPrimitive?.intOrNull ?: 0,
                    minecraftUsername = o.stringOrNull("minecraft_username"),
                    grossMintedG = o["gross_minted_g"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    mintEvents = o["mint_events"]?.jsonPrimitive?.intOrNull ?: 0,
                )
            }.orEmpty()
        }
    }

    suspend fun fetchGoldFoundLeaderboard(
        serverId: String,
        limit: Int = 25,
    ): Result<List<GoldFoundLeaderboardEntry>> = withContext(io) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(serverId, Charsets.UTF_8.name())
            val text = get(
                "${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/server/$encoded/gold-found/leaderboard" +
                    "?limit=$limit&order=since_july",
            )
            val root = AppJson.parseToJsonElement(text).jsonObject
            root["leaderboard"]?.jsonArray?.map { row ->
                val o = row.jsonObject
                val ore = o["mined_ore_since_july_g"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val block = o["mined_block_since_july_g"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                GoldFoundLeaderboardEntry(
                    rank = o["rank"]?.jsonPrimitive?.intOrNull ?: 0,
                    minecraftUsername = o.stringOrNull("minecraft_username"),
                    minedSinceJulyG = o["mined_since_july_g"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    physicalMinedSinceJulyG = ore + block,
                )
            }.orEmpty()
        }
    }

    suspend fun fetchMobileFeaturedServer(): Result<FeaturedServerConfig> = withContext(io) {
        runCatching {
            val url = "${ROOTRECORD_BLOCKNOTES_BASE}api/mobile/config".toHttpUrl()
            val text = get(url.toString())
            val root = AppJson.parseToJsonElement(text).jsonObject
            val server = root["featured_server"]?.jsonObject
            if (server != null) {
                parseFeaturedServer(server)
            } else {
                fetchFeaturedServerConfig().getOrThrow()
            }
        }.recoverCatching {
            fallbackFeaturedServer()
        }
    }

    suspend fun fetchIngameEvents(limit: Int = 50): Result<List<IngameEventRow>> = withContext(io) {
        runCatching {
            val text = get("${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/ingame-events?limit=$limit")
            val root = AppJson.parseToJsonElement(text).jsonObject
            root["events"]?.jsonArray?.map { row ->
                val o = row.jsonObject
                IngameEventRow(
                    id = o["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                    eventType = o.string("event_type"),
                    worldName = o.string("world_name"),
                    dimension = o.string("dimension").ifBlank { "overworld" },
                    x = o["x"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    y = o["y"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    z = o["z"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    label = o.stringOrNull("label"),
                    body = o.stringOrNull("body"),
                    createdAt = o.stringOrNull("created_at"),
                )
            }.orEmpty()
        }
    }

    suspend fun ackIngameEvents(eventIds: List<Long>): Result<Unit> = withContext(io) {
        runCatching {
            val ids = eventIds.joinToString(",")
            post("${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/ingame-events/ack", """{"event_ids":[$ids]}""")
            Unit
        }
    }

    suspend fun fetchStockMarket(serverId: String, limit: Int = 50): Result<List<StockMarketRow>> = withContext(io) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(serverId, Charsets.UTF_8.name())
            val text = get("${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/stock-market?server_id=$encoded&limit=$limit")
            val root = AppJson.parseToJsonElement(text).jsonObject
            root["catalog"]?.jsonArray?.map { row ->
                val o = row.jsonObject
                StockMarketRow(
                    itemKey = o.string("item_key"),
                    avgPrice = o["avg_price"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    sampleCount = o["sample_count"]?.jsonPrimitive?.intOrNull ?: 0,
                    recordedAt = o.stringOrNull("recorded_at"),
                )
            }.orEmpty()
        }
    }

    suspend fun fetchStockMarketSummary(serverId: String): Result<StockMarketSummary> = withContext(io) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(serverId, Charsets.UTF_8.name())
            val text = get("${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/stock-market?server_id=$encoded&limit=1")
            val root = AppJson.parseToJsonElement(text).jsonObject
            StockMarketSummary(
                totalServerItems = root["total_server_items"]?.jsonPrimitive?.intOrNull ?: 0,
                syncedAt = root.stringOrNull("synced_at"),
            )
        }
    }

    suspend fun fetchStockMarketItems(
        serverId: String,
        page: Int = 1,
        perPage: Int = 40,
        sort: String = "quantity_desc",
        query: String = "",
        inStockOnly: Boolean = false,
    ): Result<StockMarketItemsPage> = withContext(io) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(serverId, Charsets.UTF_8.name())
            val url = "${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/stock-market/items".toHttpUrl()
                .newBuilder()
                .addQueryParameter("server_id", serverId)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("per_page", perPage.toString())
                .addQueryParameter("sort", sort)
                .apply {
                    if (query.isNotBlank()) addQueryParameter("q", query)
                    if (inStockOnly) addQueryParameter("in_stock", "1")
                }
                .build()
            val text = get(url.toString())
            parseStockMarketItemsPage(AppJson.parseToJsonElement(text).jsonObject)
        }
    }

    suspend fun fetchStockMarketHistory(
        serverId: String,
        itemKey: String,
        limit: Int = 500,
    ): Result<List<StockPricePoint>> = withContext(io) {
        runCatching {
            val sid = java.net.URLEncoder.encode(serverId, Charsets.UTF_8.name())
            val item = java.net.URLEncoder.encode(itemKey.uppercase(), Charsets.UTF_8.name())
            val text = get(
                "${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/stock-market/history" +
                    "?server_id=$sid&item=$item&limit=$limit",
            )
            val root = AppJson.parseToJsonElement(text).jsonObject
            root["points"]?.jsonArray?.map { row ->
                val o = row.jsonObject
                StockPricePoint(
                    avgPrice = o["avg_price"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    sampleCount = o["sample_count"]?.jsonPrimitive?.intOrNull ?: 0,
                    recordedAt = o.string("recorded_at"),
                )
            }.orEmpty()
        }
    }

    suspend fun fetchTreasuryReserve(treasuryId: String = "rootmc"): Result<TreasuryReserveSnapshot> = withContext(io) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(treasuryId, Charsets.UTF_8.name())
            val text = get("${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/treasury/$encoded/reserve")
            parseTreasuryReserve(AppJson.parseToJsonElement(text).jsonObject)
        }
    }

    suspend fun fetchCirculatingBalances(
        serverId: String,
        limit: Int = 500,
    ): Result<CirculatingBalancesReport> = withContext(io) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(serverId, Charsets.UTF_8.name())
            val text = get(
                "${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/server/$encoded/economy/circulating-balances?limit=$limit",
            )
            parseCirculatingBalances(AppJson.parseToJsonElement(text).jsonObject)
        }
    }

    suspend fun fetchEconomyOverview(serverId: String): Result<EconomyOverview> = withContext(io) {
        runCatching {
            val reserve = fetchTreasuryReserve().getOrThrow()
            val marketSummary = fetchStockMarketSummary(serverId).getOrElse { StockMarketSummary(0, null) }
            val marketItems = fetchStockMarketItems(serverId, perPage = 40, inStockOnly = true).getOrElse {
                StockMarketItemsPage(emptyList(), 0, 1, 1)
            }
            val movers = marketItems.items
                .filter { it.change24hPct != null && it.displayPrice() > 0 }
                .sortedByDescending { kotlin.math.abs(it.change24hPct ?: 0.0) }
                .take(8)
            val netWorth = fetchNetWorthLeaderboard(serverId, 8).getOrElse { emptyList() }
            val mint = fetchGoldMintLeaderboard(limit = 8).getOrElse { emptyList() }
            val goldFound = fetchGoldFoundLeaderboard(serverId, 8).getOrElse { emptyList() }
            EconomyOverview(
                reserve = reserve.copy(marketStacks = marketSummary.totalServerItems),
                marketSummary = marketSummary,
                marketMovers = movers,
                netWorthTop = netWorth,
                mintTop = mint,
                goldFoundTop = goldFound,
            )
        }
    }

    private fun parseStockMarketItemsPage(root: JsonObject): StockMarketItemsPage {
        val items = root["items"]?.jsonArray?.map { row ->
            val o = row.jsonObject
            StockMarketItemRow(
                itemKey = o.string("item_key"),
                totalQuantity = o["total_quantity"]?.jsonPrimitive?.intOrNull ?: 0,
                shopCount = o["shop_count"]?.jsonPrimitive?.intOrNull ?: 0,
                minPrice = o["min_price"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                maxPrice = o["max_price"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                marketAvg = o["market_avg"]?.jsonPrimitive?.doubleOrNull
                    ?: o["avg_price"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                marketSamples = o["market_samples"]?.jsonPrimitive?.intOrNull
                    ?: o["sample_count"]?.jsonPrimitive?.intOrNull ?: 0,
                change24hPct = o["change_24h_pct"]?.jsonPrimitive?.doubleOrNull,
                buyCapacity = o["buy_capacity"]?.jsonPrimitive?.intOrNull ?: 0,
                buyShopCount = o["buy_shop_count"]?.jsonPrimitive?.intOrNull ?: 0,
                maxBuyPrice = o["max_buy_price"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            )
        }.orEmpty()
        return StockMarketItemsPage(
            items = items,
            total = root["total"]?.jsonPrimitive?.intOrNull ?: items.size,
            page = root["page"]?.jsonPrimitive?.intOrNull ?: 1,
            totalPages = root["total_pages"]?.jsonPrimitive?.intOrNull ?: 1,
        )
    }

    private fun parseCirculatingBalances(root: JsonObject): CirculatingBalancesReport {
        val totalsObj = root["totals"]?.jsonObject
        val totals = CirculatingBalancesTotals(
            playerNotesG = totalsObj?.get("player_notes_g")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            townNotesG = totalsObj?.get("town_notes_g")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            nationNotesG = totalsObj?.get("nation_notes_g")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            circulatingNotesG = totalsObj?.get("circulating_notes_g")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            playerCount = totalsObj?.get("player_count")?.jsonPrimitive?.intOrNull ?: 0,
            townCount = totalsObj?.get("town_count")?.jsonPrimitive?.intOrNull ?: 0,
            nationCount = totalsObj?.get("nation_count")?.jsonPrimitive?.intOrNull ?: 0,
        )
        val players = root["players"]?.jsonArray?.map { row ->
            val o = row.jsonObject
            CirculatingBalanceRow(
                rank = o["rank"]?.jsonPrimitive?.intOrNull ?: 0,
                displayName = o.string("display_name").ifBlank { o.string("minecraft_username") },
                minecraftUsername = o.stringOrNull("minecraft_username"),
                notesG = o["notes_g"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            )
        }.orEmpty()
        val towns = root["towns"]?.jsonArray?.map { row ->
            val o = row.jsonObject
            val bonded = o["bonded_g"]?.jsonPrimitive?.doubleOrNull
            val notes = o["notes_g"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            CirculatingTownRow(
                rank = o["rank"]?.jsonPrimitive?.intOrNull ?: 0,
                displayName = o.string("display_name"),
                nationName = o.stringOrNull("nation_name"),
                mayorName = o.stringOrNull("mayor_name"),
                notesG = notes,
                bondedG = bonded ?: notes,
                bondEarningsG = o["bond_earnings_g"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            )
        }.orEmpty()
        val nations = root["nations"]?.jsonArray?.map { row ->
            val o = row.jsonObject
            val bonded = o["bonded_g"]?.jsonPrimitive?.doubleOrNull
            val notes = o["notes_g"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            CirculatingNationRow(
                rank = o["rank"]?.jsonPrimitive?.intOrNull ?: 0,
                displayName = o.string("display_name"),
                leaderName = o.stringOrNull("leader_name"),
                townCount = o["town_count"]?.jsonPrimitive?.intOrNull ?: 0,
                notesG = notes,
                bondedG = bonded ?: notes,
                bondEarningsG = o["bond_earnings_g"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            )
        }.orEmpty()
        return CirculatingBalancesReport(
            serverId = root.string("server_id"),
            syncedAt = root.stringOrNull("synced_at"),
            totals = totals,
            players = players,
            towns = towns,
            nations = nations,
        )
    }

    private fun parseTreasuryReserve(root: JsonObject): TreasuryReserveSnapshot {
        val month = root["month"]?.jsonObject
        val noteSupply = root["note_supply"]?.jsonObject
        val goldFound = root["gold_found_summary"]?.jsonObject
        val balanceHistory = root["balance_history"]?.jsonArray?.mapNotNull { row ->
            val o = row.jsonObject
            val at = o.stringOrNull("recorded_at") ?: return@mapNotNull null
            ChartPoint(at, o["balance"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
        }.orEmpty()
        val balanceDaily = root["balance_daily"]?.jsonArray?.mapNotNull { row ->
            val o = row.jsonObject
            val day = o.stringOrNull("day") ?: return@mapNotNull null
            ChartPoint(day, o["balance"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
        }.orEmpty()
        val holderDaily = root["holder_supply_daily"]?.jsonArray?.mapNotNull { row ->
            val o = row.jsonObject
            val day = o.stringOrNull("day") ?: return@mapNotNull null
            ChartPoint(day, o["holder_combined_g"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
        }.orEmpty()
        val flowDaily = root["flow_daily"]?.jsonArray?.mapNotNull { row ->
            val o = row.jsonObject
            val day = o.stringOrNull("day") ?: return@mapNotNull null
            FlowDayRow(
                day = day,
                inflow = o["inflow"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                outflow = o["outflow"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            )
        }.orEmpty()
        val physicalFound = goldFound?.get("physical_mined_since_july_g")?.jsonPrimitive?.doubleOrNull
        val allFound = goldFound?.get("mined_since_july_g")?.jsonPrimitive?.doubleOrNull
        val foundHero = when {
            allFound != null && physicalFound != null && allFound > physicalFound + 0.01 -> allFound
            physicalFound != null -> physicalFound
            else -> allFound
        }
        return TreasuryReserveSnapshot(
            balance = root["balance"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            syncedAt = root.stringOrNull("synced_at"),
            viewMonth = root.stringOrNull("view_hst_month") ?: root.stringOrNull("current_hst_month"),
            monthInflow = month?.get("inflow")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            monthOutflow = month?.get("outflow")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            monthNet = month?.get("net")?.jsonPrimitive?.doubleOrNull ?: 0.0,
            playerNotesG = noteSupply?.get("player_notes_g")?.jsonPrimitive?.doubleOrNull
                ?: root["gold_minted_wallet_gold"]?.jsonPrimitive?.doubleOrNull,
            goldMinedG = noteSupply?.get("gold_mined_g")?.jsonPrimitive?.doubleOrNull,
            totalNotesG = noteSupply?.get("total_notes_g")?.jsonPrimitive?.doubleOrNull,
            backingPct = noteSupply?.get("backing_pct")?.jsonPrimitive?.doubleOrNull,
            overIssueG = noteSupply?.get("over_issue_g")?.jsonPrimitive?.doubleOrNull,
            noteSupplySummary = noteSupply?.get("summary")?.jsonPrimitive?.content,
            goldFoundSinceJuly = foundHero,
            balanceHistory = if (balanceHistory.isNotEmpty()) balanceHistory else balanceDaily,
            holderSupplyDaily = holderDaily,
            flowDaily = flowDaily,
        )
    }

    suspend fun fetchVaultOrders(serverId: String): Result<List<VaultOrderRow>> = withContext(io) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(serverId, Charsets.UTF_8.name())
            val text = get("${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/vault?server_id=$encoded")
            val root = AppJson.parseToJsonElement(text).jsonObject
            root["pending"]?.jsonArray?.map { row ->
                val o = row.jsonObject
                VaultOrderRow(
                    id = o["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                    itemKey = o.string("item_key"),
                    quantity = o["quantity"]?.jsonPrimitive?.intOrNull ?: 0,
                    pricePaid = o["price_paid"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    status = o.string("status").ifBlank { "pending" },
                    createdAt = o.stringOrNull("created_at"),
                )
            }.orEmpty()
        }
    }

    suspend fun buyFromApp(serverId: String, itemKey: String, quantity: Int): Result<BuyOrderResult> = withContext(io) {
        runCatching {
            val body = """{"server_id":"$serverId","item_key":"$itemKey","quantity":$quantity}"""
            val text = post("${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/buy", body)
            val o = AppJson.parseToJsonElement(text).jsonObject
            BuyOrderResult(
                orderId = o["order_id"]?.jsonPrimitive?.longOrNull,
                itemKey = o.string("item_key"),
                quantity = o["quantity"]?.jsonPrimitive?.intOrNull ?: quantity,
                pricePaid = o["price_paid"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                message = o.stringOrNull("message"),
            )
        }
    }

    suspend fun fetchShopAlerts(serverId: String): Result<List<ShopPriceAlertRow>> = withContext(io) {
        runCatching {
            val text = get("${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/shop-alerts?server_id=$serverId")
            val arr = AppJson.parseToJsonElement(text).jsonObject["alerts"]?.jsonArray ?: JsonArray(emptyList())
            arr.map { el ->
                val o = el.jsonObject
                ShopPriceAlertRow(
                    id = o.string("id"),
                    itemKey = o.string("item_key"),
                    alertType = o.string("alert_type").ifBlank { "below" },
                    thresholdValue = o["threshold_value"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    enabled = o["enabled"]?.jsonPrimitive?.intOrNull != 0
                        && o["enabled"]?.jsonPrimitive?.booleanOrNull != false,
                    lastSeenPrice = o["last_seen_price"]?.jsonPrimitive?.doubleOrNull,
                    lastNotifiedAt = o.stringOrNull("last_notified_at"),
                )
            }
        }
    }

    suspend fun createShopAlert(
        serverId: String,
        itemKey: String,
        alertType: String,
        threshold: Double,
    ): Result<Unit> = withContext(io) {
        runCatching {
            val safeItem = itemKey.trim().uppercase().replace(Regex("[^A-Z0-9_]"), "_")
            val safeType = if (alertType.equals("above", ignoreCase = true)) "above" else "below"
            val body =
                """{"server_id":"$serverId","item_key":"$safeItem","alert_type":"$safeType","threshold_value":$threshold}"""
            post("${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/shop-alerts", body)
            Unit
        }
    }

    suspend fun deleteShopAlert(alertId: String): Result<Unit> = withContext(io) {
        runCatching {
            delete("${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/shop-alerts/$alertId")
            Unit
        }
    }

    suspend fun fetchMayorTown(serverId: String): Result<MayorTownSnapshot> = withContext(io) {
        runCatching {
            val text = get("${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/towny/me?server_id=$serverId")
            val o = AppJson.parseToJsonElement(text).jsonObject
            val isMayor = o["is_mayor"]?.jsonPrimitive?.booleanOrNull == true
            val town = o["town"]?.jsonObject
            if (!isMayor || town == null) {
                MayorTownSnapshot(
                    isMayor = false,
                    minecraftLinked = o["minecraft_linked"]?.jsonPrimitive?.booleanOrNull != false,
                )
            } else {
                val listings = town["shop_listings"]?.jsonArray?.map { el ->
                    val row = el.jsonObject
                    ShopListingRow(
                        shopId = row.string("shop_id"),
                        ownerUsername = row.stringOrNull("owner_username"),
                        itemKey = row.string("item_key"),
                        price = row["price"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        world = row.string("world"),
                        x = row["x"]?.jsonPrimitive?.intOrNull ?: 0,
                        y = row["y"]?.jsonPrimitive?.intOrNull ?: 0,
                        z = row["z"]?.jsonPrimitive?.intOrNull ?: 0,
                        listingType = row.string("listing_type").ifBlank { "sell" },
                    )
                }.orEmpty()
                MayorTownSnapshot(
                    isMayor = true,
                    townName = town.stringOrNull("town_name"),
                    residentCount = town["resident_count"]?.jsonPrimitive?.intOrNull ?: 0,
                    nationName = town.stringOrNull("nation_name"),
                    isCapital = town["is_capital"]?.jsonPrimitive?.booleanOrNull == true
                        || town["is_capital"]?.jsonPrimitive?.intOrNull == 1,
                    discordInviteUrl = town.stringOrNull("discord_invite_url"),
                    shopListingCount = town["shop_listing_count"]?.jsonPrimitive?.intOrNull ?: listings.size,
                    shopListings = listings,
                    syncedAt = town.stringOrNull("synced_at"),
                )
            }
        }
    }

    private fun parseFeaturedServer(o: JsonObject): FeaturedServerConfig =
        FeaturedServerConfig(
            serverId = o.string("server_id").ifBlank { "rootmc" },
            name = o.string("name").ifBlank { "RootMC" },
            address = o.string("address").ifBlank { FALLBACK_ADDRESS },
            defaultWorldName = o.string("default_world_name").ifBlank { "RootMC" },
            gameVersion = o.string("game_version").ifBlank { "26.2" },
            mapUrl = o.stringOrNull("map_url"),
            verifyUrl = o.string("verify_url").ifBlank { VERIFY_URL },
            realmUrl = o.string("realm_url").ifBlank { REALM_URL },
            featured = o["featured"]?.jsonPrimitive?.booleanOrNull == true,
            connected = o["connected"]?.jsonPrimitive?.booleanOrNull != false,
            rootmcPluginInstalled = o["rootmc_plugin_installed"]?.jsonPrimitive?.booleanOrNull == true,
            rootmcPluginVersion = o.stringOrNull("rootmc_plugin_version"),
            rootmcSyncActive = o["rootmc_sync_active"]?.jsonPrimitive?.booleanOrNull == true
                || o["rootstat_active"]?.jsonPrimitive?.booleanOrNull == true,
            mcmmo = parseMcmmo(o["mcmmo"]?.jsonObject),
            playtime = parsePlaytime(o["playtime"]?.jsonObject),
            netWorth = parseNetWorth(o["net_worth"]?.jsonObject),
            belongsToServer = o["belongs_to_server"]?.jsonPrimitive?.booleanOrNull == true,
            autoAddWorld = o["auto_add_world"]?.jsonPrimitive?.booleanOrNull == true,
        )

    private fun parsePlaytime(o: JsonObject?): PlaytimeStats? {
        if (o == null) return null
        val total = o["total_playtime_seconds"]?.jsonPrimitive?.longOrNull
            ?: o["total_seconds"]?.jsonPrimitive?.longOrNull
        if ((total == null || total <= 0L)
            && o.stringOrNull("first_join_at") == null
            && o.stringOrNull("last_login_at") == null
        ) {
            return null
        }
        return PlaytimeStats(
            totalSeconds = total ?: 0L,
            firstJoinAt = o.stringOrNull("first_join_at"),
            lastLoginAt = o.stringOrNull("last_login_at"),
        )
    }

    private fun parseNetWorth(o: JsonObject?): NetWorthStats? {
        if (o == null) return null
        val total = o["total_value"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val balance = o["balance_value"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val inventory = o["inventory_value"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        if (total <= 0.0 && balance <= 0.0 && inventory <= 0.0) return null
        return NetWorthStats(
            balanceValue = balance,
            inventoryValue = inventory,
            chestValue = o["chest_value"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            shopStockValue = o["shop_stock_value"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            totalValue = total,
            syncedAt = o.stringOrNull("synced_at"),
        )
    }

    private fun parseMcmmo(o: JsonObject?): McMMOStats? {
        if (o == null) return null
        val skillsObj = o["skills"]?.jsonObject
        val skills = skillsObj?.entries?.associate { (key, value) ->
            key to (value.jsonPrimitive.intOrNull ?: 0)
        }.orEmpty()
        val power = o["power_level"]?.jsonPrimitive?.intOrNull ?: skills.values.sum()
        if (power <= 0 && skills.isEmpty()) return null
        return McMMOStats(
            powerLevel = power,
            skills = skills,
            syncedAt = o.stringOrNull("synced_at"),
            minecraftUsername = o.stringOrNull("minecraft_username"),
        )
    }

    private fun parseMembership(o: JsonObject): ServerMembership {
        val serversArray = o["servers"]?.jsonArray
        val servers = if (serversArray != null) {
            serversArray.map { parseFeaturedServer(it.jsonObject) }
        } else {
            val featured = o["featured_server"]?.jsonObject
            if (featured != null) listOf(parseFeaturedServer(featured)) else emptyList()
        }
        val featured = o["featured_server"]?.jsonObject?.let { parseFeaturedServer(it) }
            ?: servers.firstOrNull { it.featured }
            ?: servers.firstOrNull()
        return ServerMembership(
            accountId = o.string("account_id"),
            minecraftLinked = o["minecraft_linked"]?.jsonPrimitive?.booleanOrNull == true
                || o["rootstat_linked"]?.jsonPrimitive?.booleanOrNull == true,
            minecraftUsername = o.stringOrNull("minecraft_username"),
            minecraftUuid = o.stringOrNull("minecraft_uuid"),
            verifiedAt = o.stringOrNull("verified_at"),
            servers = servers,
            featuredServer = featured,
        )
    }

    private fun get(url: String): String {
        val req = Request.Builder()
            .url(url)
            .get()
            .header("X-App-Version", BuildConfig.VERSION_NAME)
            .header("X-RR-App-Id", BLOCKNOTES_APP_ID)
            .build()
        return http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw apiError(text, resp.code)
            text
        }
    }

    private fun post(url: String, jsonBody: String): String {
        val req = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .header("X-App-Version", BuildConfig.VERSION_NAME)
            .header("X-RR-App-Id", BLOCKNOTES_APP_ID)
            .build()
        return http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw apiError(text, resp.code)
            text
        }
    }

    private fun delete(url: String): String {
        val req = Request.Builder()
            .url(url)
            .delete()
            .header("X-App-Version", BuildConfig.VERSION_NAME)
            .header("X-RR-App-Id", BLOCKNOTES_APP_ID)
            .build()
        return http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw apiError(text, resp.code)
            text
        }
    }

    private fun apiError(text: String, code: Int): Throwable {
        val root = runCatching { AppJson.parseToJsonElement(text).jsonObject }.getOrNull()
        val detail = root?.get("detail")?.jsonPrimitive?.contentOrNull
        return IllegalStateException(detail ?: "server_api_$code")
    }

    suspend fun fetchDailyReports(serverId: String, limit: Int = 14): Result<DailyReportPage> = withContext(io) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(serverId, Charsets.UTF_8.name())
            val text = get(
                "${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/daily-report?server_id=$encoded&limit=$limit",
            )
            val root = AppJson.parseToJsonElement(text).jsonObject
            val reports = root["reports"]?.jsonArray?.map { row ->
                val o = row.jsonObject
                val categories = o["categories"]?.jsonArray?.map { cat ->
                    val c = cat.jsonObject
                    DailyReportCategory(
                        category = c.string("category"),
                        title = c.string("title"),
                        postedAt = c.stringOrNull("posted_at"),
                        summary = c.string("summary"),
                        reportText = c.string("report_text"),
                        unchangedFromPrior = c["unchanged_from_prior"]?.jsonPrimitive?.booleanOrNull == true,
                    )
                }.orEmpty()
                DailyReportDay(
                    dayKey = o.string("day_key"),
                    postedAt = o.stringOrNull("posted_at"),
                    summary = o.string("summary"),
                    reportText = o.string("report_text"),
                    unchangedFromPrior = o["unchanged_from_prior"]?.jsonPrimitive?.booleanOrNull == true,
                    categories = categories,
                )
            }.orEmpty()
            DailyReportPage(
                serverId = root.string("server_id").ifBlank { serverId },
                reports = reports,
            )
        }
    }

    private fun fallbackFeaturedServer() = FeaturedServerConfig(
        serverId = "rootmc",
        name = "RootMC",
        address = FALLBACK_ADDRESS,
        defaultWorldName = "RootMC",
        gameVersion = "26.2",
        mapUrl = null,
        verifyUrl = VERIFY_URL,
        realmUrl = REALM_URL,
        featured = true,
        connected = true,
        rootmcPluginInstalled = false,
        rootmcPluginVersion = null,
        rootmcSyncActive = false,
    )

    private companion object {
        const val FALLBACK_ADDRESS = "Announced at launch — join RootMC Discord"
        const val VERIFY_URL = "https://rootmc.net/verify/"
        const val REALM_URL = "https://rootmc.net/"
    }
}

private fun JsonObject.string(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
private fun JsonObject.stringOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
