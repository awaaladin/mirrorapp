package com.mirror.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirror.app.core.result.AppError
import com.mirror.app.core.result.AppResult
import com.mirror.app.domain.model.AuthUser
import com.mirror.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class AuthSessionState {
    data object Loading : AuthSessionState()
    data class SignedIn(val user: AuthUser) : AuthSessionState()
    data object SignedOut : AuthSessionState()
}

sealed class AuthFormState {
    data object Idle : AuthFormState()
    data object Submitting : AuthFormState()
    data class Error(val message: String) : AuthFormState()
}

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    val sessionState: StateFlow<AuthSessionState> = authRepository.observeCurrentUser()
        .map { user -> if (user != null) AuthSessionState.SignedIn(user) else AuthSessionState.SignedOut }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthSessionState.Loading)

    private val _formState = MutableStateFlow<AuthFormState>(AuthFormState.Idle)
    val formState: StateFlow<AuthFormState> = _formState.asStateFlow()

    fun signUp(email: String, password: String) {
        val validationError = validate(email, password)
        if (validationError != null) {
            _formState.value = AuthFormState.Error(validationError)
            return
        }
        _formState.value = AuthFormState.Submitting
        viewModelScope.launch {
            when (val result = authRepository.signUp(email.trim(), password)) {
                is AppResult.Success -> _formState.value = AuthFormState.Idle
                is AppResult.Failure -> _formState.value = AuthFormState.Error(result.error.toMessage())
            }
        }
    }

    fun logIn(email: String, password: String) {
        val validationError = validate(email, password)
        if (validationError != null) {
            _formState.value = AuthFormState.Error(validationError)
            return
        }
        _formState.value = AuthFormState.Submitting
        viewModelScope.launch {
            when (val result = authRepository.logIn(email.trim(), password)) {
                is AppResult.Success -> _formState.value = AuthFormState.Idle
                is AppResult.Failure -> _formState.value = AuthFormState.Error(result.error.toMessage())
            }
        }
    }

    fun logOut() {
        authRepository.logOut()
    }

    fun clearError() {
        if (_formState.value is AuthFormState.Error) {
            _formState.value = AuthFormState.Idle
        }
    }

    private fun validate(email: String, password: String): String? = when {
        email.isBlank() -> "Enter your email address."
        !email.contains("@") || !email.contains(".") -> "Enter a valid email address."
        password.length < 6 -> "Password must be at least 6 characters."
        else -> null
    }

    private fun AppError.toMessage(): String = when (this) {
        AppError.InvalidCredentials -> "Incorrect email or password."
        AppError.EmailAlreadyInUse -> "An account with this email already exists."
        AppError.WeakPassword -> "Choose a stronger password (at least 6 characters)."
        AppError.AccountNotFound -> "No account found with this email."
        AppError.NetworkUnavailable -> "No internet connection. Please try again."
        is AppError.AuthFailed -> message
        else -> "Something went wrong. Please try again."
    }
}
