package com.mirror.app.data.auth

import com.mirror.app.core.result.AppError
import com.mirror.app.core.result.AppResult
import com.mirror.app.data.network.AuthRequestDto
import com.mirror.app.data.network.AuthResponseDto
import com.mirror.app.data.network.ErrorResponseDto
import com.mirror.app.data.network.MirrorBackendApi
import com.mirror.app.domain.model.AuthUser
import com.mirror.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException

/**
 * Talks to the custom mirror-backend Ktor server (a sibling project, not part of this Gradle
 * build) rather than a third-party auth provider - a small self-hosted signup/login/JWT API,
 * following the same [AuthRepository] boundary so the rest of the app doesn't care which
 * backend is behind it.
 */
class BackendAuthRepository(
    private val api: MirrorBackendApi,
    private val sessionStore: SessionStore
) : AuthRepository {

    private val currentUser = MutableStateFlow(sessionStore.loadUser())
    private val json = Json { ignoreUnknownKeys = true }

    override fun observeCurrentUser(): Flow<AuthUser?> = currentUser.asStateFlow()

    override suspend fun signUp(email: String, password: String): AppResult<AuthUser> =
        callAuthEndpoint(isSignUp = true) { api.signUp(AuthRequestDto(email, password)) }

    override suspend fun logIn(email: String, password: String): AppResult<AuthUser> =
        callAuthEndpoint(isSignUp = false) { api.logIn(AuthRequestDto(email, password)) }

    override fun logOut() {
        sessionStore.clear()
        currentUser.value = null
    }

    private suspend fun callAuthEndpoint(
        isSignUp: Boolean,
        call: suspend () -> Response<AuthResponseDto>
    ): AppResult<AuthUser> {
        val response = try {
            call()
        } catch (e: IOException) {
            return AppResult.Failure(AppError.NetworkUnavailable)
        }

        if (response.isSuccessful) {
            val body = response.body()
                ?: return AppResult.Failure(AppError.AuthFailed("The server returned an empty response."))
            val user = AuthUser(uid = body.userId.toString(), email = body.email)
            sessionStore.save(body.token, user)
            currentUser.value = user
            return AppResult.Success(user)
        }

        val message = parseErrorMessage(response)
        val error = when (response.code()) {
            401 -> AppError.InvalidCredentials
            409 -> AppError.EmailAlreadyInUse
            else -> AppError.AuthFailed(message)
        }
        return AppResult.Failure(error)
    }

    private fun parseErrorMessage(response: Response<*>): String {
        val errorBody = response.errorBody()?.string()
        return try {
            errorBody?.let { json.decodeFromString<ErrorResponseDto>(it).error }
                ?: "Something went wrong. Please try again."
        } catch (e: Exception) {
            "Something went wrong. Please try again."
        }
    }
}
