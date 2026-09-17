package com.rootrecord.rootmc.data.repository

import com.rootrecord.rootmc.data.local.RootMcPreferences
import com.rootrecord.rootmc.data.remote.AppJson
import com.rootrecord.rootmc.di.IoDispatcher
import com.rootrecord.rootmc.di.ROOTRECORD_BLOCKNOTES_BASE
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private fun JsonPrimitive?.booleanLike(): Boolean {
    this ?: return false
    booleanOrNull?.let { return it }
    return content.equals("true", ignoreCase = true)
}

private data class MembershipFlags(
    val proUnlocked: Boolean,
    val lifeMember: Boolean,
)

private fun membershipFromJson(root: JsonObject): MembershipFlags {
    val life = root["lifeMember"]?.jsonPrimitive?.booleanLike() == true ||
        root["life_member"]?.jsonPrimitive?.booleanLike() == true
    val pro = life ||
        root["proUnlocked"]?.jsonPrimitive?.booleanLike() == true ||
        root["pro_unlocked"]?.jsonPrimitive?.booleanLike() == true
    return MembershipFlags(proUnlocked = pro, lifeMember = life)
}

private val JsonMedia = "application/json; charset=utf-8".toMediaType()

private val LOGIN_URL = "${ROOTRECORD_BLOCKNOTES_BASE}v1/auth/login"
private val SIGNUP_URL = "${ROOTRECORD_BLOCKNOTES_BASE}v1/auth/signup"
private val LOGOUT_URL = "${ROOTRECORD_BLOCKNOTES_BASE}v1/auth/logout"
private val ME_URL = "${ROOTRECORD_BLOCKNOTES_BASE}v1/me"
private val APP_SESSION_URL = "${ROOTRECORD_BLOCKNOTES_BASE}api/app-session/start"
private val PUSH_TOKEN_URL = "${ROOTRECORD_BLOCKNOTES_BASE}api/me/push-token"
private val DISCORD_LINK_START_URL =
    "${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/realm/minecraft/link/discord/start"
private val DISCORD_SIGNIN_START_URL =
    "${ROOTRECORD_BLOCKNOTES_BASE}api/governance/auth/discord/start"
private val LINK_PREVIEW_URL =
    "${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/realm/minecraft/link/preview"
private val LINK_APP_COMPLETE_URL =
    "${ROOTRECORD_BLOCKNOTES_BASE}api/rootmc/realm/minecraft/link/app/complete"

data class LinkCodePreview(
    val valid: Boolean,
    val minecraftUsername: String?,
    val reason: String?,
)

@Serializable
private data class LinkAppCompleteBody(val code: String)

@Serializable
private data class DiscordOAuthStartBody(
    val code: String? = null,
    val mobile_app: Boolean = true,
    val return_to: String? = null,
)

@Serializable
private data class PushTokenBody(
    val token: String,
    val platform: String = "android",
    val app_id: String = BLOCKNOTES_APP_ID,
)

@Serializable
private data class AppSessionBody(
    val app_id: String = BLOCKNOTES_APP_ID,
    val mode: String = "signed_in",
)

@Serializable
private data class LoginBody(
    val email: String,
    val password: String,
    val app_id: String = BLOCKNOTES_APP_ID,
)

/**
 * Root Record account session (see POST /v1/auth/login on rootrecord-api-rootmc).
 * Uses the same OkHttp stack as feedback (guest id + optional bearer).
 */
