package com.mirror.app.domain.model

/** What Gemini (via mirrorcentral's POST /showroom/analyze) read from a portrait. Advisory only:
 *  nothing here is applied to the photo - the user does that by tapping shades in Showroom. */
data class FaceAnalysis(
    val skinDepth: Int,
    val undertone: String,
    val skinHex: String,
    val lipHex: String,
    val faceShape: String,
    val photoWarnings: List<String>,
    val suggestions: List<ShadeSuggestion>,
    val summary: String
)

/** [category] is the backend's wire name: "foundation", "lipstick", "blush" or "eyebrow". */
data class ShadeSuggestion(val category: String, val name: String, val hexColor: String, val reason: String)
