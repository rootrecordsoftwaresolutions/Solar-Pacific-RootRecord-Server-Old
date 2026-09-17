package com.rootrecord.kilauea.alerts.notifications

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertDifferTest {

    @Test
    fun nwsFeatureIds_readsPropertiesId() {
        val geo = buildJsonObject {
            put(
                "features",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put(
                                "properties",
                                buildJsonObject {
                                    put("id", JsonPrimitive("urn:oid:2.49.0.1.840.0.test"))
                                },
                            )
                        },
                    ),
                ),
            )
        }
        assertEquals(
            setOf("urn:oid:2.49.0.1.840.0.test"),
            AlertDiffer.nwsFeatureIds(geo),
        )
    }

    @Test
    fun newIds_diff() {
        val cur = setOf("a", "b", "c")
        val seen = setOf("a")
        assertEquals(setOf("b", "c"), AlertDiffer.newIds(cur, seen))
    }

    @Test
    fun encodeRoundTrip_stringSet() {
        val s = setOf("x", "y")
        val json = AlertDiffer.encodeStringSet(s)
        assertEquals(s, AlertDiffer.parseStringSet(json))
    }
}
