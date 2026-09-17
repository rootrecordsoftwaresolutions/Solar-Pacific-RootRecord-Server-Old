package com.rootrecord.rootmc.domain.usecase

import com.rootrecord.rootmc.domain.model.CoordinateFormats
import com.rootrecord.rootmc.util.CoordinateMath
import javax.inject.Inject

data class PortalCalcResult(
    val overworldX: Int,
    val overworldZ: Int,
    val netherX: Int,
    val netherZ: Int,
    val formatted: String,
)

class NetherPortalCalcUseCase @Inject constructor() {

    /** Calculate linked portal coords from an Overworld position. */
    operator fun invoke(overworldX: Int, overworldZ: Int, overworldY: Int = 64): PortalCalcResult {
        val (netherX, netherZ) = CoordinateMath.overworldToNether(overworldX, overworldZ)
        return PortalCalcResult(
            overworldX = overworldX,
            overworldZ = overworldZ,
            netherX = netherX,
            netherZ = netherZ,
            formatted = CoordinateFormats.formatPortalPair(overworldX, overworldZ, overworldY),
        )
    }

    /** Calculate linked portal coords from a Nether position. */
    fun fromNether(netherX: Int, netherZ: Int, netherY: Int = 64): PortalCalcResult {
        val (overworldX, overworldZ) = CoordinateMath.netherToOverworld(netherX, netherZ)
        return PortalCalcResult(
            overworldX = overworldX,
            overworldZ = overworldZ,
            netherX = netherX,
            netherZ = netherZ,
            formatted = CoordinateFormats.formatPortalFromNether(netherX, netherZ, netherY),
        )
    }
}
