package com.mirror.app.domain.model

/**
 * A single purchasable/selectable makeup color, e.g. one lipstick shade.
 *
 * [hexColor] is stored as "#AARRGGBB" or "#RRGGBB" so it can be parsed directly with
 * android.graphics.Color.parseColor without an intermediate lookup table.
 */
data class ShadeItem(
    val id: String,
    val name: String,
    val hexColor: String,
    val category: ShadeCategory,
    val finish: ShadeFinish,
    val isPremium: Boolean = false
)

enum class ShadeCategory {
    LIPSTICK,
    FOUNDATION,
    POWDER,
    EYESHADOW,
    BLUSH
}

enum class ShadeFinish {
    MATTE,
    GLOSSY,
    SHIMMER,
    SATIN
}
