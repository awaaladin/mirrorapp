package com.mirror.backend

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class AuthRequest(val email: String, val password: String)

@Serializable
data class AuthResponse(val token: String, val userId: Int, val email: String)

@Serializable
data class ErrorResponse(val error: String)

fun Route.authRoutes() {
    post("/auth/signup") {
        val request = call.receive<AuthRequest>()
        val email = request.email.trim().lowercase()

        if (email.isBlank() || !email.contains("@") || !email.contains(".")) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Enter a valid email address."))
            return@post
        }
        if (request.password.length < 6) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Password must be at least 6 characters."))
            return@post
        }

        val existing = transaction { Users.selectAll().where { Users.email eq email }.firstOrNull() }
        if (existing != null) {
            call.respond(HttpStatusCode.Conflict, ErrorResponse("An account with this email already exists."))
            return@post
        }

        val userId = transaction {
            Users.insertAndGetId {
                it[Users.email] = email
                it[passwordHash] = hashPassword(request.password)
                it[createdAtEpochMillis] = System.currentTimeMillis()
            }.value
        }

        call.respond(HttpStatusCode.Created, AuthResponse(generateToken(userId, email), userId, email))
    }

    post("/auth/login") {
        val request = call.receive<AuthRequest>()
        val email = request.email.trim().lowercase()

        val row = transaction { Users.selectAll().where { Users.email eq email }.firstOrNull() }
        if (row == null || !verifyPassword(request.password, row[Users.passwordHash])) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Incorrect email or password."))
            return@post
        }

        val userId = row[Users.id].value
        call.respond(HttpStatusCode.OK, AuthResponse(generateToken(userId, email), userId, email))
    }

    authenticate("auth-jwt") {
        get("/auth/me") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", Int::class)
            val email = principal?.getClaim("email", String::class)
            if (userId == null || email == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid session."))
                return@get
            }
            call.respond(HttpStatusCode.OK, AuthResponse(token = "", userId = userId, email = email))
        }
    }
}
