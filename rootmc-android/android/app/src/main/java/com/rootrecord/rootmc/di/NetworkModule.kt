package com.rootrecord.rootmc.di

import com.rootrecord.rootmc.data.remote.AuthBearerInterceptor
import com.rootrecord.rootmc.data.remote.GuestHeaderInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

// RootMC realm API (Worker: rootmc-api at api.rootmc.net).
const val ROOTMC_API_BASE = "https://api.rootmc.net/"

/** @deprecated use [ROOTMC_API_BASE] */
const val ROOTRECORD_BLOCKNOTES_BASE = ROOTMC_API_BASE

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    @Named("rootrecord")
    fun provideRootRecordOkHttp(
        guestHeaderInterceptor: GuestHeaderInterceptor,
        authBearerInterceptor: AuthBearerInterceptor,
    ): OkHttpClient {
        val log = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(guestHeaderInterceptor)
            .addInterceptor(authBearerInterceptor)
            .addInterceptor(log)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** Public Mojang APIs — no Root Record auth headers. */
    @Provides
    @Singleton
    @Named("mojang")
    fun provideMojangOkHttp(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
