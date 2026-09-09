package com.mirror.app.data.network

/**
 * Points at the custom Ktor server in the project's backend/ folder (alongside app/). Update this
 * when you move from local development to a deployed instance (e.g. Render):
 * "https://your-app.onrender.com/" - use https there, no code changes needed elsewhere.
 *
 * For local development, this must be the LAN IP of the machine running the server (not
 * "localhost" - that would mean the phone itself), and the phone must be on the same Wi-Fi
 * network as that machine.
 */
object NetworkConfig {
    const val BASE_URL = "http://10.114.84.83:8080/"
}
