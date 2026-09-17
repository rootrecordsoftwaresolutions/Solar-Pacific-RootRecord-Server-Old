package com.rootrecord.rootmc.data.repository

import android.util.Base64
import com.rootrecord.rootmc.data.local.RootMcPreferences
import com.rootrecord.rootmc.data.remote.AppJson
import com.rootrecord.rootmc.di.IoDispatcher
import com.rootrecord.rootmc.domain.model.MinecraftProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class MinecraftProfileRepository @Inject constructor(
    @param:Named("mojang") private val http: OkHttpClient,
    private val prefs: RootMcPreferences,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun lookupAndSave(input: String): Result<MinecraftProfile> = withContext(io) {
        runCatching {
            val trimmed = input.trim()
            require(trimmed.isNotBlank()) { "Enter a Minecraft username or UUID." }

            val profile = if (MinecraftProfile.looksLikeUuid(trimmed)) {
                fetchByUuid(trimmed)
            } else {
                val username = trimmed.replace(Regex("\\s+"), "")
                require(username.length in 3..16) { "Username must be 3–16 characters." }
                require(username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
                    "Username may only contain letters, numbers, and underscores."
                }
                fetchByUsername(username)
            }

            prefs.setMinecraftProfile(
                username = profile.username,
                uuid = profile.uuid,
                skinUrl = profile.skinUrl,
                skinSlim = profile.skinSlim,
            )
            profile
        }
    }

    suspend fun clearProfile() = withContext(io) {
        prefs.clearMinecraftProfile()
    }

    private fun fetchByUsername(username: String): MinecraftProfile {
        val req = Request.Builder()
            .url("https://api.mojang.com/users/profiles/minecraft/${username}")
            .get()
            .header("User-Agent", "RootRecord-RootMC/1.0")
            .build()
        http.newCall(req).execute().use { resp ->
            when (resp.code) {
                404 -> error("No Minecraft account found for \"$username\".")
                !in 200..299 -> error("Mojang lookup failed (${resp.code}). Try again in a moment.")
            }
            val text = resp.body?.string().orEmpty()
            val root = AppJson.parseToJsonElement(text).jsonObject
            val id = root["id"]?.jsonPrimitive?.content?.trim().orEmpty()
            val name = root["name"]?.jsonPrimitive?.content?.trim().orEmpty()
            require(id.isNotBlank() && name.isNotBlank()) { "Unexpected Mojang response." }
            return fetchByUuid(id, resolvedName = name)
        }
    }

    private fun fetchByUuid(rawUuid: String, resolvedName: String? = null): MinecraftProfile {
        val compactUuid = rawUuid.replace("-", "").lowercase()
        val req = Request.Builder()
            .url("https://sessionserver.mojang.com/session/minecraft/profile/$compactUuid")
            .get()
            .header("User-Agent", "RootRecord-RootMC/1.0")
            .build()
        http.newCall(req).execute().use { resp ->
            when (resp.code) {
                404 -> error("No Minecraft profile found for that UUID.")
                429 -> error("Mojang rate limit — wait a few seconds and try again.")
                !in 200..299 -> error("Mojang profile lookup failed (${resp.code}).")
            }
            val text = resp.body?.string().orEmpty()
            val root = AppJson.parseToJsonElement(text).jsonObject
            val name = resolvedName
                ?: root["name"]?.jsonPrimitive?.content?.trim().orEmpty()
            val (skinUrl, skinSlim) = parseTextures(root)
            return MinecraftProfile(
                username = name.ifBlank { "Unknown" },
                uuid = MinecraftProfile.formatUuid(compactUuid),
                skinUrl = skinUrl,
                skinSlim = skinSlim,
            )
        }
    }

    private fun parseTextures(profile: JsonObject): Pair<String?, Boolean> {
        val properties = profile["properties"]?.jsonArray ?: return null to false
        val texturesValue = properties
            .mapNotNull { it.jsonObject }
            .firstOrNull { it["name"]?.jsonPrimitive?.content == "textures" }
            ?.get("value")
            ?.jsonPrimitive
            ?.content
            ?: return null to false

        val decoded = runCatching {
            String(Base64.decode(texturesValue, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull() ?: return null to false

        val texturesRoot = runCatching {
            AppJson.parseToJsonElement(decoded).jsonObject
        }.getOrNull()?.get("textures")?.jsonObject ?: return null to false

        val skin = texturesRoot["SKIN"]?.jsonObject ?: return null to false
        val url = skin["url"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotBlank() }
        val slim = skin["metadata"]?.jsonObject
            ?.get("model")
            ?.jsonPrimitive
            ?.content
            ?.equals("slim", ignoreCase = true) == true
        return url to slim
    }
}
