package dev.iconoclast.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Geist's small radii: soft corners, nothing pill-shaped but chips. */
private val IconoclastShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/**
 * The app's look, after Geist UI: black on white, or white on black following the system,
 * greys for everything quieter, Geist Sans and Mono. No dynamic colour, no accent, and the
 * surface tint is transparent so elevation never shifts a colour.
 */
@Composable
fun IconoclastTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = IconoclastTypography,
        shapes = IconoclastShapes,
        content = content,
    )
}
