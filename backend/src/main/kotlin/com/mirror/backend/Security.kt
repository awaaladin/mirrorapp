package com.mirror.backend

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.mindrot.jbcrypt.BCrypt
import java.io.File
import java.security.SecureRandom
import java.util.Base64

const val JWT_ISSUER = "mirror-backend"
const val JWT_AUDIENCE = "mirror-app"
private const val TOKEN_VALID_DAYS = 30L

/**
 * Prefers the JWT_SECRET environment variable (set this on Render/any real host, since
 * container filesystems there are typically ephemeral and would otherwise generate a new
 * secret - invalidating every session - on each redeploy). Falls back to a file on disk for
 * local development convenience, so restarting the server on your own machine doesn't force
 * everyone to log in again.
 */
private val jwtSecret: String by lazy {
    System.getenv("JWT_SECRET")?.takeIf { it.isNotBlank() } ?: run {
        val file = File("jwt-secret.txt")
        if (file.exists()) {
            file.readText().trim()
        } else {
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val secret = Base64.getEncoder().encodeToString(bytes)
            file.writeText(secret)
            secret
        }
    }
}

val jwtAlgorithm: Algorithm by lazy { Algorithm.HMAC256(jwtSecret) }

fun generateToken(userId: Int, email: String): String =
    JWT.create()
        .withIssuer(JWT_ISSUER)
        .withAudience(JWT_AUDIENCE)
        .withClaim("userId", userId)
        .withClaim("email", email)
        .withExpiresAt(java.util.Date(System.currentTimeMillis() + TOKEN_VALID_DAYS * 24 * 60 * 60 * 1000))
        .sign(jwtAlgorithm)

fun hashPassword(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt())

fun verifyPassword(password: String, hash: String): Boolean =
    try {
        BCrypt.checkpw(password, hash)
    } catch (e: IllegalArgumentException) {
        false
    }
