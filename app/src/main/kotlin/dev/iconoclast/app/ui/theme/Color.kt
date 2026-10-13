package dev.iconoclast.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The Geist palette: black, white and the greys between, no accent colour. Geist UI's
 * accents 1 to 8, light, and their dark mirror. Errors use the text colour too: the
 * words carry the message, never a hue.
 */
private object Grey {
    val L1 = Color(0xFFFAFAFA)
    val L2 = Color(0xFFF2F2F2)
    val L3 = Color(0xFFEAEAEA)
    val L4 = Color(0xFFD4D4D4)
    val L5 = Color(0xFF999999)
    val L6 = Color(0xFF666666)
    val L7 = Color(0xFF444444)

    val D1 = Color(0xFF0A0A0A)
    val D2 = Color(0xFF141414)
    val D3 = Color(0xFF1F1F1F)
    val D4 = Color(0xFF333333)
    val D5 = Color(0xFF666666)
    val D6 = Color(0xFF8F8F8F)
    val D7 = Color(0xFFBBBBBB)
}

/** The preview backdrop: a neutral mid grey, the same in both themes, never tinted. */
val PreviewBackdrop = Color(0xFF3D3D3D)

internal val LightColors: ColorScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Grey.L3,
    onPrimaryContainer = Color.Black,
    inversePrimary = Color.White,
    secondary = Grey.L7,
    onSecondary = Color.White,
    secondaryContainer = Grey.L3,
    onSecondaryContainer = Color.Black,
    tertiary = Color.Black,
    onTertiary = Color.White,
    tertiaryContainer = Grey.L3,
    onTertiaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Grey.L2,
    onSurfaceVariant = Grey.L6,
    surfaceTint = Color.Transparent,
    inverseSurface = Color.Black,
    inverseOnSurface = Color.White,
    error = Color.Black,
    onError = Color.White,
    errorContainer = Grey.L3,
    onErrorContainer = Color.Black,
    outline = Grey.L5,
    outlineVariant = Grey.L3,
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = Grey.L3,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Grey.L1,
    surfaceContainer = Grey.L2,
    surfaceContainerHigh = Grey.L3,
    surfaceContainerHighest = Grey.L4,
)

internal val DarkColors: ColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Grey.D3,
    onPrimaryContainer = Color.White,
    inversePrimary = Color.Black,
    secondary = Grey.D7,
    onSecondary = Color.Black,
    secondaryContainer = Grey.D3,
    onSecondaryContainer = Color.White,
    tertiary = Color.White,
    onTertiary = Color.Black,
    tertiaryContainer = Grey.D3,
    onTertiaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color(0xFFEDEDED),
    surface = Color.Black,
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Grey.D2,
    onSurfaceVariant = Grey.D6,
    surfaceTint = Color.Transparent,
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black,
    error = Color.White,
    onError = Color.Black,
    errorContainer = Grey.D3,
    onErrorContainer = Color.White,
    outline = Grey.D5,
    outlineVariant = Grey.D3,
    scrim = Color.Black,
    surfaceBright = Grey.D3,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Grey.D1,
    surfaceContainer = Grey.D2,
    surfaceContainerHigh = Grey.D3,
    surfaceContainerHighest = Grey.D4,
)
