package com.rootrecord.kilauea.alerts.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Identifies the client to public federal APIs (NWS policy requires descriptive User-Agent).
 */
class PublicApiInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request().newBuilder()
            .header(
                "User-Agent",
                "KilaueaAlerts/1.0 (Root Record; contact via https://rootrecord.online; Android)",
            )
            .header("Accept", "application/json, application/geo+json, */*")
            .build()
        return chain.proceed(req)
    }
}
