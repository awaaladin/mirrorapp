package com.mirror.app.data.auth

import android.content.Context
import com.mirror.app.domain.model.AuthUser

/** Persists the signed-in session (JWT + user) so login survives app restarts. Plain
 *  SharedPreferences is enough for the two small values this needs - no extra dependency. */
class SessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mirror_session", Context.MODE_PRIVATE)

    fun loadUser(): AuthUser? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null)
        return AuthUser(uid = userId, email = email)
    }

    fun loadToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun save(token: String, user: AuthUser) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_ID, user.uid)
            .putString(KEY_EMAIL, user.email)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
    }
}
