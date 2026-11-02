package com.mirror.app.data.usage

import com.mirror.app.core.result.AppError
import com.mirror.app.core.result.AppResult
import com.mirror.app.data.auth.SessionStore
import com.mirror.app.data.auth.withFreshToken
import com.mirror.app.data.network.MirrorBackendApi
import com.mirror.app.data.network.PhotoClaimDto
import com.mirror.app.data.network.UsageDto
import com.mirror.app.data.network.VerifyCodeDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The outcome of asking mirrorcentral whether a new photo may start (POST /usage/photo). */
sealed interface PhotoCheck {
    data object Allowed : PhotoCheck
    data object NeedsVerification : PhotoCheck
    data class LimitReached(val limit: Int) : PhotoCheck
    data object Offline : PhotoCheck
    data object Failed : PhotoCheck
}

/**
 * Free accounts' monthly allowances and the email confirmation they need. Makeup is painted on the
 * phone, so starting a new photo asks the server first; that's what makes the free limit count.
 * [verificationRequested] is set whenever the server says the email isn't confirmed yet, and the
 * code dialog (see MirrorNavHost) follows it.
 */
class UsageRepository(
    private val api: MirrorBackendApi,
    private val sessionStore: SessionStore
) {
    private val _verificationRequested = MutableStateFlow(false)
    val verificationRequested: StateFlow<Boolean> = _verificationRequested.asStateFlow()

    private val _usage = MutableStateFlow<UsageDto?>(null)
    val usage: StateFlow<UsageDto?> = _usage.asStateFlow()

    fun requestVerification() {
        _verificationRequested.value = true
    }

    fun dismissVerification() {
        _verificationRequested.value = false
    }

    suspend fun refreshUsage() {
        val response = withFreshToken(api, sessionStore) { api.getUsage(it) } ?: return
        if (response.isSuccessful) _usage.value = response.body()
    }

    suspend fun checkPhoto(photoId: String): PhotoCheck {
        val response = withFreshToken(api, sessionStore) { api.claimPhoto(it, PhotoClaimDto(photoId)) }
            ?: return PhotoCheck.Offline
        val body = response.body()
        if (!response.isSuccessful || body == null) return PhotoCheck.Failed
        return when {
            body.allowed -> PhotoCheck.Allowed
            body.reason == "verify_email" -> PhotoCheck.NeedsVerification.also { requestVerification() }
            else -> PhotoCheck.LimitReached(body.photos.limit ?: 0)
        }.also { refreshUsage() }
    }

    suspend fun sendCode(): AppResult<Unit> {
        val response = withFreshToken(api, sessionStore) { api.sendEmailCode(it) }
            ?: return AppResult.Failure(AppError.NetworkUnavailable)
        return when {
            response.isSuccessful -> AppResult.Success(Unit)
            response.code() == 429 -> AppResult.Failure(AppError.RequestFailed("We've sent a few codes already. Check your inbox and spam, or try again in an hour."))
            else -> AppResult.Failure(AppError.RequestFailed("Couldn't send a code. Please try again."))
        }
    }

    suspend fun verify(code: String): AppResult<Unit> {
        val response = withFreshToken(api, sessionStore) { api.verifyEmailCode(it, VerifyCodeDto(code)) }
            ?: return AppResult.Failure(AppError.NetworkUnavailable)
        if (!response.isSuccessful) {
            return AppResult.Failure(AppError.RequestFailed("That code is wrong or has expired. Ask for a new one."))
        }
        _verificationRequested.value = false
        refreshUsage()
        return AppResult.Success(Unit)
    }
}
