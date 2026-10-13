package dev.iconoclast.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.iconoclast.app.R

/** Geist Sans, the vendored static weights in res/font. */
val GeistSans = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
)

/** Geist Mono, for numbers that change under a finger: they keep their width. */
val GeistMono = FontFamily(
    Font(R.font.geist_mono_regular, FontWeight.Normal),
    Font(R.font.geist_mono_medium, FontWeight.Medium),
)

private fun sans(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) = TextStyle(
    fontFamily = GeistSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

/** Few sizes, tight tracking on the large ones, as Geist sets them. */
internal val IconoclastTypography = Typography(
    displayLarge = sans(48, 56, FontWeight.SemiBold, -0.04),
    displayMedium = sans(40, 48, FontWeight.SemiBold, -0.04),
    displaySmall = sans(32, 40, FontWeight.SemiBold, -0.03),
    headlineLarge = sans(28, 36, FontWeight.SemiBold, -0.03),
    headlineMedium = sans(24, 32, FontWeight.SemiBold, -0.02),
    headlineSmall = sans(22, 28, FontWeight.SemiBold, -0.02),
    titleLarge = sans(20, 28, FontWeight.SemiBold, -0.02),
    titleMedium = sans(17, 24, FontWeight.SemiBold, -0.01),
    titleSmall = sans(15, 20, FontWeight.SemiBold),
    bodyLarge = sans(16, 24),
    bodyMedium = sans(15, 22),
    bodySmall = sans(13, 18),
    labelLarge = sans(15, 20, FontWeight.Medium),
    labelMedium = sans(13, 18, FontWeight.Medium),
    labelSmall = sans(12, 16, FontWeight.Medium),
)

/** A control's current value: "1.0×", "49%". */
val ValueStyle = TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
