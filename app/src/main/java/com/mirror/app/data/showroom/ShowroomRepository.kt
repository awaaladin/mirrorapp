package com.mirror.app.data.showroom

import com.mirror.app.core.result.AppError
import com.mirror.app.core.result.AppResult
import com.mirror.app.data.auth.SessionStore
import com.mirror.app.data.auth.refreshAccessToken
import com.mirror.app.data.network.FaceAnalysisDto
import com.mirror.app.data.network.MirrorBackendApi
import com.mirror.app.domain.model.FaceAnalysis
import com.mirror.app.domain.model.ShadeSuggestion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.IOException

/**
 * Sends a portrait to mirrorcentral's Gemini-backed /showroom/analyze and returns the analysis.
 * The Gemini key lives on the server only - the app never holds it. The access token is
 * refreshed once on a 401 since the
 * 30-minute access JWT is likely to have lapsed by the time someone opens Showroom.
 */
class ShowroomRepository(
    private val api: MirrorBackendApi,
    private val sessionStore: SessionStore
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun analyze(jpegBytes: ByteArray): AppResult<FaceAnalysis> {
        var token = sessionStore.loadAccessToken()
            ?: return AppResult.Failure(AppError.AuthFailed("Sign in to use Showroom."))
        val part = MultipartBody.Part.createFormData(
            "image", "face.jpg", jpegBytes.toRequestBody("image/jpeg".toMediaType())
        )

        var response = send(token, part) ?: return AppResult.Failure(AppError.NetworkUnavailable)
        if (response.code() == 401) {
            token = refreshAccessToken(api, sessionStore)
                ?: return AppResult.Failure(AppError.AuthFailed("Your session expired. Please sign in again."))
            response = send(token, part) ?: return AppResult.Failure(AppError.NetworkUnavailable)
        }
        if (!response.isSuccessful) {
            return AppResult.Failure(AppError.RequestFailed(parseErrorMessage(response)))
        }
        val body = response.body() ?: return AppResult.Failure(AppError.RequestFailed("Empty response."))
        return AppResult.Success(body.toDomain())
    }

    private suspend fun send(token: String, part: MultipartBody.Part): Response<FaceAnalysisDto>? =
        try {
            api.analyzeFace("Bearer $token", part)
        } catch (e: IOException) {
            null
        }

    private fun parseErrorMessage(response: Response<*>): String {
        val fallback = when (response.code()) {
            413 -> "That photo is too large."
            403 -> "Showroom is part of Mirror Pro."
            429 -> "You've used this month's Showroom analyses."
            502, 503 -> "Showroom analysis is unavailable right now. Please try again shortly."
            else -> "Something went wrong. Please try again."
        }
        val errorBody = response.errorBody()?.string() ?: return fallback
        return try {
            (json.parseToJsonElement(errorBody).jsonObject["detail"] as? JsonPrimitive)?.content ?: fallback
        } catch (e: Exception) {
            fallback
        }
    }
}

private fun FaceAnalysisDto.toDomain() = FaceAnalysis(
    skinDepth = skinDepth,
    undertone = undertone,
    skinHex = skinHex,
    lipHex = lipHex,
    faceShape = faceShape,
    photoWarnings = photoWarnings,
    suggestions = suggestions.map { ShadeSuggestion(it.category, it.name, it.hex, it.reason) },
    summary = summary
)
