package com.mirror.app.domain.repository

import com.mirror.app.core.result.AppResult
import com.mirror.app.domain.model.AccountType
import com.mirror.app.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

/**
 * Account authentication boundary, kept as an interface so the concrete auth provider
 * (the custom mirror-backend server today) never leaks into ViewModels or UI.
 */
interface AuthRepository {
    /** Emits the current signed-in user, or null when signed out. Emits immediately on collection. */
    fun observeCurrentUser(): Flow<AuthUser?>

    suspend fun signUp(email: String, password: String, accountType: AccountType): AppResult<AuthUser>
    suspend fun logIn(email: String, password: String): AppResult<AuthUser>
    fun logOut()
}
