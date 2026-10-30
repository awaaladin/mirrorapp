package com.mirror.app.domain.model

/** The signed-in user, independent of which auth provider backs it (the custom mirror-backend server today). */
data class AuthUser(
    val uid: String,
    val email: String?
)
