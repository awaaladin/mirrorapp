package com.mirror.app.data.network

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

@Serializable
data class AuthRequestDto(val email: String, val password: String)

@Serializable
data class AuthResponseDto(val token: String, val userId: Int, val email: String)

@Serializable
data class ErrorResponseDto(val error: String)

interface MirrorBackendApi {
    @POST("auth/signup")
    suspend fun signUp(@Body request: AuthRequestDto): Response<AuthResponseDto>

    @POST("auth/login")
    suspend fun logIn(@Body request: AuthRequestDto): Response<AuthResponseDto>

    @GET("auth/me")
    suspend fun me(@Header("Authorization") bearerToken: String): Response<AuthResponseDto>
}
