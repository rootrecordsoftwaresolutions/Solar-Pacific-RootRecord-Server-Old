package com.rootrecord.rootmc.domain.model

import com.rootrecord.rootmc.data.local.entity.MinecraftDimension
import com.rootrecord.rootmc.util.CoordinateMath
import kotlin.math.roundToInt

object CoordinateFormats {

    fun formatTpCommand(
        x: Int,
        y: Int,
        z: Int,
        dimension: MinecraftDimension,
        label: String = "",
    ): String = buildString {
        if (label.isNotBlank()) {
            appendLine("# $label")
        }
        append("/tp @s $x $y $z")
        when (dimension) {
            MinecraftDimension.NETHER -> append("  # Nether")
            MinecraftDimension.END -> append("  # End")
            MinecraftDimension.OVERWORLD -> Unit
        }
    }.trim()

    fun formatPlainText(
        x: Int,
        y: Int,
        z: Int,
        dimension: MinecraftDimension,
        label: String = "",
    ): String = buildString {
        if (label.isNotBlank()) append("$label: ")
        append("X $x, Y $y, Z $z")
        if (dimension != MinecraftDimension.OVERWORLD) {
            append(" (${dimension.name.lowercase().replaceFirstChar { it.titlecase() }})")
        }
    }

    fun formatDistance(distanceBlocks: Double, sameDimension: Boolean): String {
        val rounded = distanceBlocks.roundToInt()
        return if (sameDimension) {
            "$rounded blocks"
        } else {
            "$rounded blocks (cross-dimension; compare Overworld coords only)"
        }
    }

    fun formatBearing(short: String, long: String, fromLabel: String, toLabel: String): String =
        "$short ($long) · $fromLabel → $toLabel"

    fun formatAlignment(
        alignment: CoordinateMath.HorizontalAlignment,
        diagonalBearing: String?,
    ): String = when (alignment) {
        CoordinateMath.HorizontalAlignment.NORTH_SOUTH -> "Aligned North–South"
        CoordinateMath.HorizontalAlignment.EAST_WEST -> "Aligned East–West"
        CoordinateMath.HorizontalAlignment.DIAGONAL -> {
            val bearing = diagonalBearing ?: "diagonal"
            "Aligned diagonal ($bearing)"
        }
    }

    fun formatPortalPair(overworldX: Int, overworldZ: Int, overworldY: Int = 64): String {
        val (netherX, netherZ) = CoordinateMath.overworldToNether(overworldX, overworldZ)
        return buildString {
            appendLine("Overworld portal: X $overworldX, Y $overworldY, Z $overworldZ")
            append("Nether portal:   X $netherX, Y ?, Z $netherZ")
        }
    }

    fun formatPortalFromNether(netherX: Int, netherZ: Int, netherY: Int = 64): String {
        val (overworldX, overworldZ) = CoordinateMath.netherToOverworld(netherX, netherZ)
        return buildString {
            appendLine("Nether portal:   X $netherX, Y $netherY, Z $netherZ")
            append("Overworld portal: X $overworldX, Y ?, Z $overworldZ")
        }
    }
}
