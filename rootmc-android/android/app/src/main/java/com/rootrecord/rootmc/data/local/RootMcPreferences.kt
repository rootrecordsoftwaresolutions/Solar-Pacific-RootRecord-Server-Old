package com.rootrecord.rootmc.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import com.rootrecord.rootmc.domain.model.MembershipTier
import com.rootrecord.rootmc.domain.model.MinecraftProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rootmc_prefs")

@Singleton
class RootMcPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val ds get() = context.dataStore

    val guestId: Flow<String> = ds.data.map { p -> p[GUEST_ID] ?: "" }

    val authSignedIn: Flow<Boolean> = ds.data.map { p ->
        val token = p[AUTH_ACCESS_TOKEN]
        if (token.isNullOrBlank()) return@map false
        val expires = p[AUTH_SESSION_EXPIRES_AT] ?: 0L
        expires == 0L || System.currentTimeMillis() < expires
    }

    val authSessionExpiresAt: Flow<Long?> = ds.data.map { p ->
        if (p[AUTH_ACCESS_TOKEN].isNullOrBlank()) null else p[AUTH_SESSION_EXPIRES_AT]
    }

    val authEmail: Flow<String?> = ds.data.map { p ->
        if (p[AUTH_ACCESS_TOKEN].isNullOrBlank()) null else p[AUTH_EMAIL]
    }

    val authAccountId: Flow<String?> = ds.data.map { p ->
        if (p[AUTH_ACCESS_TOKEN].isNullOrBlank()) null else p[AUTH_ACCOUNT_ID]
    }

    /** Pro or Lifetime while signed in — hides ads and unlocks member perks. */
    val authProUnlocked: Flow<Boolean> = ds.data.map { p ->
        !p[AUTH_ACCESS_TOKEN].isNullOrBlank() &&
            (p[AUTH_PRO_UNLOCKED] == true || p[AUTH_LIFE_MEMBER] == true)
    }

    val authLifeMember: Flow<Boolean> = ds.data.map { p ->
        !p[AUTH_ACCESS_TOKEN].isNullOrBlank() && p[AUTH_LIFE_MEMBER] == true
    }

    val membershipTier: Flow<MembershipTier> = combine(
        authSignedIn,
        authProUnlocked,
        authLifeMember,
    ) { signedIn, pro, life ->
        when {
            !signedIn -> MembershipTier.Guest
            life -> MembershipTier.Lifetime
            pro -> MembershipTier.Pro
            else -> MembershipTier.SignedInFree
        }
    }

    val themeMode: Flow<String> = ds.data.map { p ->
        p[THEME_MODE]?.takeIf { it in THEME_MODES } ?: THEME_SYSTEM
    }

    val analyticsOptIn: Flow<Boolean> = ds.data.map { it[ANALYTICS_OPT_IN] == true }

    val bootstrapComplete: Flow<Boolean> = ds.data.map { it[BOOTSTRAP_COMPLETE] == true }

    val defaultWorldId: Flow<Long?> = ds.data.map { p ->
        p[DEFAULT_WORLD_ID]?.takeIf { it > 0L }
    }

    suspend fun getAuthAccessToken(): String? {
        val prefs = ds.data.first()
        val token = prefs[AUTH_ACCESS_TOKEN]?.takeIf { it.isNotBlank() } ?: return null
        val expires = prefs[AUTH_SESSION_EXPIRES_AT] ?: 0L
        if (expires > 0L && System.currentTimeMillis() >= expires) {
            clearAuthSession()
            return null
        }
        return token
    }

    suspend fun ensureValidSession() {
        getAuthAccessToken()
    }

    suspend fun setAuthSession(
        accessToken: String,
        email: String,
        accountId: String?,
        proUnlocked: Boolean,
        lifeMember: Boolean = false,
        sessionExpiresAtMs: Long? = null,
    ) {
        val expires = sessionExpiresAtMs
            ?: com.rootrecord.rootmc.data.remote.JwtUtils.expiresAtEpochMs(accessToken)
        ds.edit {
            it[AUTH_ACCESS_TOKEN] = accessToken
            it[AUTH_EMAIL] = email.trim().lowercase()
            if (accountId.isNullOrBlank()) it.remove(AUTH_ACCOUNT_ID) else it[AUTH_ACCOUNT_ID] = accountId
            it[AUTH_LIFE_MEMBER] = lifeMember
            it[AUTH_PRO_UNLOCKED] = proUnlocked || lifeMember
            if (expires != null && expires > 0L) {
                it[AUTH_SESSION_EXPIRES_AT] = expires
            } else {
                it.remove(AUTH_SESSION_EXPIRES_AT)
            }
        }
    }

    suspend fun clearAuthSession() {
        ds.edit {
            it.remove(AUTH_ACCESS_TOKEN)
            it.remove(AUTH_EMAIL)
            it.remove(AUTH_ACCOUNT_ID)
            it.remove(AUTH_PRO_UNLOCKED)
            it.remove(AUTH_LIFE_MEMBER)
            it.remove(AUTH_SESSION_EXPIRES_AT)
        }
    }

    suspend fun setMembershipFlags(proUnlocked: Boolean, lifeMember: Boolean) {
        ds.edit { p ->
            if (p[AUTH_ACCESS_TOKEN].isNullOrBlank()) return@edit
            p[AUTH_LIFE_MEMBER] = lifeMember
            p[AUTH_PRO_UNLOCKED] = proUnlocked || lifeMember
        }
    }

    suspend fun updateAuthProfile(email: String? = null, accountId: String? = null) {
        ds.edit { p ->
            if (p[AUTH_ACCESS_TOKEN].isNullOrBlank()) return@edit
            email?.takeIf { it.isNotBlank() }?.let { p[AUTH_EMAIL] = it.trim().lowercase() }
            when {
                accountId.isNullOrBlank() -> Unit
                else -> p[AUTH_ACCOUNT_ID] = accountId
            }
        }
    }

    suspend fun ensureGuestId(): String {
        val existing = ds.data.first()[GUEST_ID]?.trim().orEmpty()
        if (existing.isNotEmpty()) return existing
        val g = "g_${java.util.UUID.randomUUID().toString().replace("-", "").take(24)}"
        ds.edit { it[GUEST_ID] = g }
        return g
    }

    suspend fun setThemeMode(mode: String) {
        ds.edit { it[THEME_MODE] = mode.takeIf { candidate -> candidate in THEME_MODES } ?: THEME_SYSTEM }
    }

    suspend fun setAnalyticsOptIn(v: Boolean) {
        ds.edit { it[ANALYTICS_OPT_IN] = v }
    }

    suspend fun setBootstrapComplete(v: Boolean) {
        ds.edit { it[BOOTSTRAP_COMPLETE] = v }
    }

    suspend fun setDefaultWorldId(worldId: Long) {
        ds.edit { it[DEFAULT_WORLD_ID] = worldId }
    }

    suspend fun incrementAdActionCount(): Int {
        var next = 0
        ds.edit {
            next = (it[AD_ACTION_COUNT] ?: 0) + 1
            it[AD_ACTION_COUNT] = next
        }
        return next
    }

    suspend fun resetAdActionCount() {
        ds.edit { it[AD_ACTION_COUNT] = 0 }
    }

    val appOpenCount: Flow<Int> = ds.data.map { it[APP_OPEN_COUNT] ?: 0 }

    val minecraftProfile: Flow<MinecraftProfile?> = ds.data.map { p ->
        val username = p[MINECRAFT_USERNAME]?.trim().orEmpty()
        val uuid = p[MINECRAFT_UUID]?.trim().orEmpty()
        if (username.isBlank() || uuid.isBlank()) return@map null
        MinecraftProfile(
            username = username,
            uuid = uuid,
            skinUrl = p[MINECRAFT_SKIN_URL]?.trim()?.takeIf { it.isNotBlank() },
            skinSlim = p[MINECRAFT_SKIN_SLIM] == true,
        )
    }

    suspend fun incrementAppOpenCount(): Int {
        var next = 0
        ds.edit {
            next = (it[APP_OPEN_COUNT] ?: 0) + 1
            it[APP_OPEN_COUNT] = next
        }
        return next
    }

    suspend fun setMinecraftProfile(
        username: String,
        uuid: String,
        skinUrl: String?,
        skinSlim: Boolean,
    ) {
        ds.edit {
            it[MINECRAFT_USERNAME] = username.trim()
            it[MINECRAFT_UUID] = MinecraftProfile.formatUuid(uuid)
            if (skinUrl.isNullOrBlank()) it.remove(MINECRAFT_SKIN_URL) else it[MINECRAFT_SKIN_URL] = skinUrl
            it[MINECRAFT_SKIN_SLIM] = skinSlim
        }
    }

    suspend fun clearMinecraftProfile() {
        ds.edit {
            it.remove(MINECRAFT_USERNAME)
            it.remove(MINECRAFT_UUID)
            it.remove(MINECRAFT_SKIN_URL)
            it.remove(MINECRAFT_SKIN_SLIM)
        }
    }

    val sharedWorldIds: Flow<Set<Long>> = ds.data.map { prefs ->
        prefs[SHARED_WORLD_IDS].orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    }

    suspend fun setWorldSharedOnProfile(worldId: Long, shared: Boolean) {
        ds.edit { prefs ->
            val current = prefs[SHARED_WORLD_IDS].orEmpty().toMutableSet()
            val key = worldId.toString()
            if (shared) current.add(key) else current.remove(key)
            prefs[SHARED_WORLD_IDS] = current
        }
    }

    suspend fun isWorldSharedOnProfile(worldId: Long): Boolean {
        val ids = ds.data.first()[SHARED_WORLD_IDS].orEmpty()
        return worldId.toString() in ids
    }

    /** One automatic local DB wipe when migrations/schema fail on startup. */
    suspend fun markDatabaseResetIfAllowed(): Boolean {
        if (ds.data.first()[DB_RESET_TRIED] == true) return false
        ds.edit {
            it[DB_RESET_TRIED] = true
            it[BOOTSTRAP_COMPLETE] = false
        }
        return true
    }

    suspend fun clearDatabaseResetFlag() {
        ds.edit { it.remove(DB_RESET_TRIED) }
    }

    val cloudSyncUpdatedAt: Flow<Long> = ds.data.map { it[CLOUD_SYNC_UPDATED_AT] ?: 0L }

    val cloudSyncError: Flow<String?> = ds.data.map { it[CLOUD_SYNC_ERROR] }

    suspend fun getCloudSyncUpdatedAt(): Long = ds.data.first()[CLOUD_SYNC_UPDATED_AT] ?: 0L

    suspend fun setCloudSyncUpdatedAt(epochMs: Long) {
        ds.edit { it[CLOUD_SYNC_UPDATED_AT] = epochMs.coerceAtLeast(0L) }
    }

    suspend fun setCloudSyncError(message: String?) {
        ds.edit {
            if (message.isNullOrBlank()) it.remove(CLOUD_SYNC_ERROR) else it[CLOUD_SYNC_ERROR] = message.take(500)
        }
    }

    companion object {
        private val GUEST_ID = stringPreferencesKey("guest_id")
        private val AUTH_ACCESS_TOKEN = stringPreferencesKey("auth_access_token")
        private val AUTH_EMAIL = stringPreferencesKey("auth_email")
        private val AUTH_ACCOUNT_ID = stringPreferencesKey("auth_account_id")
        private val AUTH_PRO_UNLOCKED = booleanPreferencesKey("auth_pro_unlocked")
        private val AUTH_LIFE_MEMBER = booleanPreferencesKey("auth_life_member")
        private val AUTH_SESSION_EXPIRES_AT = longPreferencesKey("auth_session_expires_at")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val ANALYTICS_OPT_IN = booleanPreferencesKey("analytics_opt_in")
        private val BOOTSTRAP_COMPLETE = booleanPreferencesKey("bootstrap_complete")
        private val DEFAULT_WORLD_ID = longPreferencesKey("default_world_id")
        private val AD_ACTION_COUNT = intPreferencesKey("ad_action_count")
        private val APP_OPEN_COUNT = intPreferencesKey("app_open_count")
        private val MINECRAFT_USERNAME = stringPreferencesKey("minecraft_username")
        private val MINECRAFT_UUID = stringPreferencesKey("minecraft_uuid")
        private val MINECRAFT_SKIN_URL = stringPreferencesKey("minecraft_skin_url")
        private val MINECRAFT_SKIN_SLIM = booleanPreferencesKey("minecraft_skin_slim")
        private val SHARED_WORLD_IDS = stringSetPreferencesKey("shared_world_ids")
        private val DB_RESET_TRIED = booleanPreferencesKey("db_reset_tried")
        private val CLOUD_SYNC_UPDATED_AT = longPreferencesKey("cloud_sync_updated_at")
        private val CLOUD_SYNC_ERROR = stringPreferencesKey("cloud_sync_error")

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        val THEME_MODES = setOf(THEME_SYSTEM, THEME_LIGHT, THEME_DARK)
    }
}
