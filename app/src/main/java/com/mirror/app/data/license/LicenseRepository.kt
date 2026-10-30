package com.mirror.app.data.license

import com.mirror.app.data.auth.SessionStore
import com.mirror.app.data.auth.refreshAccessToken
import com.mirror.app.data.network.LicenseRequestDto
import com.mirror.app.data.network.MirrorBackendApi
import com.mirror.app.domain.model.AuthUser
import com.mirror.app.domain.model.ProPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.Response
import java.io.IOException

/**
 * Keeps the signed license from POST /billing/license. [plan] comes from the stored license, so
 * it still works offline; a failed check-in keeps that license, and it lapses by itself if the
 * phone stays offline past its expiry. This only decides what the app shows - the Pro routes
 * check the subscription on the server regardless.
 */
class LicenseRepository(
    private val api: MirrorBackendApi,
    private val sessionStore: SessionStore,
    private val verifier: LicenseVerifier = LicenseVerifier()
) {
    private val _plan = MutableStateFlow(ProPlan.FREE)
    val plan: StateFlow<ProPlan> = _plan.asStateFlow()

    suspend fun checkIn(user: AuthUser?) {
        _plan.value = storedPlan(user)
        if (user == null) return
        val response = requestLicense() ?: return
        if (!response.isSuccessful) return
        sessionStore.saveLicense(response.body()?.license)
        _plan.value = storedPlan(user)
    }

    private fun storedPlan(user: AuthUser?): ProPlan {
        val license = sessionStore.loadLicense()
        if (user == null || license == null) return ProPlan.FREE
        return verifier.planFrom(license, user.uid, sessionStore.deviceId())
    }

    private suspend fun requestLicense(): Response<com.mirror.app.data.network.LicenseResponseDto>? {
        val token = sessionStore.loadAccessToken() ?: return null
        val request = LicenseRequestDto(sessionStore.deviceId())
        return try {
            val response = api.getLicense("Bearer $token", request)
            if (response.code() != 401) return response
            val fresh = refreshAccessToken(api, sessionStore) ?: return null
            api.getLicense("Bearer $fresh", request)
        } catch (e: IOException) {
            null
        }
    }
}
