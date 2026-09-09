package com.mirror.backend

import com.auth0.jwt.JWT
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json

fun main() {
    initDatabase()
    // Render (and most PaaS hosts) assign the listen port via $PORT; default to 8080 for
    // local development. 0.0.0.0 (not 127.0.0.1) so the server is reachable from other
    // devices on the same Wi-Fi network - e.g. a phone running the Mirror app.
    val port = System.getenv("PORT")?.toIntOrNull()?.takeIf { it > 0 } ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::mirrorModule)
        .start(wait = true)
}

fun Application.mirrorModule() {
    install(CallLogging)
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(cause.message ?: "Unexpected server error."))
        }
    }
    install(Authentication) {
        jwt("auth-jwt") {
            verifier(JWT.require(jwtAlgorithm).withIssuer(JWT_ISSUER).withAudience(JWT_AUDIENCE).build())
            validate { credential -> JWTPrincipal(credential.payload) }
        }
    }
    install(Routing) {
        authRoutes()
    }
}
