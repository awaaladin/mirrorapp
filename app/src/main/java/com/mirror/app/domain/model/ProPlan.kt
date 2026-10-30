package com.mirror.app.domain.model

/** What this account has bought. The app itself is free; mirrorcentral's
 *  app/services/entitlements.py is the source of truth for what each purchase unlocks. */
enum class ProPlan {
    FREE,

    /** The one-time payment: no watermark, forever. */
    SOLO,

    /** Mirror Pro. Not on sale yet. */
    STUDIO;

    val removesWatermark: Boolean get() = this != FREE
    val hasShowroom: Boolean get() = this == STUDIO
    val hasClientMode: Boolean get() = this == STUDIO

    companion object {
        fun fromWire(value: String?): ProPlan =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: FREE
    }
}
