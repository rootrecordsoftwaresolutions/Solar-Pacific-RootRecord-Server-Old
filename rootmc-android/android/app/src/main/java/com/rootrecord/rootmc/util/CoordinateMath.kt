package com.rootrecord.rootmc.util

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

object CoordinateMath {

    fun distance3d(
        x1: Int,
        y1: Int,
        z1: Int,
        x2: Int,
        y2: Int,
        z2: Int,
    ): Double {
        val dx = (x2 - x1).toDouble()
        val dy = (y2 - y1).toDouble()
        val dz = (z2 - z1).toDouble()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /** Minecraft horizontal delta: +X east, +Z south. */
    fun horizontalDelta(x1: Int, z1: Int, x2: Int, z2: Int): Pair<Int, Int> =
        Pair(x2 - x1, z2 - z1)

    /**
     * Eight-point compass bearing from (x1,z1) toward (x2,z2).
     * Returns short label (N, NE, …) or null when both deltas are zero.
     */
    fun compassBearingShort(dx: Int, dz: Int): String? {
        if (dx == 0 && dz == 0) return null
        val angleDeg = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble()))
        val normalized = (angleDeg + 360.0) % 360.0
        return when {
            normalized < 22.5 || normalized >= 337.5 -> "S"
            normalized < 67.5 -> "SE"
            normalized < 112.5 -> "E"
            normalized < 157.5 -> "NE"
            normalized < 202.5 -> "N"
            normalized < 247.5 -> "NW"
            normalized < 292.5 -> "W"
            else -> "SW"
        }
    }

    fun compassBearingLong(short: String): String = when (short) {
        "N" -> "North"
        "NE" -> "North-East"
        "E" -> "East"
        "SE" -> "South-East"
        "S" -> "South"
        "SW" -> "South-West"
        "W" -> "West"
        "NW" -> "North-West"
        else -> short
    }

    enum class HorizontalAlignment {
        NORTH_SOUTH,
        EAST_WEST,
        DIAGONAL,
    }

    /** True when X/Z deltas form a perfect N–S, E–W, or 45° diagonal line. */
    fun horizontalAlignment(dx: Int, dz: Int): HorizontalAlignment? {
        if (dx == 0 && dz == 0) return null
        if (dx == 0) return HorizontalAlignment.NORTH_SOUTH
        if (dz == 0) return HorizontalAlignment.EAST_WEST
        if (abs(dx) == abs(dz)) return HorizontalAlignment.DIAGONAL
        return null
    }

    /** Overworld block coords → Nether portal coords (divide X/Z by 8). */
    fun overworldToNether(overworldX: Int, overworldZ: Int): Pair<Int, Int> =
        Pair(overworldX / 8, overworldZ / 8)

    /** Nether block coords → linked Overworld portal coords (multiply X/Z by 8). */
    fun netherToOverworld(netherX: Int, netherZ: Int): Pair<Int, Int> =
        Pair(netherX * 8, netherZ * 8)

    data class ChunkBounds(
        val chunkX: Int,
        val chunkZ: Int,
        val minX: Int,
        val maxX: Int,
        val minZ: Int,
        val maxZ: Int,
    )

    /** Minecraft chunk (16×16 blocks) containing the given block position. */
    fun chunkBoundsForBlock(blockX: Int, blockZ: Int): ChunkBounds {
        val chunkX = Math.floorDiv(blockX, 16)
        val chunkZ = Math.floorDiv(blockZ, 16)
        return ChunkBounds(
            chunkX = chunkX,
            chunkZ = chunkZ,
            minX = chunkX * 16,
            maxX = chunkX * 16 + 15,
            minZ = chunkZ * 16,
            maxZ = chunkZ * 16 + 15,
        )
    }

    /** Axis-aligned rectangle from two corner blocks (X/Z only). */
    fun rectangleFromCorners(
        x1: Int,
        z1: Int,
        x2: Int,
        z2: Int,
    ): ChunkBounds {
        val minX = minOf(x1, x2)
        val maxX = maxOf(x1, x2)
        val minZ = minOf(z1, z2)
        val maxZ = maxOf(z1, z2)
        return ChunkBounds(
            chunkX = Math.floorDiv(minX, 16),
            chunkZ = Math.floorDiv(minZ, 16),
            minX = minX,
            maxX = maxX,
            minZ = minZ,
            maxZ = maxZ,
        )
    }

    fun formatAreaBounds(minX: Int, maxX: Int, minZ: Int, maxZ: Int): String =
        "X $minX…$maxX · Z $minZ…$maxZ"
}
