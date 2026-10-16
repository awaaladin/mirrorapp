package com.mirror.app.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

// Mirrors mirrorcentral's app/schemas/auth.py exactly - field names must match its JSON.

@Serializable
data class RegisterRequestDto(
    val email: String,
    val password: String,
    @SerialName("account_type") val accountType: String = "consumer"
)

@Serializable
data class LoginRequestDto(val email: String, val password: String)

@Serializable
data class RefreshRequestDto(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class UserPublicDto(val id: String, val email: String, @SerialName("account_type") val accountType: String)

@Serializable
data class TokenResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    val user: UserPublicDto
)

// Mirrors mirrorcentral's app/schemas/shades.py's ShadePublic.
@Serializable
data class ShadeDto(
    val id: String,
    val name: String,
    @SerialName("hex_color") val hexColor: String,
    val category: String,
    val finish: String
)

// Mirrors mirrorcentral's app/schemas/clients.py exactly - field names must match its JSON.

@Serializable
data class ClientProfileCreateDto(val name: String, val notes: String = "", val tag: String = "self")

@Serializable
data class ClientProfileUpdateDto(val name: String? = null, val notes: String? = null, val tag: String? = null)

@Serializable
data class ClientProfileDto(
    val id: String,
    @SerialName("owner_user_id") val ownerUserId: String,
    val name: String,
    val notes: String,
    val tag: String
)

@Serializable
data class DeviceTokenRequestDto(val token: String, val platform: String)

@Serializable
data class AppVersionDto(
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    @SerialName("download_url") val downloadUrl: String,
    @SerialName("release_notes") val releaseNotes: String,
    @SerialName("is_mandatory") val isMandatory: Boolean
)

// Mirrors mirrorcentral's app/services/showroom.py FaceAnalysis (POST /showroom/analyze).
@Serializable
data class ShowroomSuggestionDto(
    val category: String,
    val name: String,
    val hex: String,
    val reason: String = ""
)

@Serializable
data class FaceAnalysisDto(
    @SerialName("skin_depth") val skinDepth: Int,
    val undertone: String,
    @SerialName("skin_hex") val skinHex: String,
    @SerialName("lip_hex") val lipHex: String,
    @SerialName("face_shape") val faceShape: String,
    @SerialName("photo_warnings") val photoWarnings: List<String> = emptyList(),
    val suggestions: List<ShowroomSuggestionDto> = emptyList(),
    val summary: String = ""
)

interface MirrorBackendApi {
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequestDto): Response<TokenResponseDto>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequestDto): Response<TokenResponseDto>

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequestDto): Response<TokenResponseDto>

    @GET("shades")
    suspend fun listShades(
        @Query("category") category: String? = null,
        @Query("q") query: String? = null
    ): Response<List<ShadeDto>>

    @POST("clients")
    suspend fun createClient(
        @Header("Authorization") authorization: String,
        @Body request: ClientProfileCreateDto
    ): Response<ClientProfileDto>

    @GET("clients")
    suspend fun listClients(@Header("Authorization") authorization: String): Response<List<ClientProfileDto>>

    @PATCH("clients/{clientId}")
    suspend fun updateClient(
        @Header("Authorization") authorization: String,
        @Path("clientId") clientId: String,
        @Body request: ClientProfileUpdateDto
    ): Response<ClientProfileDto>

    @DELETE("clients/{clientId}")
    suspend fun deleteClient(
        @Header("Authorization") authorization: String,
        @Path("clientId") clientId: String
    ): Response<Unit>

    @POST("devices/register")
    suspend fun registerDeviceToken(@Body request: DeviceTokenRequestDto): Response<Unit>

    @GET("app/version")
    suspend fun getLatestAppVersion(@Query("platform") platform: String = "android"): Response<AppVersionDto>

    @Multipart
    @POST("showroom/analyze")
    suspend fun analyzeFace(
        @Header("Authorization") authorization: String,
        @Part image: MultipartBody.Part
    ): Response<FaceAnalysisDto>
}
