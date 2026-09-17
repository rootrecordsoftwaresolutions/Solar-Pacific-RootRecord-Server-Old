package com.rootrecord.rootmc.util



import com.rootrecord.rootmc.data.remote.AppJson

import kotlinx.serialization.json.JsonArray

import kotlinx.serialization.json.JsonElement

import kotlinx.serialization.json.JsonObject

import kotlinx.serialization.json.jsonArray

import kotlinx.serialization.json.jsonPrimitive



data class ReferenceDisplayItem(

    val id: String,

    val title: String,

    val subtitle: String,

)



object ReferenceItemParser {



    fun parseBundle(jsonBlob: String): List<ReferenceDisplayItem> {

        return runCatching {

            val root = AppJson.parseToJsonElement(jsonBlob)

            val array = when (root) {

                is JsonArray -> root

                is JsonObject -> root["items"]?.jsonArray ?: return emptyList()

                else -> return emptyList()

            }

            array.mapNotNull { parseElement(it) }

        }.getOrDefault(emptyList())

    }



    private fun parseElement(el: JsonElement): ReferenceDisplayItem? {

        val obj = el as? JsonObject ?: return null



        // minecraft-ids.grahamedgecombe.com format (type, meta, name, text_type)

        val legacyType = obj["type"]?.jsonPrimitive?.content?.toIntOrNull()

        if (legacyType != null) {

            val meta = obj["meta"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0

            val name = obj["name"]?.jsonPrimitive?.content?.trim().orEmpty()

            val textType = obj["text_type"]?.jsonPrimitive?.content?.trim().orEmpty()

            if (name.isBlank()) return null

            val numericId = if (meta > 0) "$legacyType:$meta" else legacyType.toString()

            val subtitle = buildList {

                add("Numeric ID $numericId")

                if (textType.isNotEmpty()) add("minecraft:$textType")

            }.joinToString(" · ")

            return ReferenceDisplayItem(

                id = numericId,

                title = name,

                subtitle = subtitle,

            )

        }



        val id = obj["id"]?.jsonPrimitive?.content?.trim().orEmpty()

        val name = obj["name"]?.jsonPrimitive?.content?.trim()

            ?: obj["profession"]?.jsonPrimitive?.content?.trim()

            ?: id

        if (name.isBlank()) return null



        val subtitle = buildList {

            obj["stack"]?.jsonPrimitive?.content?.let { add("Stack: $it") }

            obj["maxLevel"]?.jsonPrimitive?.content?.let { add("Max level: $it") }

            obj["effect"]?.jsonPrimitive?.content?.let { add(it) }

            obj["trade"]?.jsonPrimitive?.content?.let { add(it) }

            obj["hostile"]?.jsonPrimitive?.content?.let { hostile ->

                add(if (hostile == "true") "Hostile" else "Passive")

            }

            obj["level"]?.jsonPrimitive?.content?.let { add("Villager level $it") }

        }.joinToString(" · ")



        return ReferenceDisplayItem(

            id = id.ifBlank { name },

            title = name,

            subtitle = subtitle,

        )

    }

}

