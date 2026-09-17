package com.rootrecord.rootmc.data.remote

import com.rootrecord.rootmc.data.local.RootMcPreferences
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class GuestHeaderInterceptor @Inject constructor(
    private val prefs: RootMcPreferences,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val guest = runBlocking { prefs.ensureGuestId() }
        val req = chain.request().newBuilder()
            .header("X-Guest-Id", guest)
            .header(
                "User-Agent",
                "RootMC/1.0 (Root Record; https://rootrecord.online; Android)",
            )
            .build()
        return chain.proceed(req)
    }
}
