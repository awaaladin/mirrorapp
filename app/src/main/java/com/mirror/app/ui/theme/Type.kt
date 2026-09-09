package com.mirror.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The design kit specifies three families: Newsreader (editorial serif, brand moments),
 * Inter (utility sans, the bulk of the UI) and Space Mono (all-caps cataloging labels -
 * shade codes, finish tags, technical readouts).
 *
 * We stand in with the closest system families (Serif / SansSerif / Monospace) rather than
 * bundling the real webfonts, since this device has no offline copy of them - swap the
 * FontFamily values below for `FontFamily(Font(R.font.newsreader_regular), ...)` once the
 * real .ttf files are added to res/font.
 */
private val EditorialSerif = FontFamily.Serif
private val UtilitySans = FontFamily.SansSerif
private val CatalogMono = FontFamily.Monospace

val MirrorTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = EditorialSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.015).em
    ),
    headlineMedium = TextStyle(
        fontFamily = EditorialSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.01).em
    ),
    headlineSmall = TextStyle(
        fontFamily = EditorialSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 30.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = UtilitySans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = UtilitySans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp
    ),
    bodySmall = TextStyle(
        fontFamily = UtilitySans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = UtilitySans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    // "label-eyebrow" in the kit: all-caps Space Mono micro-labels with heavy tracking.
    labelMedium = TextStyle(
        fontFamily = CatalogMono,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.14.em
    ),
    // "label-spec" in the kit: shade codes, finish tags, technical telemetry.
    labelSmall = TextStyle(
        fontFamily = CatalogMono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.04.em
    )
)

/**
 * Italic Newsreader look-title moments (client names, look titles) aren't one of M3's fixed
 * Typography slots, so it's kept as a standalone style rather than shoehorned into one.
 */
val TextStyleHeadlineSmallItalic: TextStyle = TextStyle(
    fontFamily = EditorialSerif,
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.Normal,
    fontSize = 22.sp,
    lineHeight = 30.sp
)
