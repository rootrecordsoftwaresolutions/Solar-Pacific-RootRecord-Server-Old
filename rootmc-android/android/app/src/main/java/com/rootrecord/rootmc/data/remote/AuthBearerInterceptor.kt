package com.rootrecord.rootmc.data.remote

import com.rootrecord.rootmc.data.local.RootMcPreferences
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Adds `Authorization: Bearer` for authenticated Root Record API calls.
 * Skipped on `/v1/auth/login` and `/v1/auth/signup` so credentials are not sent with stale tokens.
 */
class AuthBearerInterceptor @Inject constructor(
    private val prefs: RootMcPreferences,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val url = req.url.toString()
        if (url.contains("/v1/auth/login") || url.contains("/v1/auth/signup") ||
            url.contains("/link/app/complete")
        ) {
            return chain.proceed(req)
        }
        val token = runBlocking { prefs.getAuthAccessToken() }
        val next = if (token.isNullOrBlank()) {
            req
        } else {
            req.newBuilder().header("Authorization", "Bearer $token").build()
        }
        return chain.proceed(next)
    }
}
