package com.mirror.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * The design kit's tokens are mostly Material3 ColorScheme role names, so we build one real
 * ColorScheme rather than a parallel bespoke palette. Only a light scheme exists for now -
 * the kit doesn't specify a dark/night variant.
 *
 * Note: the kit also specifies "fixed" accent roles (primaryFixed, secondaryFixed, etc.).
 * Those were added to ColorScheme in a Material3 version newer than the one resolved here
 * (1.3.1) - see [MirrorColorTokens] for those values used directly where needed instead of
 * through MaterialTheme.colorScheme.
 */
private val MirrorColorScheme = lightColorScheme(
    primary = MirrorColorTokens.Primary,
    onPrimary = MirrorColorTokens.OnPrimary,
    primaryContainer = MirrorColorTokens.PrimaryContainer,
    onPrimaryContainer = MirrorColorTokens.OnPrimaryContainer,
    inversePrimary = MirrorColorTokens.InversePrimary,

    secondary = MirrorColorTokens.Secondary,
    onSecondary = MirrorColorTokens.OnSecondary,
    secondaryContainer = MirrorColorTokens.SecondaryContainer,
    onSecondaryContainer = MirrorColorTokens.OnSecondaryContainer,

    tertiary = MirrorColorTokens.Tertiary,
    onTertiary = MirrorColorTokens.OnTertiary,
    tertiaryContainer = MirrorColorTokens.TertiaryContainer,
    onTertiaryContainer = MirrorColorTokens.OnTertiaryContainer,

    error = MirrorColorTokens.Error,
    onError = MirrorColorTokens.OnError,
    errorContainer = MirrorColorTokens.ErrorContainer,
    onErrorContainer = MirrorColorTokens.OnErrorContainer,

    background = MirrorColorTokens.Background,
    onBackground = MirrorColorTokens.OnBackground,
    surface = MirrorColorTokens.Surface,
    onSurface = MirrorColorTokens.OnSurface,
    surfaceVariant = MirrorColorTokens.SurfaceVariant,
    onSurfaceVariant = MirrorColorTokens.OnSurfaceVariant,
    surfaceTint = MirrorColorTokens.SurfaceTint,
    surfaceDim = MirrorColorTokens.SurfaceDim,
    surfaceBright = MirrorColorTokens.SurfaceBright,
    surfaceContainerLowest = MirrorColorTokens.SurfaceContainerLowest,
    surfaceContainerLow = MirrorColorTokens.SurfaceContainerLow,
    surfaceContainer = MirrorColorTokens.SurfaceContainer,
    surfaceContainerHigh = MirrorColorTokens.SurfaceContainerHigh,
    surfaceContainerHighest = MirrorColorTokens.SurfaceContainerHighest,

    outline = MirrorColorTokens.Outline,
    outlineVariant = MirrorColorTokens.OutlineVariant,
    inverseSurface = MirrorColorTokens.InverseSurface,
    inverseOnSurface = MirrorColorTokens.InverseOnSurface,
    scrim = MirrorColorTokens.Scrim
)

@Composable
fun MirrorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MirrorColorScheme,
        typography = MirrorTypography,
        shapes = MirrorShapes,
        content = content
    )
}
