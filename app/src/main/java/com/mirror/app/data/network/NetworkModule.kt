package com.mirror.app.data.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Builds the Retrofit client for [MirrorBackendApi]. Manual construction for MVP - no DI
 *  framework wired up yet, matching the rest of the app's repositories. */
fun createMirrorBackendApi(): MirrorBackendApi {
    val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }
    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    val json = Json { ignoreUnknownKeys = true }

    val retrofit = Retrofit.Builder()
        .baseUrl(NetworkConfig.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    return retrofit.create(MirrorBackendApi::class.java)
}
