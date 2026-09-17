package com.rootrecord.kilauea.alerts.data.remote

import com.rootrecord.kilauea.alerts.data.local.KilaueaPreferences
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class GuestHeaderInterceptor @Inject constructor(
    private val prefs: KilaueaPreferences,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val guest = runBlocking { prefs.ensureGuestId() }
        val req = chain.request().newBuilder()
            .header("X-Guest-Id", guest)
            .header(
                "User-Agent",
                "KilaueaAlerts/1.0 (Root Record; https://rootrecord.online; Android)",
            )
            .build()
        return chain.proceed(req)
    }
}
