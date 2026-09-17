package com.rootrecord.rootmc.domain.usecase

import com.rootrecord.rootmc.data.local.entity.MinecraftDimension
import com.rootrecord.rootmc.domain.model.CoordinateFormats
import com.rootrecord.rootmc.util.CoordinateMath
import javax.inject.Inject

data class DistanceResult(
    val distanceBlocks: Double,
    val sameDimension: Boolean,
    val formatted: String,
    val bearingShort: String?,
    val bearingLong: String?,
    val alignmentText: String?,
)

class DistanceBetweenCoordsUseCase @Inject constructor() {

    operator fun invoke(
        x1: Int,
        y1: Int,
        z1: Int,
        dimension1: MinecraftDimension,
        x2: Int,
        y2: Int,
        z2: Int,
        dimension2: MinecraftDimension,
    ): DistanceResult {
        val sameDimension = dimension1 == dimension2
        val distance = CoordinateMath.distance3d(x1, y1, z1, x2, y2, z2)
        val (dx, dz) = CoordinateMath.horizontalDelta(x1, z1, x2, z2)
        val bearingShort = CoordinateMath.compassBearingShort(dx, dz)
        val bearingLong = bearingShort?.let { CoordinateMath.compassBearingLong(it) }
        val alignmentText = if (sameDimension) {
            CoordinateMath.horizontalAlignment(dx, dz)?.let { alignment ->
                CoordinateFormats.formatAlignment(alignment, bearingShort)
            }
        } else {
            null
        }
        return DistanceResult(
            distanceBlocks = distance,
            sameDimension = sameDimension,
            formatted = CoordinateFormats.formatDistance(distance, sameDimension),
            bearingShort = bearingShort,
            bearingLong = bearingLong,
            alignmentText = alignmentText,
        )
    }
}
