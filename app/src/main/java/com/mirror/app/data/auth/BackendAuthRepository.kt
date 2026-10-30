package com.mirror.app.data.auth

import com.mirror.app.core.result.AppError
import com.mirror.app.core.result.AppResult
import com.mirror.app.data.network.LoginRequestDto
import com.mirror.app.data.network.MirrorBackendApi
import com.mirror.app.data.network.RegisterRequestDto
import com.mirror.app.data.network.TokenResponseDto
import com.mirror.app.domain.model.AuthUser
import com.mirror.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response
import java.io.IOException

/**
 * Talks to mirrorcentral (the custom Python/FastAPI backend - a sibling project, not part of
 * this Gradle build) rather than a third-party auth provider, behind the same [AuthRepository]
 * boundary so the rest of the app doesn't care which backend answers.
 */
class BackendAuthRepository(
    private val api: MirrorBackendApi,
    private val sessionStore: SessionStore
) : AuthRepository {

    private val currentUser = MutableStateFlow(sessionStore.loadUser())
    private val json = Json { ignoreUnknownKeys = true }

    override fun observeCurrentUser(): Flow<AuthUser?> = currentUser.asStateFlow()

    override suspend fun signUp(email: String, password: String): AppResult<AuthUser> =
        authenticate { api.register(RegisterRequestDto(email, password)) }

    override suspend fun logIn(email: String, password: String): AppResult<AuthUser> =
        authenticate { api.login(LoginRequestDto(email, password)) }

    override fun logOut() {
        sessionStore.clear()
        currentUser.value = null
    }

    private suspend fun authenticate(call: suspend () -> Response<TokenResponseDto>): AppResult<AuthUser> {
        val response = try {
            call()
        } catch (e: IOException) {
            return AppResult.Failure(AppError.NetworkUnavailable)
        }
        return handleResponse(response)
    }

    private fun handleResponse(response: Response<TokenResponseDto>): AppResult<AuthUser> {
        if (response.isSuccessful) {
            val body = response.body()
                ?: return AppResult.Failure(AppError.AuthFailed("The server returned an empty response."))
            val user = AuthUser(uid = body.user.id, email = body.user.email)
            sessionStore.save(body.accessToken, body.refreshToken, user)
            currentUser.value = user
            return AppResult.Success(user)
        }

        val error = when (response.code()) {
            401 -> AppError.InvalidCredentials
            409 -> AppError.EmailAlreadyInUse
            422 -> AppError.WeakPassword
            else -> AppError.AuthFailed(parseErrorMessage(response))
        }
        return AppResult.Failure(error)
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
