package com.mirror.app.data.network

/**
 * The app tries [LOCAL_BASE_URL] first (fast, useful during development when this phone and
 * the machine running mirrorcentral locally are on the same Wi-Fi), then falls back to
 * [DEPLOYED_BASE_URL] on any network failure - so the app works standalone (any network,
 * anywhere) even when there's no local dev server reachable at all, which is the normal case
 * for a real user's phone.
 */
object NetworkConfig {
    /** Update if you run `uvicorn app.main:app --host 0.0.0.0 --port 8000` on this LAN. */
    const val LOCAL_BASE_URL = "http://10.96.159.83:8000/"
    const val DEPLOYED_BASE_URL = "https://mirrorcentral.vercel.app/"
}
