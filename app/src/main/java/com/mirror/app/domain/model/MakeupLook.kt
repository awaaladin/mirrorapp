package com.mirror.app.domain.model

/**
 * A complete makeup style applied to one source photo. Each layer is nullable/empty
 * because a look can be built up incrementally (e.g. lipstick only, no eyeshadow yet).
 *
 * This model intentionally stores shade *references* (ShadeItem) rather than raw colors so
 * a saved look stays linked to a named product the user can re-select or share.
 */
data class MakeupLook(
    val id: String,
    val sourcePhotoUri: String,
    val name: String = "Untitled look",
    val lipColor: ShadeItem? = null,
    val foundationShade: ShadeItem? = null,
    val eyeshadowColor: ShadeItem? = null,
    val blushColor: ShadeItem? = null,
    val eyebrowStyle: EyebrowStyle? = null,
    val lashStyle: LashStyle? = null,
    val geleStyle: GeleStyle? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

enum class EyebrowStyle {
    NATURAL_FILL,
    SOFT_ARCH,
    BOLD_DEFINED,
    STRAIGHT
}

enum class LashStyle {
    NATURAL,
    WISPY,
    DRAMATIC,
    DOLL_EYE
}

enum class GeleStyle {
    AUTO_GELE,
    IPELE,
    SIMPLE_WRAP,
    WIG_STRAIGHT,
    WIG_CURLY
}
