package com.mirror.app.data.auth

import com.mirror.app.data.network.MirrorBackendApi
import com.mirror.app.data.network.RefreshRequestDto
import com.mirror.app.domain.model.AuthUser
import java.io.IOException

/** Trades the stored refresh token for a new access token, or null if that isn't possible. */
suspend fun refreshAccessToken(api: MirrorBackendApi, sessionStore: SessionStore): String? {
    val refreshToken = sessionStore.loadRefreshToken() ?: return null
    val response = try {
        api.refresh(RefreshRequestDto(refreshToken))
    } catch (e: IOException) {
        return null
    }
    val body = response.takeIf { it.isSuccessful }?.body() ?: return null
    sessionStore.save(body.accessToken, body.refreshToken, AuthUser(body.user.id, body.user.email))
    return body.accessToken
}
