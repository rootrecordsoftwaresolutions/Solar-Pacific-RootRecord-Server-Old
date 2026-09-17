package com.rootrecord.kilauea.alerts.domain

/**
 * Top Big Island locations — coordinates approximate population centers / landmarks.
 */
enum class BigIslandLocation(
    val id: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
) {
    Hilo("hilo", "Hilo", 19.7297, -155.0900),
    KailuaKona("kona", "Kailua-Kona", 19.6406, -155.9956),
    VolcanoVillage("volcano", "Volcano Village / Hawaiʻi Volcanoes NP", 19.4194, -155.2888),
    Waimea("waimea", "Waimea (Kamuela)", 20.0233, -155.6719),
    Pahoa("pahoa", "Pahoa", 19.4943, -154.9533),
    Waikoloa("waikoloa", "Waikoloa", 19.9375, -155.7920),
    Honokaa("honokaa", "Honokaʻa", 20.0773, -155.4640),
    Naalehu("naalehu", "Nāʻālehu", 19.0600, -155.5861),
    CaptainCook("captain_cook", "Captain Cook", 19.4969, -155.9217),
    SouthPoint("south_point", "South Point / Ocean View", 18.9126, -155.6826),
    ;

    companion object {
        fun byId(id: String): BigIslandLocation? =
            entries.firstOrNull { it.id == id }
    }
}
