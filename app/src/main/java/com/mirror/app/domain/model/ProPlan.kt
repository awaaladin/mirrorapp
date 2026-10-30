package com.mirror.app.domain.model

/** What this account can use. The app itself is free; mirrorcentral's
 *  app/services/entitlements.py is the source of truth for what each paid plan unlocks. */
enum class ProPlan {
    FREE,
    SOLO,
    STUDIO;

    val hasShowroom: Boolean get() = this != FREE
    val hasClientMode: Boolean get() = this == STUDIO

    companion object {
        fun fromWire(value: String?): ProPlan =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: FREE
    }
}