@Singleton
class RootRecordAuthRepository @Inject constructor(
    @param:Named("rootrecord") private val http: OkHttpClient,
    private val prefs: RootMcPreferences,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun login(email: String, password: String): Result<Unit> = authenticate(
        url = LOGIN_URL,
        email = email,
        password = password,
        failurePrefix = "sign_in_failed",
    )

    suspend fun createAccount(email: String, password: String): Result<Unit> = authenticate(
        url = SIGNUP_URL,
        email = email,
        password = password,
        failurePrefix = "account_create_failed",
    )

    private suspend fun authenticate(
        url: String,
        email: String,
        password: String,
        failurePrefix: String,
    ): Result<Unit> = withContext(io) {
        runCatching {
            val trimmed = email.trim().lowercase()
            val payload = AppJson.encodeToString(LoginBody.serializer(), LoginBody(trimmed, password))
            val req = Request.Builder()
                .url(url)
                .header("X-RR-App-Id", BLOCKNOTES_APP_ID)
                .post(payload.toRequestBody(JsonMedia))
                .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val root = parseApiObject(text, resp.code)
                if (!resp.isSuccessful) {
                    val detail = root["detail"]?.jsonPrimitive?.content
                    error(detail ?: "${failurePrefix}_${resp.code}")
                }
                val token = root["access_token"]?.jsonPrimitive?.content
                    ?: root["token"]?.jsonPrimitive?.content
                    ?: error("missing_token")
                val mail = root["email"]?.jsonPrimitive?.content?.trim()?.lowercase() ?: trimmed
                val accountId = root["account_id"]?.jsonPrimitive?.content
                val access = membershipFromJson(root)
                prefs.setAuthSession(
                    accessToken = token,
                    email = mail,
                    accountId = accountId,
                    proUnlocked = access.proUnlocked,
                    lifeMember = access.lifeMember,
                    sessionExpiresAtMs = parseExpiresAtMs(root),
                )
            }
        }
    }

    suspend fun logout() {
        withContext(io) {
            try {
                val token = prefs.getAuthAccessToken()
                if (!token.isNullOrBlank()) {
                    val req = Request.Builder()
                        .url(LOGOUT_URL)
                        .post("{}".toRequestBody(JsonMedia))
                        .build()
                    http.newCall(req).execute().close()
                }
            } catch (_: Exception) {
                /* revoke may fail offline; always clear local session */
            }
            prefs.clearAuthSession()
        }
    }

    /** Best-effort POST `/api/me/push-token` (requires signed-in session). */
    suspend fun registerPushToken(token: String): Result<Unit> = withContext(io) {
        runCatching {
            val trimmed = token.trim()
            if (trimmed.length < 20) return@runCatching
            val sessionToken = prefs.getAuthAccessToken()
            if (sessionToken.isNullOrBlank()) return@runCatching
            val payload = AppJson.encodeToString(PushTokenBody.serializer(), PushTokenBody(trimmed))
            val req = Request.Builder()
                .url(PUSH_TOKEN_URL)
                .header("X-RR-App-Id", BLOCKNOTES_APP_ID)
                .post(payload.toRequestBody(JsonMedia))
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("push_token_failed_${resp.code}")
            }
        }
    }

    /** Best-effort POST `/api/app-session/start` → Discord dev channel (once per cold start). */
    suspend fun notifyAppSessionStart() {
        withContext(io) {
            runCatching {
                val payload = AppJson.encodeToString(
                    AppSessionBody.serializer(),
                    AppSessionBody(),
                )
                val req = Request.Builder()
                    .url(APP_SESSION_URL)
                    .header("X-RR-App-Id", BLOCKNOTES_APP_ID)
                    .post(payload.toRequestBody(JsonMedia))
                    .build()
                http.newCall(req).execute().close()
            }
        }
    }

    suspend fun refreshAccountAccess(): Result<Unit> = withContext(io) {
        runCatching {
            val token = prefs.getAuthAccessToken()
            if (token.isNullOrBlank()) return@runCatching
            val req = Request.Builder().url(ME_URL).get().build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.code == 401) {
                    prefs.clearAuthSession()
                    return@runCatching
                }
                if (!resp.isSuccessful) return@runCatching
                val root = runCatching { AppJson.parseToJsonElement(text).jsonObject }.getOrNull()
                    ?: return@runCatching
                val access = membershipFromJson(root)
                prefs.setMembershipFlags(access.proUnlocked, access.lifeMember)
                val mail = root["email"]?.jsonPrimitive?.content?.trim()?.lowercase()
                val accountId = root["account_id"]?.jsonPrimitive?.content
                if (!mail.isNullOrBlank() || !accountId.isNullOrBlank()) {
                    prefs.updateAuthProfile(email = mail, accountId = accountId)
                }
            }
        }
    }

    /** OAuth callback from Discord (mobile deep link). */
    suspend fun establishOAuthSession(accessToken: String): Result<Unit> = withContext(io) {
        runCatching {
            val token = accessToken.trim()
            if (token.length < 20) error("invalid_token")
            prefs.setAuthSession(
                accessToken = token,
                email = "",
                accountId = null,
                proUnlocked = false,
                lifeMember = false,
                sessionExpiresAtMs = com.rootrecord.rootmc.data.remote.JwtUtils.expiresAtEpochMs(token),
            )
            refreshAccountAccess().getOrThrow()
        }
    }

    suspend fun previewLinkCode(code: String): Result<LinkCodePreview> = withContext(io) {
        runCatching {
            val trimmed = code.trim().uppercase()
            if (trimmed.length != 6) {
                return@runCatching LinkCodePreview(valid = false, minecraftUsername = null, reason = "invalid")
            }
            val encoded = java.net.URLEncoder.encode(trimmed, Charsets.UTF_8.name())
            val req = Request.Builder()
                .url("$LINK_PREVIEW_URL?code=$encoded")
                .header("X-RR-App-Id", BLOCKNOTES_APP_ID)
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val root = parseApiObject(text, resp.code)
                if (!resp.isSuccessful) {
                    val detail = root["detail"]?.jsonPrimitive?.content
                    error(detail ?: "link_preview_failed_${resp.code}")
                }
                val valid = root["valid"]?.jsonPrimitive?.booleanOrNull == true
                LinkCodePreview(
                    valid = valid,
                    minecraftUsername = root["minecraft_username"]?.jsonPrimitive?.content,
                    reason = root["reason"]?.jsonPrimitive?.content,
                )
            }
        }
    }

    /** Sign in with a fresh in-game /link code (30-day session). */
    suspend fun loginWithLinkCode(code: String): Result<Unit> = withContext(io) {
        runCatching {
            val trimmed = code.trim().uppercase()
            if (trimmed.length != 6) error("Enter the 6-character code from /link in-game.")
            val payload = AppJson.encodeToString(
                LinkAppCompleteBody.serializer(),
                LinkAppCompleteBody(trimmed),
            )
            val req = Request.Builder()
                .url(LINK_APP_COMPLETE_URL)
                .header("X-RR-App-Id", BLOCKNOTES_APP_ID)
                .post(payload.toRequestBody(JsonMedia))
                .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val root = parseApiObject(text, resp.code)
                if (!resp.isSuccessful) {
                    val detail = root["detail"]?.jsonPrimitive?.content
                    error(detail ?: "link_sign_in_failed_${resp.code}")
                }
                val token = root["access_token"]?.jsonPrimitive?.content
                    ?: root["token"]?.jsonPrimitive?.content
                    ?: error("missing_token")
                val mail = root["email"]?.jsonPrimitive?.content?.trim()?.lowercase().orEmpty()
                val accountId = root["account_id"]?.jsonPrimitive?.content
                prefs.setAuthSession(
                    accessToken = token,
                    email = mail,
                    accountId = accountId,
                    proUnlocked = false,
                    lifeMember = false,
                    sessionExpiresAtMs = parseExpiresAtMs(root),
                )
            }
            refreshAccountAccess().getOrThrow()
        }
    }

    suspend fun ensureValidSession() {
        prefs.ensureValidSession()
    }

    suspend fun startDiscordLink(code: String): Result<String> = withContext(io) {
        runCatching {
            val payload = AppJson.encodeToString(
                DiscordOAuthStartBody.serializer(),
                DiscordOAuthStartBody(code = code.trim().uppercase(), mobile_app = true),
            )
            postDiscordOAuthStart(DISCORD_LINK_START_URL, payload)
        }
    }

    suspend fun startDiscordSignIn(): Result<String> = withContext(io) {
        runCatching {
            val payload = AppJson.encodeToString(
                DiscordOAuthStartBody.serializer(),
                DiscordOAuthStartBody(mobile_app = true),
            )
            postDiscordOAuthStart(DISCORD_SIGNIN_START_URL, payload)
        }
    }

    private fun parseExpiresAtMs(root: JsonObject): Long? {
        val iso = root["expires_at"]?.jsonPrimitive?.content?.trim()
        if (!iso.isNullOrBlank()) {
            runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull()?.let { return it }
        }
        val token = root["access_token"]?.jsonPrimitive?.content
            ?: root["token"]?.jsonPrimitive?.content
        return token?.let { com.rootrecord.rootmc.data.remote.JwtUtils.expiresAtEpochMs(it) }
    }

    private fun postDiscordOAuthStart(url: String, payload: String): String {
        val req = Request.Builder()
            .url(url)
            .header("X-RR-App-Id", BLOCKNOTES_APP_ID)
            .post(payload.toRequestBody(JsonMedia))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val root = parseApiObject(text, resp.code)
            if (!resp.isSuccessful) {
                val detail = root["detail"]?.jsonPrimitive?.content
                error(detail ?: "discord_oauth_failed_${resp.code}")
            }
            return root["authorize_url"]?.jsonPrimitive?.content
                ?: error("missing_authorize_url")
        }
    }

    private fun parseApiObject(text: String, httpCode: Int): JsonObject {
        if (text.isBlank()) {
            error(
                when (httpCode) {
                    503 -> "Sign-in service is busy. Wait a moment and try again."
                    in 500..599 -> "Server error ($httpCode). Try again in a minute."
                    else -> "Empty server response (HTTP $httpCode)."
                },
            )
        }
        if (text.contains("error code: 1102", ignoreCase = true)) {
            error("Sign-in timed out on the server. Try again, or reset your password at rootrecord.info if this keeps happening.")
        }
        val trimmed = text.trim()
        if (trimmed.startsWith("<") || trimmed.startsWith("<!DOCTYPE", ignoreCase = true)) {
            error("Server error ($httpCode). Try again later.")
        }
        return runCatching { AppJson.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: error("Unexpected server response (HTTP $httpCode). Try again.")
    }
}
