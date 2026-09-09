package com.mirror.app.core.result

/**
 * A typed result wrapper used across the data/domain boundary instead of throwing exceptions
 * for expected failure cases (no face found, bad image, etc.) so the UI layer can pattern-match
 * on a closed set of outcomes rather than catching arbitrary throwables.
 */
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Failure(val error: AppError) : AppResult<Nothing>()
}

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(data)
    return this
}

inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) action(error)
    return this
}

/** Closed set of user-facing failure reasons surfaced across the app's repositories. */
sealed class AppError {
    // Photo / landmark pipeline
    data object NoFaceDetected : AppError()
    data object MultipleFacesDetected : AppError()
    data object ImageLoadFailed : AppError()
    data object LandmarkDetectionFailed : AppError()

    // Auth
    data object InvalidCredentials : AppError()
    data object EmailAlreadyInUse : AppError()
    data object WeakPassword : AppError()
    data object AccountNotFound : AppError()
    data object NetworkUnavailable : AppError()
    data class AuthFailed(val message: String) : AppError()
}
