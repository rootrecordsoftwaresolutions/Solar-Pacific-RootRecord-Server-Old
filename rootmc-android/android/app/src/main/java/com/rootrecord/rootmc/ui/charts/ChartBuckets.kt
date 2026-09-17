package com.rootrecord.rootmc.ui.charts

import com.rootrecord.rootmc.data.repository.ChartPoint
import com.rootrecord.rootmc.data.repository.StockPricePoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

enum class MarketChartPeriod { HOUR, DAY, WEEK }

data class OhlcBucket(
    val key: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
)

object ChartBuckets {
    fun marketBuckets(points: List<StockPricePoint>, period: MarketChartPeriod): List<OhlcBucket> {
        val byKey = linkedMapOf<String, OhlcBucket>()
        for (point in points) {
            val key = marketBucketKey(point.recordedAt, period) ?: continue
            val price = point.avgPrice
            val existing = byKey[key]
            if (existing == null) {
                byKey[key] = OhlcBucket(key, price, price, price, price)
            } else {
                byKey[key] = existing.copy(
                    high = maxOf(existing.high, price),
                    low = minOf(existing.low, price),
                    close = price,
                )
            }
        }
        return byKey.values.toList()
    }

    fun marketBucketKey(iso: String, period: MarketChartPeriod): String? {
        val raw = iso.trim()
        if (raw.isEmpty()) return null
        return when (period) {
            MarketChartPeriod.HOUR -> if (raw.length >= 13) raw.substring(0, 13) else raw
            MarketChartPeriod.DAY -> if (raw.length >= 10) raw.substring(0, 10) else raw
            MarketChartPeriod.WEEK -> weekStartKey(raw)
        }
    }

    fun formatMarketLabel(key: String, period: MarketChartPeriod): String = when (period) {
        MarketChartPeriod.HOUR ->
            if (key.length >= 13) key.substring(5, 13).replace('T', ' ') else key
        MarketChartPeriod.WEEK ->
            if (key.length >= 10) "Wk ${key.substring(5)}" else key
        MarketChartPeriod.DAY ->
            if (key.length >= 10) key.substring(5) else key
    }

    fun reserveDailyLabels(points: List<ChartPoint>, maxPoints: Int = 45): List<ChartPoint> {
        if (points.size <= maxPoints) return points
        val step = (points.size + maxPoints - 1) / maxPoints
        return points.filterIndexed { index, _ -> index % step == 0 || index == points.lastIndex }
    }

    private fun weekStartKey(iso: String): String {
        return runCatching {
            val day = if (iso.length >= 10) {
                LocalDate.parse(iso.substring(0, 10))
            } else {
                Instant.parse(iso).atOffset(ZoneOffset.UTC).toLocalDate()
            }
            val weekFields = WeekFields.of(Locale.US)
            val start = day.with(weekFields.dayOfWeek(), 1)
            start.format(DateTimeFormatter.ISO_LOCAL_DATE)
        }.getOrElse { iso.take(10) }
    }
}
