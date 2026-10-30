package com.mirror.app.data.auth

import android.content.Context
import com.mirror.app.domain.model.AuthUser
import java.util.UUID

/** Persists the signed-in session (access + refresh JWTs, plus the user) so login survives
 *  app restarts. Plain SharedPreferences is enough for these few small values - no extra
 *  dependency. */
class SessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mirror_session", Context.MODE_PRIVATE)
    private val deviceStore = context.applicationContext.getSharedPreferences("mirror_device", Context.MODE_PRIVATE)

    fun loadUser(): AuthUser? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null)
        return AuthUser(uid = userId, email = email)
    }

    fun loadAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun loadRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun save(accessToken: String, refreshToken: String, user: AuthUser) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_USER_ID, user.uid)
            .putString(KEY_EMAIL, user.email)
            .apply()
    }

    fun loadLicense(): String? = prefs.getString(KEY_LICENSE, null)

    fun saveLicense(license: String?) {
        prefs.edit().putString(KEY_LICENSE, license).apply()
    }

    /** Random per-install id a license is bound to. Kept outside mirror_session so signing out
     *  doesn't change it. */
    fun deviceId(): String {
        deviceStore.getString(KEY_DEVICE_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        deviceStore.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
        const val KEY_LICENSE = "license"
        const val KEY_DEVICE_ID = "device_id"
    }
}
