package com.mirror.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Color tokens transcribed from the Mirror "Complexion Atelier" design kit
 * (stitch_mirror_beauty_ui_kit/mirror/DESIGN.md). These map 1:1 onto Material3's
 * ColorScheme roles - the kit was authored against that exact role set - so Theme.kt
 * builds a real M3 lightColorScheme from these rather than a bespoke palette.
 */
object MirrorColorTokens {
    val Primary = Color(0xFF994422)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFFB85C38)
    val OnPrimaryContainer = Color(0xFFFFFFFF)
    val PrimaryFixed = Color(0xFFFFDBCF)
    val PrimaryFixedDim = Color(0xFFFFB59A)
    val OnPrimaryFixed = Color(0xFF380D00)
    val OnPrimaryFixedVariant = Color(0xFF7B2F0E)
    val InversePrimary = Color(0xFFFFB59A)

    val Secondary = Color(0xFF7B5804)
    val OnSecondary = Color(0xFFFFFFFF)
    val SecondaryContainer = Color(0xFFFECD74)
    val OnSecondaryContainer = Color(0xFF785500)
    val SecondaryFixed = Color(0xFFFFDEA7)
    val SecondaryFixedDim = Color(0xFFEFBF67)
    val OnSecondaryFixed = Color(0xFF271900)
    val OnSecondaryFixedVariant = Color(0xFF5E4200)

    val Tertiary = Color(0xFF00686D)
    val OnTertiary = Color(0xFFFFFFFF)
    val TertiaryContainer = Color(0xFF008389)
    val OnTertiaryContainer = Color(0xFFFEFFFF)
    val TertiaryFixed = Color(0xFF91F2F8)
    val TertiaryFixedDim = Color(0xFF74D5DC)
    val OnTertiaryFixed = Color(0xFF002022)
    val OnTertiaryFixedVariant = Color(0xFF004F53)

    val Error = Color(0xFFBA1A1A)
    val OnError = Color(0xFFFFFFFF)
    val ErrorContainer = Color(0xFFFFDAD6)
    val OnErrorContainer = Color(0xFF93000A)

    val Background = Color(0xFFFFF8F4)
    val OnBackground = Color(0xFF1F1B17)
    val Surface = Color(0xFFFFF8F4)
    val OnSurface = Color(0xFF1F1B17)
    val SurfaceVariant = Color(0xFFEAE1DA)
    val OnSurfaceVariant = Color(0xFF55433C)
    val SurfaceTint = Color(0xFF9A4523)
    val SurfaceDim = Color(0xFFE1D8D2)
    val SurfaceBright = Color(0xFFFFF8F4)
    val SurfaceContainerLowest = Color(0xFFFFFFFF)
    val SurfaceContainerLow = Color(0xFFFBF2EB)
    val SurfaceContainer = Color(0xFFF6ECE5)
    val SurfaceContainerHigh = Color(0xFFF0E6E0)
    val SurfaceContainerHighest = Color(0xFFEAE1DA)

    val Outline = Color(0xFF88726B)
    val OutlineVariant = Color(0xFFDBC1B8)
    val InverseSurface = Color(0xFF34302B)
    val InverseOnSurface = Color(0xFFF9EFE8)
    val Scrim = Color(0xFF000000)
}
