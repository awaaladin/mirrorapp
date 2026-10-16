package com.mirror.app.data.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds a Retrofit client for [MirrorBackendApi] pointed at [baseUrl]. [timeoutSeconds] is
 * kept short for the local dev server (so an unreachable local server fails fast and the
 * caller can fall back to the deployed one quickly) and longer for the deployed one (a cold
 * serverless start can genuinely take a few seconds). Manual construction for MVP - no DI
 * framework wired up yet, matching the rest of the app's repositories.
 */
fun createMirrorBackendApi(baseUrl: String, timeoutSeconds: Long): MirrorBackendApi {
    val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }
    val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    val json = Json { ignoreUnknownKeys = true }

    val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    return retrofit.create(MirrorBackendApi::class.java)
}
