package com.mirror.app.data.auth

import android.content.Context
import com.mirror.app.domain.model.AccountType
import com.mirror.app.domain.model.AuthUser

/** Persists the signed-in session (access + refresh JWTs, plus the user) so login survives
 *  app restarts. Plain SharedPreferences is enough for these few small values - no extra
 *  dependency. */
class SessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mirror_session", Context.MODE_PRIVATE)

    fun loadUser(): AuthUser? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null)
        val accountType = AccountType.fromWire(prefs.getString(KEY_ACCOUNT_TYPE, null))
        return AuthUser(uid = userId, email = email, accountType = accountType)
    }

    fun loadAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun loadRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun save(accessToken: String, refreshToken: String, user: AuthUser) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_USER_ID, user.uid)
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_ACCOUNT_TYPE, user.accountType.name)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
        const val KEY_ACCOUNT_TYPE = "account_type"
    }
}
