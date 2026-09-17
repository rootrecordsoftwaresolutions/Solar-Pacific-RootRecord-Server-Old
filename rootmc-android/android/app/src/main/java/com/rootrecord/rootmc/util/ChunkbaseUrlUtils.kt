package com.rootrecord.rootmc.util

import com.rootrecord.rootmc.data.local.entity.MinecraftDimension

object ChunkbaseUrlUtils {

    private const val BASE = "https://www.chunkbase.com/apps/seed-map"

    /** Build Chunkbase seed-map URL (opens in WebView). Returns null if seed is not usable. */
    fun buildSeedMapUrl(
        seedRaw: String?,
        gameVersion: String?,
        dimension: String,
        centerX: Int = 0,
        centerZ: Int = 0,
        zoom: Double = 0.5,
    ): String? {
        val seed = parseSeed(seedRaw) ?: return null
        val platform = toChunkbasePlatform(gameVersion)
        val dim = when (dimension.uppercase()) {
            MinecraftDimension.NETHER.name -> "nether"
            MinecraftDimension.END.name -> "end"
            else -> "overworld"
        }
        return "$BASE#seed=$seed&platform=$platform&dimension=$dim&x=$centerX&z=$centerZ&zoom=$zoom"
    }

    fun parseSeed(seedRaw: String?): Long? {
        val s = seedRaw?.trim().orEmpty()
        if (s.isEmpty()) return null
        s.toLongOrNull()?.let { return it }
        val digits = s.filter { it.isDigit() || it == '-' }
        return digits.toLongOrNull()
    }

    fun hasUsableSeed(seedRaw: String?): Boolean = parseSeed(seedRaw) != null

    /** Chunkbase platform slug, e.g. java_1_21 or java_26_1 */
    fun toChunkbasePlatform(gameVersion: String?): String {
        val v = gameVersion?.trim().orEmpty().ifBlank { "1.21" }
        val parts = v.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }
        return when {
            parts.size >= 2 -> "java_${parts[0]}_${parts[1]}"
            parts.size == 1 -> "java_${parts[0]}_0"
            else -> "java_1_21"
        }
    }
}
