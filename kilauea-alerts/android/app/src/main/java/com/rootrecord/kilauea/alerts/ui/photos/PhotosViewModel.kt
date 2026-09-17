package com.rootrecord.kilauea.alerts.ui.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.kilauea.alerts.data.local.KilaueaPreferences
import com.rootrecord.kilauea.alerts.data.repository.PhotosRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import javax.inject.Inject

data class PhotoItem(
    val id: String,
    val createdAt: String?,
    val caption: String?,
    val url: String,
)

data class PhotosUiState(
    val loading: Boolean = false,
    val uploading: Boolean = false,
    val items: List<PhotoItem> = emptyList(),
    val error: String? = null,
    val toast: String? = null,
)

@HiltViewModel
class PhotosViewModel @Inject constructor(
    private val repo: PhotosRepository,
    private val prefs: KilaueaPreferences,
) : ViewModel() {

    val signedIn = prefs.authSignedIn.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    // Photo submissions are a Pro/Lifetime perk — gallery viewing stays free. Mirrors the same
    // `authProUnlocked` flag the rest of the app uses (set by RootRecordAuthRepository on login /
    // /v1/auth/me refresh; Lifetime is rolled up server-side into proUnlocked = true).
    val proUnlocked = prefs.authProUnlocked.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _state = MutableStateFlow(PhotosUiState())
    val state = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val r = repo.gallery()
            _state.update { s ->
                val json = r.getOrNull()
                s.copy(
                    loading = false,
                    items = json?.let(::parseItems) ?: emptyList(),
                    error = r.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun upload(file: File, caption: String?) {
        viewModelScope.launch {
            if (!prefs.authSignedIn.first()) {
                _state.update { it.copy(toast = "Sign in required to submit photos.") }
                return@launch
            }
            // Belt-and-suspenders: the UI hides the picker for non-Pro users, but if someone routes
            // around it (deep link, stale state) we still refuse client-side instead of wasting an
            // R2 upload + server moderation slot.
            if (!prefs.authProUnlocked.first()) {
                _state.update { it.copy(toast = "Photo submissions are limited to members only.") }
                return@launch
            }
            _state.update { it.copy(uploading = true, error = null, toast = null) }
            val r = repo.upload(file, caption)
            _state.update { s ->
                val ok = r.getOrNull()
                if (ok != null) {
                    s.copy(uploading = false, toast = "Submitted for approval.", error = null)
                } else {
                    val msg = r.exceptionOrNull()?.message
                    s.copy(uploading = false, error = msg ?: "Upload failed.")
                }
            }
            load()
        }
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    private fun parseItems(json: JsonObject): List<PhotoItem> {
        val items = json["items"]?.jsonArray ?: JsonArray(emptyList())
        return items.mapNotNull { el ->
            val o = el.jsonObject
            val id = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val url = o["url"]?.jsonPrimitive?.content ?: return@mapNotNull null
            PhotoItem(
                id = id,
                createdAt = o["created_at"]?.jsonPrimitive?.content,
                caption = o["caption"]?.jsonPrimitive?.content,
                url = url,
            )
        }
    }
}

