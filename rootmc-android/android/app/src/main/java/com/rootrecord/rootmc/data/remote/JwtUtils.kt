package com.rootrecord.rootmc.data.remote

import android.util.Base64
import org.json.JSONObject

object JwtUtils {
    /** Reads `exp` from JWT payload without verifying signature (client-side cache hint only). */
    fun expiresAtEpochMs(token: String): Long? {
        val parts = token.split('.')
        if (parts.size < 2) return null
        return runCatching {
            val decoded = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val exp = JSONObject(String(decoded)).optLong("exp", 0L)
            if (exp > 0L) exp * 1000L else null
        }.getOrNull()
    }
}
