package com.rootrecord.kilauea.alerts.data.repository

import com.rootrecord.kilauea.alerts.data.local.KilaueaPreferences
import com.rootrecord.kilauea.alerts.data.remote.AppJson
import com.rootrecord.kilauea.alerts.data.remote.RootRecordApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotosRepository @Inject constructor(
    private val api: RootRecordApi,
    private val prefs: KilaueaPreferences,
) {
    suspend fun gallery(limit: Int = 30, cursor: String? = null): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            val body = api.photosGallery(limit = limit, cursor = cursor)
            AppJson.parseToJsonElement(body).let { it as JsonObject }
        }
    }

    suspend fun upload(file: File, caption: String?): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            val token = prefs.getAuthAccessToken() ?: error("not_signed_in")
            val mediaType = guessMediaType(file.name).toMediaType()
            val part = MultipartBody.Part.createFormData(
                "file",
                file.name,
                file.asRequestBody(mediaType),
            )
            val body = api.uploadPhoto(
                bearer = "Bearer $token",
                file = part,
                caption = caption?.take(400),
            )
            AppJson.parseToJsonElement(body).let { it as JsonObject }
        }
    }

    private fun guessMediaType(name: String): String {
        val n = name.lowercase()
        return when {
            n.endsWith(".png") -> "image/png"
            n.endsWith(".webp") -> "image/webp"
            n.endsWith(".heic") || n.endsWith(".heif") -> "image/heic"
            else -> "image/jpeg"
        }
    }
}

