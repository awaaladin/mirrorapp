package com.mirror.app.data.clients

import com.mirror.app.core.result.AppError
import com.mirror.app.core.result.AppResult
import com.mirror.app.data.network.ClientProfileCreateDto
import com.mirror.app.data.network.ClientProfileDto
import com.mirror.app.data.network.ClientProfileUpdateDto
import com.mirror.app.data.network.MirrorBackendApi
import com.mirror.app.domain.model.ClientProfile
import com.mirror.app.domain.model.ClientTag
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response
import java.io.IOException

/**
 * Client Mode data, synced with mirrorcentral's /clients API (Pro/Business accounts only).
 * Same local-then-deployed fallback pattern as ShadeRepository/BackendAuthRepository, but every
 * call here needs a bearer token since /clients is auth-gated (unlike the public shade catalog).
 */
class ClientRepository(
    private val localApi: MirrorBackendApi,
    private val deployedApi: MirrorBackendApi
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listClients(accessToken: String): AppResult<List<ClientProfile>> =
        withFallback { api -> api.listClients(bearer(accessToken)) }.mapSuccess { dtos ->
            dtos.map { it.toDomain() }
        }

    suspend fun createClient(accessToken: String, name: String, notes: String, tag: ClientTag): AppResult<ClientProfile> =
        withFallback { api ->
            api.createClient(bearer(accessToken), ClientProfileCreateDto(name, notes, tag.name.lowercase()))
        }.mapSuccess { it.toDomain() }

    suspend fun deleteClient(accessToken: String, clientId: String): AppResult<Unit> =
        withFallback { api -> api.deleteClient(bearer(accessToken), clientId) }.mapSuccess { }

    private fun bearer(accessToken: String) = "Bearer $accessToken"

    private suspend fun <T> withFallback(call: suspend (MirrorBackendApi) -> Response<T>): AppResult<T> {
        val response = try {
            call(localApi)
        } catch (e: IOException) {
            try {
                call(deployedApi)
            } catch (e2: IOException) {
                return AppResult.Failure(AppError.NetworkUnavailable)
            }
        }
        if (!response.isSuccessful) {
            return AppResult.Failure(AppError.RequestFailed(parseErrorMessage(response)))
        }
        @Suppress("UNCHECKED_CAST")
        return AppResult.Success(response.body() as T ?: Unit as T)
    }

    /** FastAPI's error body is `{"detail": "message"}` for a plain HTTPException, or
     *  `{"detail": [{"msg": "...", ...}, ...]}` for a 422 Pydantic validation error. */
    private fun parseErrorMessage(response: Response<*>): String {
        val errorBody = response.errorBody()?.string() ?: return "Something went wrong. Please try again."
        return try {
            val detail = json.parseToJsonElement(errorBody).jsonObject["detail"]
            when (detail) {
                is JsonPrimitive -> detail.content
                is JsonArray -> detail.joinToString("; ") { item ->
                    item.jsonObject["msg"]?.jsonPrimitive?.content ?: item.toString()
                }
                else -> "Something went wrong. Please try again."
            }
        } catch (e: Exception) {
            "Something went wrong. Please try again."
        }
    }
}

private inline fun <T, R> AppResult<T>.mapSuccess(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

private fun ClientProfileDto.toDomain() = ClientProfile(id = id, name = name, notes = notes, tag = ClientTag.fromWire(tag))
